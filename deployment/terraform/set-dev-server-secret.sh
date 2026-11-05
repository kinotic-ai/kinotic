#!/usr/bin/env bash
# Stores one of the development server's operator secrets in their keychain, replacing the entry when
# it exists, so a rotation is the same command. security prompts for the value, without echo.
#
#   set-dev-server-secret.sh <kinotic-cloudflare-api-token|kinotic-cloudflare-dns-api-token|kinotic-proxmox-password>
#
# The keychain is $KINOTIC_KEYCHAIN, ~/Library/Keychains/kinotic.keychain-db by default, the one
# with-dev-server-secrets.sh reads.
set -euo pipefail

KEYCHAIN="${KINOTIC_KEYCHAIN:-$HOME/Library/Keychains/kinotic.keychain-db}"
SERVICE="${1:-}"
case "$SERVICE" in
  kinotic-cloudflare-api-token|kinotic-cloudflare-dns-api-token|kinotic-proxmox-password) ;;
  *) echo "usage: $0 <kinotic-cloudflare-api-token|kinotic-cloudflare-dns-api-token|kinotic-proxmox-password>" >&2; exit 2 ;;
esac
[[ -f "$KEYCHAIN" ]] || { echo "No keychain at $KEYCHAIN; proxmox/README.md, \"Operator secrets\", creates it" >&2; exit 1; }
if ! security show-keychain-info "$KEYCHAIN" >/dev/null 2>&1; then
  security unlock-keychain "$KEYCHAIN"
fi

# The value is read here and handed to security on its stdin, so it is never an argument of any process;
# without -w security stores an empty password, and -w prompts only as the last argument, where the
# keychain cannot follow it
printf '%s: ' "$SERVICE"
IFS= read -rs SECRET
echo
[[ -n "$SECRET" ]] || { echo "No value given; nothing stored" >&2; exit 1; }
# in interactive mode a double-quoted argument takes a backslash as escaping the next character
ESCAPED=${SECRET//\\/\\\\}
ESCAPED=${ESCAPED//\"/\\\"}
printf 'add-generic-password -U -a "%s" -s "%s" -w "%s" "%s"\n' "$USER" "$SERVICE" "$ESCAPED" "$KEYCHAIN" | security -i
unset SECRET ESCAPED
echo "Stored $SERVICE in $KEYCHAIN"
