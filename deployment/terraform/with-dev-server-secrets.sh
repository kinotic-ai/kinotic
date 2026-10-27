#!/usr/bin/env bash
# Runs a command with the development server's operator secrets in its environment, read from a
# keychain of their own, so no checkout file holds them: the Cloudflare tokens the dev-server Azure
# root applies with, and root@pam's password the proxmox root applies with.
#
#   with-dev-server-secrets.sh <command> [args...]
#
#   with-dev-server-secrets.sh terraform -chdir=deployment/terraform/azure/dev-server apply
#   with-dev-server-secrets.sh deployment/terraform/proxmox/redeploy.sh
#
# The keychain is $KINOTIC_KEYCHAIN, ~/Library/Keychains/kinotic.keychain-db by default; a locked
# keychain is unlocked first, with its password from the terminal. Creating it and its entries is
# in proxmox/README.md under "Operator secrets".
set -euo pipefail

KEYCHAIN="${KINOTIC_KEYCHAIN:-$HOME/Library/Keychains/kinotic.keychain-db}"
[[ $# -gt 0 ]] || { echo "usage: $0 <command> [args...]" >&2; exit 2; }
[[ -f "$KEYCHAIN" ]] || { echo "No keychain at $KEYCHAIN; proxmox/README.md, \"Operator secrets\", creates it" >&2; exit 1; }

# show-keychain-info fails on a locked keychain, and a read from one fails rather than prompting
if ! security show-keychain-info "$KEYCHAIN" >/dev/null 2>&1; then
  # unlock-keychain prompts on the controlling terminal, which waits forever when no one is at it
  [[ -t 0 ]] || { echo "$KEYCHAIN is locked; unlock it with: security unlock-keychain $KEYCHAIN" >&2; exit 1; }
  security unlock-keychain "$KEYCHAIN"
fi

secret() {   # <service>
  security find-generic-password -s "$1" -w "$KEYCHAIN" 2>/dev/null \
    || { echo "No $1 entry in $KEYCHAIN; proxmox/README.md, \"Operator secrets\", adds it" >&2; return 1; }
}

# Assigned before export, so a missing entry stops the script under set -e
TF_VAR_cloudflare_api_token="$(secret kinotic-cloudflare-api-token)"
TF_VAR_cloudflare_dns_api_token="$(secret kinotic-cloudflare-dns-api-token)"
TF_VAR_proxmox_password="$(secret kinotic-proxmox-password)"
export TF_VAR_cloudflare_api_token TF_VAR_cloudflare_dns_api_token TF_VAR_proxmox_password

exec "$@"
