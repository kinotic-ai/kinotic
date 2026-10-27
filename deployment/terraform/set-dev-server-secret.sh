#!/usr/bin/env bash
# Stores one of the development server's operator secrets in their keychain, replacing the entry when
# it exists, so a rotation is the same command. The value is read from the terminal without echo.
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

read -rsp "$SERVICE: " value
echo
[[ -n "$value" ]] || { echo "Empty value; nothing stored" >&2; exit 1; }
# The keychain path follows -w's value: given after a bare -w, security takes the path as the value
# and stores the entry in the login keychain
security add-generic-password -U -a "$USER" -s "$SERVICE" -w "$value" "$KEYCHAIN"
echo "Stored $SERVICE in $KEYCHAIN"
