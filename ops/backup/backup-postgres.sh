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

for variable_name in \
  PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD PGSSLMODE \
  BACKUP_GPG_RECIPIENT BACKUP_OUTPUT_DIR GNUPGHOME RUNTIME_DIRECTORY; do
  require_environment "$variable_name"
done

for command_name in pg_dump pg_restore gpg sha256sum awk grep date mktemp; do
  require_command "$command_name"
done

pg_dump_version="$(pg_dump --version)"
pg_restore_version="$(pg_restore --version)"
[[ "$pg_dump_version" =~ \ 16\. ]] || fail "pg_dump major 16 é obrigatório: ${pg_dump_version}"
[[ "$pg_restore_version" =~ \ 16\. ]] || fail "pg_restore major 16 é obrigatório: ${pg_restore_version}"
printf 'Cliente PostgreSQL validado: %s; %s\n' "$pg_dump_version" "$pg_restore_version"

[[ "$BACKUP_OUTPUT_DIR" == /* ]] || fail "BACKUP_OUTPUT_DIR deve ser um caminho absoluto"
[[ ! -L "$BACKUP_OUTPUT_DIR" ]] || fail "BACKUP_OUTPUT_DIR não pode ser link simbólico"
[[ "$RUNTIME_DIRECTORY" == /* && -d "$RUNTIME_DIRECTORY" ]] \
  || fail "RUNTIME_DIRECTORY privado não está disponível"
[[ "$GNUPGHOME" == /* && ! -L "$GNUPGHOME" ]] \
  || fail "GNUPGHOME deve ser um diretório absoluto e não pode ser link simbólico"

recipient="${BACKUP_GPG_RECIPIENT^^}"
[[ "$recipient" =~ ^([0-9A-F]{40}|[0-9A-F]{64})$ ]] \
  || fail "BACKUP_GPG_RECIPIENT deve ser um fingerprint OpenPGP completo"

mkdir -p -- "$GNUPGHOME" "$BACKUP_OUTPUT_DIR/daily" "$BACKUP_OUTPUT_DIR/weekly" "$BACKUP_OUTPUT_DIR/monthly"
chmod 0700 -- "$GNUPGHOME" "$BACKUP_OUTPUT_DIR" \
  "$BACKUP_OUTPUT_DIR/daily" "$BACKUP_OUTPUT_DIR/weekly" "$BACKUP_OUTPUT_DIR/monthly"

key_fingerprints="$(
  gpg --batch --no-tty --with-colons --fingerprint --list-keys "$recipient" 2>/dev/null \
    | awk -F: '$1 == "fpr" { print toupper($10) }'
)"
grep -Fxq "$recipient" <<< "$key_fingerprints" \
  || fail "a chave pública configurada não existe no GNUPGHOME"

if gpg --batch --no-tty --with-colons --list-secret-keys "$recipient" 2>/dev/null \
  | grep -Eq '^(sec|ssb):'; then
  fail "a chave privada de restore não pode permanecer no host de backup"
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
week_key="$(date -u +%G-W%V)"
month_key="$(date -u +%Y-%m)"
bundle_name="agendafacil-${timestamp}"
archive_plain_name="${bundle_name}.dump"
archive_encrypted_name="${archive_plain_name}.gpg"
checksum_name="${archive_encrypted_name}.sha256"
daily_directory="$BACKUP_OUTPUT_DIR/daily"
final_bundle="$daily_directory/$bundle_name"
partial_bundle="$daily_directory/.${bundle_name}.partial.$$"
runtime_work_directory="$(mktemp -d "$RUNTIME_DIRECTORY/${bundle_name}.XXXXXX")"
plain_archive="$runtime_work_directory/$archive_plain_name"

cleanup() {
  rm -rf -- "$runtime_work_directory"
  if [[ -n "${partial_bundle:-}" && -d "$partial_bundle" ]]; then
    rm -rf -- "$partial_bundle"
  fi
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM

[[ ! -e "$final_bundle" ]] || fail "já existe backup com o identificador UTC atual"
mkdir -m 0700 -- "$partial_bundle"

pg_dump \
  --format=custom \
  --no-owner \
  --no-acl \
  --file="$plain_archive"

[[ -s "$plain_archive" ]] || fail "pg_dump produziu archive vazio"
pg_restore --list "$plain_archive" >/dev/null

gpg \
  --batch \
  --yes \
  --no-tty \
  --trust-model always \
  --recipient "$recipient" \
  --output "$partial_bundle/$archive_encrypted_name" \
  --encrypt "$plain_archive"

[[ -s "$partial_bundle/$archive_encrypted_name" ]] || fail "GnuPG produziu arquivo vazio"
(
  cd "$partial_bundle"
  sha256sum "$archive_encrypted_name" > "$checksum_name"
  sha256sum --check "$checksum_name"
)

mv -T -- "$partial_bundle" "$final_bundle"
partial_bundle=""

script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
retention_script="$script_directory/backup-retention.sh"
[[ -x "$retention_script" ]] || fail "script de retenção não está executável: $retention_script"
"$retention_script" "$BACKUP_OUTPUT_DIR" "$final_bundle" "$week_key" "$month_key"

printf 'Backup criptografado concluído: %s\n' "$final_bundle"
