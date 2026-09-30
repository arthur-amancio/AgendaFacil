#!/usr/bin/env bash
set -euo pipefail

umask 0077

fail() {
  printf 'ERRO: %s\n' "$1" >&2
  exit 1
}

require_environment() {
  local variable_name="$1"
  [[ -n "${!variable_name:-}" ]] || fail "variável obrigatória ausente: ${variable_name}"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "comando obrigatório não encontrado: $1"
}

[[ $# -eq 1 ]] || fail "uso: restore-postgres.sh CAMINHO_DO_BUNDLE"
bundle_directory="$1"
[[ -d "$bundle_directory" && ! -L "$bundle_directory" ]] \
  || fail "bundle de backup não encontrado ou simbólico"

for variable_name in \
  PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD PGSSLMODE \
  RESTORE_CONFIRM_DATABASE GNUPGHOME; do
  require_environment "$variable_name"
done

for command_name in pg_restore psql gpg sha256sum awk; do
  require_command "$command_name"
done

pg_restore_version="$(pg_restore --version)"
psql_version="$(psql --version)"
[[ "$pg_restore_version" =~ \ 16\. ]] || fail "pg_restore major 16 é obrigatório: ${pg_restore_version}"
[[ "$psql_version" =~ \ 16\. ]] || fail "psql major 16 é obrigatório: ${psql_version}"
printf 'Cliente PostgreSQL validado: %s; %s\n' "$pg_restore_version" "$psql_version"

[[ "$RESTORE_CONFIRM_DATABASE" == "$PGDATABASE" ]] \
  || fail "RESTORE_CONFIRM_DATABASE deve ser idêntico a PGDATABASE"
[[ "$GNUPGHOME" == /* && -d "$GNUPGHOME" && ! -L "$GNUPGHOME" ]] \
  || fail "GNUPGHOME de recuperação inválido"

shopt -s nullglob
encrypted_archives=("$bundle_directory"/agendafacil-*.dump.gpg)
shopt -u nullglob
[[ ${#encrypted_archives[@]} -eq 1 ]] \
  || fail "bundle deve conter exatamente um archive criptografado"
encrypted_archive="${encrypted_archives[0]}"
checksum_file="${encrypted_archive}.sha256"
[[ -f "$encrypted_archive" && ! -L "$encrypted_archive" ]] \
  || fail "archive criptografado inválido ou simbólico"
[[ -f "$checksum_file" && ! -L "$checksum_file" ]] \
  || fail "checksum correspondente não encontrado ou simbólico"

checksum_lines="$(awk 'END { print NR }' "$checksum_file")"
read -r checksum_digest checksum_target checksum_extra < "$checksum_file" \
  || fail "registro de checksum inválido"
[[ "$checksum_lines" == "1" && "$checksum_digest" =~ ^[[:xdigit:]]{64}$ \
    && "$checksum_target" == "$(basename -- "$encrypted_archive")" \
    && -z "$checksum_extra" ]] \
  || fail "registro de checksum não corresponde exatamente ao archive"

(
  cd "$bundle_directory"
  sha256sum --check --strict "$(basename -- "$checksum_file")"
)

user_table_count="$(
  psql --no-psqlrc --tuples-only --no-align --set=ON_ERROR_STOP=1 \
    --command="SELECT count(*) FROM pg_catalog.pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema');"
)"
user_table_count="${user_table_count//[[:space:]]/}"
[[ "$user_table_count" == "0" ]] \
  || fail "o banco alvo não está vazio; use um database novo e descartável"

gpg --batch --no-tty --quiet --decrypt "$encrypted_archive" \
  | pg_restore --list >/dev/null

gpg --batch --no-tty --quiet --decrypt "$encrypted_archive" \
  | pg_restore \
      --dbname="$PGDATABASE" \
      --no-owner \
      --no-acl \
      --exit-on-error \
      --single-transaction

psql --no-psqlrc --set=ON_ERROR_STOP=1 --command='SELECT 1' >/dev/null

history_exists="$(
  psql --no-psqlrc --tuples-only --no-align --set=ON_ERROR_STOP=1 \
    --command="SELECT to_regclass('public.flyway_schema_history') IS NOT NULL;"
)"
history_exists="${history_exists//[[:space:]]/}"
[[ "$history_exists" == "t" ]] || fail "flyway_schema_history não foi restaurada"

failed_migrations="$(
  psql --no-psqlrc --tuples-only --no-align --set=ON_ERROR_STOP=1 \
    --command='SELECT count(*) FROM flyway_schema_history WHERE success = false;'
)"
failed_migrations="${failed_migrations//[[:space:]]/}"
[[ "$failed_migrations" == "0" ]] || fail "existem migrations Flyway com success=false"

missing_core_tables="$(
  psql --no-psqlrc --tuples-only --no-align --set=ON_ERROR_STOP=1 --command="
    SELECT count(*)
    FROM (VALUES
      ('establishments'),
      ('users_app'),
      ('customers'),
      ('service_items'),
      ('professionals'),
      ('appointments'),
      ('establishment_settings'),
      ('establishment_business_hours')
    ) AS expected(name)
    WHERE to_regclass('public.' || expected.name) IS NULL;"
)"
missing_core_tables="${missing_core_tables//[[:space:]]/}"
[[ "$missing_core_tables" == "0" ]] || fail "tabelas principais não foram restauradas"

psql --no-psqlrc --set=ON_ERROR_STOP=1 \
  --command='SELECT count(*) FROM establishments; SELECT count(*) FROM appointments;' >/dev/null

printf 'Restore e validações estruturais concluídos no database confirmado: %s\n' "$PGDATABASE"
