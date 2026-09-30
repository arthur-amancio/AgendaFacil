#!/usr/bin/env bash
set -euo pipefail

umask 0077
export LC_ALL=C

fail() {
  printf 'REHEARSAL ERROR: %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

[[ $# -eq 1 ]] || fail "usage: run-recovery-rehearsal.sh APPLICATION_JAR"
application_jar="$(realpath -- "$1")"
[[ -f "$application_jar" ]] || fail "application JAR not found"

script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "$script_directory/../.." && pwd)"
backup_script="$project_root/ops/backup/backup-postgres.sh"
restore_script="$project_root/ops/backup/restore-postgres.sh"
fixture_sql="$script_directory/rehearsal-fixture.sql"
[[ -x "$backup_script" && -x "$restore_script" && -f "$fixture_sql" ]] \
  || fail "P0.6.7 scripts or rehearsal fixture are unavailable"

if [[ -n "${POSTGRES_CLIENT_BIN:-}" ]]; then
  [[ "$POSTGRES_CLIENT_BIN" == /* && -d "$POSTGRES_CLIENT_BIN" ]] \
    || fail "POSTGRES_CLIENT_BIN must be an existing absolute directory"
  export PATH="$POSTGRES_CLIENT_BIN:$PATH"
fi

for command_name in \
  awk basename cp curl date docker find gpg grep java mktemp openssl \
  pg_dump pg_restore psql realpath rm sha256sum; do
  require_command "$command_name"
done

for postgres_command in pg_dump pg_restore psql; do
  postgres_version="$($postgres_command --version)"
  [[ "$postgres_version" =~ \ 16\. ]] \
    || fail "$postgres_command major 16 is required: $postgres_version"
done

work_root="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/agendafacil-rehearsal.XXXXXX")"
[[ "$work_root" == */agendafacil-rehearsal.* ]] || fail "unsafe rehearsal work directory"
chmod 0700 -- "$work_root"

run_identity="${GITHUB_RUN_ID:-local}-$(printf '%s' "${GITHUB_RUN_ATTEMPT:-1}-$$" | tr -cd '[:alnum:]-')"
source_container="agendafacil-source-$run_identity"
recovery_container="agendafacil-recovery-$run_identity"
source_database="agendafacil_source"
recovery_database="agendafacil_recovery"
database_user="rehearsal_operator"
database_password="$(openssl rand -hex 24)"
app_pid=""

cleanup() {
  if [[ -n "$app_pid" ]] && kill -0 "$app_pid" 2>/dev/null; then
    kill "$app_pid" 2>/dev/null || true
    wait "$app_pid" 2>/dev/null || true
  fi
  docker rm --force "$source_container" "$recovery_container" >/dev/null 2>&1 || true
  if [[ -d "$work_root" && "$work_root" == */agendafacil-rehearsal.* ]]; then
    rm -rf -- "$work_root"
  fi
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM

psql_database() {
  local port="$1"
  local database="$2"
  shift 2
  PGPASSWORD="$database_password" PGSSLMODE=disable \
    psql --no-psqlrc --host=127.0.0.1 --port="$port" \
      --username="$database_user" --dbname="$database" \
      --set=ON_ERROR_STOP=1 "$@"
}

scalar_query() {
  local port="$1"
  local database="$2"
  local query="$3"
  psql_database "$port" "$database" --tuples-only --no-align --command="$query" \
    | tr -d '[:space:]'
}

container_port() {
  docker port "$1" 5432/tcp | awk -F: 'NR == 1 { print $NF }'
}

wait_for_postgres() {
  local port="$1"
  local database="$2"
  local deadline=$((SECONDS + 60))
  while (( SECONDS < deadline )); do
    if psql_database "$port" "$database" --tuples-only --command='SELECT 1' >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  fail "PostgreSQL did not become ready within 60 seconds"
}

start_postgres() {
  local container="$1"
  local database="$2"
  docker run --detach --rm \
    --name "$container" \
    --env POSTGRES_USER="$database_user" \
    --env POSTGRES_PASSWORD="$database_password" \
    --env POSTGRES_DB="$database" \
    --publish 127.0.0.1::5432 \
    postgres:16-alpine >/dev/null
  local port
  port="$(container_port "$container")"
  [[ "$port" =~ ^[0-9]+$ ]] || fail "could not resolve PostgreSQL port"
  wait_for_postgres "$port" "$database"
  local server_version
  server_version="$(scalar_query "$port" "$database" 'SHOW server_version_num;')"
  [[ "$server_version" == 16* ]] || fail "server PostgreSQL 16 is required"
  printf '%s\n' "$port"
}

stop_application() {
  if [[ -n "$app_pid" ]] && kill -0 "$app_pid" 2>/dev/null; then
    kill "$app_pid"
    wait "$app_pid" 2>/dev/null || true
  fi
  app_pid=""
}

start_application() {
  local database_port="$1"
  local database_name="$2"
  local http_port="$3"
  local management_port="$4"
  local log_file="$5"

  SPRING_PROFILES_ACTIVE=prod \
  DB_URL="jdbc:postgresql://127.0.0.1:${database_port}/${database_name}" \
  DB_USERNAME="$database_user" \
  DB_PASSWORD="$database_password" \
  APP_TIME_ZONE=America/Sao_Paulo \
  SERVER_PORT="$http_port" \
  MANAGEMENT_SERVER_PORT="$management_port" \
    java -jar "$application_jar" >"$log_file" 2>&1 &
  app_pid=$!

  local deadline=$((SECONDS + 90))
  local readiness_response
  while (( SECONDS < deadline )); do
    if ! kill -0 "$app_pid" 2>/dev/null; then
      tail -n 80 "$log_file" >&2
      fail "application exited before readiness"
    fi
    if readiness_response="$(curl --fail --silent --max-time 2 \
        "http://127.0.0.1:${management_port}/actuator/health/readiness" 2>/dev/null)" \
        && grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<< "$readiness_response"; then
      return 0
    fi
    sleep 1
  done
  tail -n 80 "$log_file" >&2
  fail "application readiness did not become UP within 90 seconds"
}

assert_empty_database() {
  local table_count
  table_count="$(scalar_query "$recovery_port" "$recovery_database" \
    "SELECT count(*) FROM pg_catalog.pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema');")"
  [[ "$table_count" == "0" ]] || fail "negative test changed the empty recovery database"
}

run_backup() (
  local gpg_home="$1"
  local output_directory="$2"
  local runtime_directory="$3"
  export PGHOST=127.0.0.1
  export PGPORT="$source_port"
  export PGDATABASE="$source_database"
  export PGUSER="$database_user"
  export PGPASSWORD="$database_password"
  export PGSSLMODE=disable
  export BACKUP_GPG_RECIPIENT="$gpg_fingerprint"
  export BACKUP_OUTPUT_DIR="$output_directory"
  export GNUPGHOME="$gpg_home"
  export RUNTIME_DIRECTORY="$runtime_directory"
  "$backup_script"
)

run_restore() (
  local bundle="$1"
  local confirmation="$2"
  local gpg_home="$3"
  export PGHOST=127.0.0.1
  export PGPORT="$recovery_port"
  export PGDATABASE="$recovery_database"
  export PGUSER="$database_user"
  export PGPASSWORD="$database_password"
  export PGSSLMODE=disable
  export RESTORE_CONFIRM_DATABASE="$confirmation"
  export GNUPGHOME="$gpg_home"
  "$restore_script" "$bundle"
)

expect_failure() {
  local label="$1"
  local expected_message="$2"
  shift 2
  local log_file="$work_root/negative-${label}.log"
  if "$@" >"$log_file" 2>&1; then
    fail "negative test unexpectedly succeeded: $label"
  fi
  if [[ -n "$expected_message" ]] && ! grep -Fq "$expected_message" "$log_file"; then
    tail -n 40 "$log_file" >&2
    fail "negative test did not fail for the expected reason: $label"
  fi
  printf 'Fail-closed check passed: %s\n' "$label"
}

printf 'Recovery rehearsal started with ephemeral resources only.\n'
printf 'Boundary label: simulated off-site boundary (not real off-site storage).\n'
docker pull postgres:16-alpine >/dev/null

source_port="$(start_postgres "$source_container" "$source_database")"
printf 'Source PostgreSQL server: %s\n' "$(scalar_query "$source_port" "$source_database" 'SHOW server_version;')"

source_app_log="$work_root/source-application.log"
start_application "$source_port" "$source_database" 18080 18081 "$source_app_log"
stop_application

psql_database "$source_port" "$source_database" --file="$fixture_sql" >/dev/null

canary_query="
  SELECT concat_ws('|', e.slug, e.name, e.description, s.name, p.name, a.status, a.public_token)
  FROM establishments e
  JOIN service_items s ON s.establishment_id = e.id AND s.id = 900001
  JOIN professionals p ON p.establishment_id = e.id AND p.id = 900001
  JOIN appointments a ON a.establishment_id = e.id AND a.id = 900001
  WHERE e.id = 900001;"
source_canary="$(scalar_query "$source_port" "$source_database" "$canary_query")"
expected_canary='recovery-rehearsal-canary|RecoveryRehearsalStudio|RECOVERY_REHEARSAL_CANARY_V1|ServiçoCanário|ProfissionalCanário|COMPLETED|recovery-rehearsal-token-000001'
[[ "$source_canary" == "$expected_canary" ]] || fail "source canary fixture is inconsistent"
source_establishments="$(scalar_query "$source_port" "$source_database" 'SELECT count(*) FROM establishments;')"
source_appointments="$(scalar_query "$source_port" "$source_database" 'SELECT count(*) FROM appointments;')"
source_flyway_rows="$(scalar_query "$source_port" "$source_database" 'SELECT count(*) FROM flyway_schema_history;')"
source_canary_digest="$(printf '%s' "$source_canary" | sha256sum | awk '{ print $1 }')"

recovery_gnupg="$work_root/recovery-gnupg"
backup_gnupg="$work_root/backup-gnupg"
wrong_gnupg="$work_root/wrong-gnupg"
mkdir -m 0700 -- "$recovery_gnupg" "$backup_gnupg" "$wrong_gnupg"

gpg --homedir "$recovery_gnupg" --batch --pinentry-mode loopback --passphrase '' \
  --quick-generate-key 'AgendaFacil Recovery Rehearsal <recovery-rehearsal@example.invalid>' \
  rsa3072 encrypt 1d >/dev/null 2>&1
gpg_fingerprint="$(
  gpg --homedir "$recovery_gnupg" --batch --with-colons --fingerprint \
    | awk -F: '$1 == "fpr" { print toupper($10); exit }'
)"
[[ "$gpg_fingerprint" =~ ^([0-9A-F]{40}|[0-9A-F]{64})$ ]] \
  || fail "ephemeral GPG fingerprint is invalid"
gpg --homedir "$recovery_gnupg" --batch --export "$gpg_fingerprint" \
  | gpg --homedir "$backup_gnupg" --batch --import >/dev/null 2>&1
if gpg --homedir "$backup_gnupg" --batch --with-colons --list-secret-keys 2>/dev/null \
    | grep -Eq '^(sec|ssb):'; then
  fail "backup keyring unexpectedly contains a private key"
fi
printf 'Ephemeral GPG separation validated; fingerprint value intentionally not logged.\n'

backup_repository="$work_root/backup-repository"
backup_runtime="$work_root/backup-runtime"
compromised_repository="$work_root/compromised-backup-repository"
mkdir -m 0700 -- "$backup_runtime"
expect_failure backup-host-secret-key 'chave privada de restore' \
  run_backup "$recovery_gnupg" "$compromised_repository" "$backup_runtime"

backup_started_utc="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
backup_started_epoch="$(date -u +%s)"
run_backup "$backup_gnupg" "$backup_repository" "$backup_runtime"
backup_completed_utc="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
backup_completed_epoch="$(date -u +%s)"
backup_duration_seconds=$((backup_completed_epoch - backup_started_epoch))

mapfile -t source_bundles < <(find "$backup_repository/daily" -mindepth 1 -maxdepth 1 -type d -name 'agendafacil-*' -print)
[[ ${#source_bundles[@]} -eq 1 ]] || fail "backup did not publish exactly one daily bundle"
source_bundle="${source_bundles[0]}"
mapfile -t encrypted_archives < <(find "$source_bundle" -maxdepth 1 -type f -name '*.dump.gpg' -print)
mapfile -t checksum_files < <(find "$source_bundle" -maxdepth 1 -type f -name '*.dump.gpg.sha256' -print)
[[ ${#encrypted_archives[@]} -eq 1 && ${#checksum_files[@]} -eq 1 ]] \
  || fail "backup bundle is incomplete"
if find "$backup_repository" "$backup_runtime" -type f -name '*.dump' -print -quit | grep -q .; then
  fail "plaintext dump escaped the private runtime lifecycle"
fi

bundle_name="$(basename -- "$source_bundle")"
simulated_offsite_root="$work_root/simulated-offsite-boundary"
recovered_bundle="$simulated_offsite_root/$bundle_name"
mkdir -p -m 0700 -- "$recovered_bundle"
cp -- "${encrypted_archives[0]}" "${checksum_files[0]}" "$recovered_bundle/"
[[ "$(find "$recovered_bundle" -maxdepth 1 -type f | wc -l | tr -d '[:space:]')" == "2" ]] \
  || fail "simulated off-site boundary contains unexpected files"
(
  cd "$recovered_bundle"
  sha256sum --check --strict "$(basename -- "${checksum_files[0]}")"
)
[[ "$backup_repository" == "$work_root/backup-repository" ]] || fail "unsafe source repository path"
rm -rf -- "$backup_repository"
[[ ! -e "$source_bundle" && -d "$recovered_bundle" ]] \
  || fail "original local bundle was not discarded before recovery"
printf 'Simulated off-site transfer validated; original local repository discarded.\n'

recovery_started_utc="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
recovery_started_epoch="$(date -u +%s)"
recovery_port="$(start_postgres "$recovery_container" "$recovery_database")"
printf 'Recovery PostgreSQL server: %s\n' "$(scalar_query "$recovery_port" "$recovery_database" 'SHOW server_version;')"

incomplete_bundle="$work_root/incomplete-bundle"
mkdir -m 0700 -- "$incomplete_bundle"
cp -- "$recovered_bundle"/*.dump.gpg "$incomplete_bundle/"
expect_failure incomplete-bundle 'checksum correspondente não encontrado' \
  run_restore "$incomplete_bundle" "$recovery_database" "$recovery_gnupg"
assert_empty_database

expect_failure mismatched-target 'RESTORE_CONFIRM_DATABASE deve ser idêntico a PGDATABASE' \
  run_restore "$recovered_bundle" wrong_database "$recovery_gnupg"
assert_empty_database

tampered_bundle="$work_root/tampered-bundle"
cp -a -- "$recovered_bundle" "$tampered_bundle"
tampered_checksum="$(find "$tampered_bundle" -maxdepth 1 -type f -name '*.sha256' -print -quit)"
tampered_archive_name="$(basename -- "$(find "$tampered_bundle" -maxdepth 1 -type f -name '*.dump.gpg' -print -quit)")"
printf '%064d  %s\n' 0 "$tampered_archive_name" > "$tampered_checksum"
expect_failure tampered-checksum 'FAILED' \
  run_restore "$tampered_bundle" "$recovery_database" "$recovery_gnupg"
assert_empty_database

expect_failure missing-private-key '' \
  run_restore "$recovered_bundle" "$recovery_database" "$wrong_gnupg"
assert_empty_database

psql_database "$recovery_port" "$recovery_database" \
  --command='CREATE TABLE rehearsal_nonempty_guard(id integer PRIMARY KEY);' >/dev/null
expect_failure nonempty-target 'o banco alvo não está vazio' \
  run_restore "$recovered_bundle" "$recovery_database" "$recovery_gnupg"
psql_database "$recovery_port" "$recovery_database" \
  --command='DROP TABLE rehearsal_nonempty_guard;' >/dev/null
assert_empty_database

run_restore "$recovered_bundle" "$recovery_database" "$recovery_gnupg"

restored_canary="$(scalar_query "$recovery_port" "$recovery_database" "$canary_query")"
restored_establishments="$(scalar_query "$recovery_port" "$recovery_database" 'SELECT count(*) FROM establishments;')"
restored_appointments="$(scalar_query "$recovery_port" "$recovery_database" 'SELECT count(*) FROM appointments;')"
restored_flyway_rows="$(scalar_query "$recovery_port" "$recovery_database" 'SELECT count(*) FROM flyway_schema_history;')"
[[ "$restored_canary" == "$source_canary" ]] || fail "restored canary values differ from source"
[[ "$restored_establishments" == "$source_establishments" ]] \
  || fail "restored establishment count differs from source"
[[ "$restored_appointments" == "$source_appointments" ]] \
  || fail "restored appointment count differs from source"
[[ "$restored_flyway_rows" == "$source_flyway_rows" ]] \
  || fail "restored Flyway history differs from source"

recovery_app_log="$work_root/recovery-application.log"
start_application "$recovery_port" "$recovery_database" 18082 18083 "$recovery_app_log"
curl --fail --silent --max-time 5 http://127.0.0.1:18082/login >/dev/null
public_page="$work_root/recovered-public-page.html"
curl --fail --silent --max-time 5 \
  http://127.0.0.1:18082/agenda/recovery-rehearsal-canary > "$public_page"
grep -Fq 'Recovery Rehearsal Studio' "$public_page" \
  || fail "tenant-safe public smoke did not read the restored canary"
flyway_rows_after_start="$(scalar_query "$recovery_port" "$recovery_database" \
  'SELECT count(*) FROM flyway_schema_history;')"
[[ "$flyway_rows_after_start" == "$restored_flyway_rows" ]] \
  || fail "application startup applied an unexpected Flyway change"
stop_application

recovery_completed_utc="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
recovery_completed_epoch="$(date -u +%s)"
recovery_duration_seconds=$((recovery_completed_epoch - recovery_started_epoch))

report="$work_root/recovery-rehearsal-report.txt"
cat > "$report" <<REPORT
AgendaFacil recovery rehearsal: PASS
Boundary: simulated off-site boundary; this is not proof of real off-site storage
Backup started UTC: $backup_started_utc
Backup completed UTC: $backup_completed_utc
Backup duration seconds: $backup_duration_seconds
Recovery started UTC: $recovery_started_utc
Recovery completed UTC: $recovery_completed_utc
Recovery duration seconds: $recovery_duration_seconds
Bundle: $bundle_name
Source/restored establishments: $source_establishments/$restored_establishments
Source/restored appointments: $source_appointments/$restored_appointments
Canary SHA-256: $source_canary_digest
Application smoke: /login OK; tenant-safe public page OK
Readiness: UP
Production RPO/RTO proof: NOT ESTABLISHED by this disposable CI rehearsal
REPORT
cat "$report"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    printf '## Recovery rehearsal\n\n```text\n'
    cat "$report"
    printf '```\n'
  } >> "$GITHUB_STEP_SUMMARY"
fi
