#!/usr/bin/env bash
set -euo pipefail

umask 0077
export LC_ALL=C

fail() {
  printf 'ERRO: %s\n' "$1" >&2
  exit 1
}

[[ $# -eq 4 ]] \
  || fail "uso: backup-retention.sh REPOSITORY DAILY_BUNDLE ISO_WEEK MONTH"

repository="$1"
source_bundle="$2"
week_key="$3"
month_key="$4"

[[ "$repository" == /* && -d "$repository/daily" && -d "$repository/weekly" && -d "$repository/monthly" ]] \
  || fail "repository inválido"
[[ ! -L "$repository" && ! -L "$repository/daily" && ! -L "$repository/weekly" && ! -L "$repository/monthly" ]] \
  || fail "repository e categorias não podem ser links simbólicos"
[[ "$week_key" =~ ^[0-9]{4}-W[0-9]{2}$ ]] || fail "ISO week inválida"
[[ "$month_key" =~ ^[0-9]{4}-[0-9]{2}$ ]] || fail "mês inválido"

bundle_name="$(basename -- "$source_bundle")"
[[ "$bundle_name" =~ ^agendafacil-[0-9]{8}T[0-9]{6}Z$ ]] \
  || fail "nome de bundle daily inválido"
[[ "$source_bundle" == "$repository/daily/$bundle_name" && -d "$source_bundle" ]] \
  || fail "bundle daily não pertence ao repository"

verify_bundle() {
  local bundle="$1"
  local -a encrypted_files checksum_files
  local checksum_digest checksum_target checksum_extra checksum_lines
  [[ -d "$bundle" && ! -L "$bundle" ]] || fail "bundle inválido ou simbólico"
  shopt -s nullglob
  encrypted_files=("$bundle"/agendafacil-*.dump.gpg)
  checksum_files=("$bundle"/agendafacil-*.dump.gpg.sha256)
  shopt -u nullglob
  [[ ${#encrypted_files[@]} -eq 1 && ${#checksum_files[@]} -eq 1 ]] \
    || fail "bundle deve conter exatamente archive criptografado e checksum"
  [[ "${checksum_files[0]}" == "${encrypted_files[0]}.sha256" ]] \
    || fail "checksum não corresponde ao archive"
  checksum_lines="$(awk 'END { print NR }' "${checksum_files[0]}")"
  read -r checksum_digest checksum_target checksum_extra < "${checksum_files[0]}" \
    || fail "registro de checksum inválido"
  [[ "$checksum_lines" == "1" && "$checksum_digest" =~ ^[[:xdigit:]]{64}$ \
      && "$checksum_target" == "$(basename -- "${encrypted_files[0]}")" \
      && -z "$checksum_extra" ]] \
    || fail "registro de checksum não corresponde exatamente ao archive"
  (cd "$bundle" && sha256sum --check --strict "$(basename -- "${checksum_files[0]}")" >/dev/null)
}

partial_snapshot=""
cleanup() {
  if [[ -n "$partial_snapshot" && -d "$partial_snapshot" ]]; then
    rm -rf -- "$partial_snapshot"
  fi
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM

copy_period_snapshot() {
  local category="$1"
  local period_key="$2"
  local destination="$repository/$category/$period_key"

  if [[ -d "$destination" ]]; then
    verify_bundle "$destination"
    return
  fi

  partial_snapshot="$repository/$category/.${period_key}.partial.$$"
  mkdir -m 0700 -- "$partial_snapshot"
  cp -- "$source_bundle"/*.dump.gpg "$source_bundle"/*.dump.gpg.sha256 "$partial_snapshot/"
  verify_bundle "$partial_snapshot"
  mv -T -- "$partial_snapshot" "$destination"
  partial_snapshot=""
}

trim_directories() {
  local category="$1"
  local keep="$2"
  local regex="$3"
  local directory="$repository/$category"
  local -a entries=()
  local entry name remove_count index

  shopt -s nullglob
  entries=("$directory"/*)
  shopt -u nullglob

  local -a valid_entries=()
  for entry in "${entries[@]}"; do
    [[ -d "$entry" ]] || continue
    [[ ! -L "$entry" ]] || fail "entrada simbólica inesperada em $category"
    name="$(basename -- "$entry")"
    [[ "$name" =~ $regex ]] || fail "entrada inesperada em $category: $name"
    valid_entries+=("$entry")
  done

  remove_count=$((${#valid_entries[@]} - keep))
  (( remove_count > 0 )) || return 0
  for ((index = 0; index < remove_count; index++)); do
    rm -rf -- "${valid_entries[$index]}"
  done
}

verify_bundle "$source_bundle"
copy_period_snapshot weekly "$week_key"
copy_period_snapshot monthly "$month_key"

trim_directories daily 14 '^agendafacil-[0-9]{8}T[0-9]{6}Z$'
trim_directories weekly 8 '^[0-9]{4}-W[0-9]{2}$'
trim_directories monthly 6 '^[0-9]{4}-[0-9]{2}$'
