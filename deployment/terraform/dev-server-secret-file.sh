#!/usr/bin/env bash
# Reads and writes the files the development server's containers read (generate-secrets.sh writes
# them, sync-secrets.sh places them on the host). Each is kept in the operator keychain under its
# path in the host's secrets directory, a large one over several entries, so no file of this Mac
# holds one and a copy of the keychain is the backup of them all.
#
#   dev-server-secret-file.sh list             the paths, one per line
#   dev-server-secret-file.sh get <path>       the file, on stdout
#   dev-server-secret-file.sh put <path>       stores stdin as the file, replacing it when it exists
#   dev-server-secret-file.sh edit <path>      opens the file in $EDITOR and stores what is saved
#   dev-server-secret-file.sh setenv <path> <NAME>...
#                                              prompts for each named setting of an env file, without
#                                              echo, and stores it; an empty answer keeps the setting
#
# The keychain is $KINOTIC_KEYCHAIN, ~/Library/Keychains/kinotic.keychain-db by default, the one
# with-dev-server-secrets.sh reads.
set -euo pipefail

KEYCHAIN="${KINOTIC_KEYCHAIN:-$HOME/Library/Keychains/kinotic.keychain-db}"
SERVICE="kinotic-dev-server-file"
PART_SIZE=3500
usage() { sed -n '7,13p' "$0" | cut -c3- >&2; exit 2; }

[[ -f "$KEYCHAIN" ]] || { echo "No keychain at $KEYCHAIN; proxmox/README.md, \"Operator secrets\", creates it" >&2; exit 1; }
security show-keychain-info "$KEYCHAIN" >/dev/null 2>&1 \
  || { echo "$KEYCHAIN is locked; unlock it with: security unlock-keychain $KEYCHAIN" >&2; exit 1; }

list() {
  # dump-keychain prints each entry's attributes after a line naming its keychain, the account before the service
  security dump-keychain "$KEYCHAIN" | awk -v service="\"$SERVICE\"" '
    /^keychain: /      { account = "" }
    /"acct"<blob>="/   { account = $0; sub(/^.*"acct"<blob>="/, "", account); sub(/"$/, "", account) }
    /"svce"<blob>=/    { value = $0; sub(/^.*"svce"<blob>=/, "", value); if (value == service && account != "" && account !~ /#/) print account }
  ' | sort
}

get() {
  local part=2
  {
    security find-generic-password -s "$SERVICE" -a "$1" -w "$KEYCHAIN" 2>/dev/null
    while security find-generic-password -s "$SERVICE" -a "$1#$part" -w "$KEYCHAIN" 2>/dev/null; do part=$((part + 1)); done
  } | tr -d '\n' | base64 -d | gunzip 2>/dev/null \
    || { echo "No $1 among the dev server's secret files" >&2; return 1; }
}

put() {
  [[ "$1" =~ ^[A-Za-z0-9._/-]+$ ]] || { echo "$1 is not a path of letters, digits, dots, dashes and slashes" >&2; return 1; }
  # base64 keeps a file of many lines one value, of characters security's interactive mode takes unquoted;
  # the value is handed over on stdin, so it is never an argument of any process
  local value account part=1
  value="$(gzip -9 | base64 | tr -d '\n')"
  # security's interactive mode reads a command of at most 4096 characters, and stores nothing of a longer
  # one, so the value is kept in parts: the first under the path, each later one under <path>#<number>
  while [[ -n "$value" ]]; do
    account="$1"
    [[ $part -eq 1 ]] || account="$1#$part"
    printf 'add-generic-password -U -a "%s" -s "%s" -w "%s" "%s"\n' "$account" "$SERVICE" "${value:0:$PART_SIZE}" "$KEYCHAIN" \
      | security -i
    value="${value:$PART_SIZE}"
    part=$((part + 1))
  done
  # the parts a longer file left behind
  while security delete-generic-password -s "$SERVICE" -a "$1#$part" "$KEYCHAIN" >/dev/null 2>&1; do part=$((part + 1)); done
}

edit() {
  local file
  file="$(mktemp)"
  trap 'rm -f "$file"' EXIT
  get "$1" > "$file" 2>/dev/null || true
  "${EDITOR:-vi}" "$file"
  put "$1" < "$file"
}

setenv() {
  local file="$1" lines name value
  lines="$(get "$file" 2>/dev/null || true)"
  shift
  for name in "$@"; do
    printf '%s: ' "$name" >&2
    IFS= read -rs value
    echo >&2
    if [[ -n "$value" ]]; then
      lines="$(printf '%s\n' "$lines" | grep -v "^$name=" || true)"
      lines="$(printf '%s\n%s=%s' "$lines" "$name" "$value")"
    fi
  done
  printf '%s\n' "$lines" | sed '/./,$!d' | put "$file"
}

case "${1:-}" in
  list) [[ $# -eq 1 ]] || usage; list ;;
  get|put|edit) [[ $# -eq 2 ]] || usage; "$1" "$2" ;;
  setenv) [[ $# -ge 3 ]] || usage; shift; setenv "$@" ;;
  *) usage ;;
esac
