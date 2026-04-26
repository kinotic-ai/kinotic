#!/usr/bin/env bash
#
# Sets/updates a single OIDC platform-config client secret in the k8s Secret that
# kinotic-server's OidcSecrets volume mounts. Plaintext only ever touches your terminal
# session — never IaC, never the repo, never disk outside kubectl's encrypted etcd.
#
# Usage: deployment/scripts/set-oidc-client-secret.sh <configId> [-n namespace] [-s secretName]
#
# Example:
#   deployment/scripts/set-oidc-client-secret.sh entra-platform
#   deployment/scripts/set-oidc-client-secret.sh google-platform -n kinotic
#
# After the script reports success, restart kinotic-server so PlatformOidcBootstrap
# picks up the new value:
#   kubectl -n <namespace> rollout restart deployment/kinotic-server
#
set -euo pipefail

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") <configId> [-n namespace] [-s secretName]

  configId    The OidcConfiguration id (matches kinotic.oidc.platformProviders[*].id and
              the IdP's registered redirect URI <appBaseUrl>/api/login/callback/<configId>).
  -n          Kubernetes namespace (default: kinotic).
  -s          Name of the k8s Secret to populate (default: kinotic-oidc-secrets,
              must match oidcSecrets.secretName in helm values).
EOF
  exit 1
}

CONFIG_ID="${1:-}"
[ -z "$CONFIG_ID" ] && usage
shift

NAMESPACE="kinotic"
SECRET_NAME="kinotic-oidc-secrets"

while getopts "n:s:h" opt; do
  case "$opt" in
    n) NAMESPACE="$OPTARG" ;;
    s) SECRET_NAME="$OPTARG" ;;
    h|*) usage ;;
  esac
done

read -srp "Client secret for $CONFIG_ID: " VALUE
echo
[ -z "$VALUE" ] && { echo "Empty value, aborting." >&2; exit 1; }

if ! kubectl -n "$NAMESPACE" get secret "$SECRET_NAME" >/dev/null 2>&1; then
  kubectl -n "$NAMESPACE" create secret generic "$SECRET_NAME" --from-literal="$CONFIG_ID=$VALUE"
  echo "Created $NAMESPACE/$SECRET_NAME with key '$CONFIG_ID'."
else
  ENCODED=$(printf '%s' "$VALUE" | base64 | tr -d '\n')
  kubectl -n "$NAMESPACE" patch secret "$SECRET_NAME" --type=merge \
    -p "{\"data\":{\"$CONFIG_ID\":\"$ENCODED\"}}"
  echo "Updated key '$CONFIG_ID' in $NAMESPACE/$SECRET_NAME."
fi

cat <<EOF

Next: restart kinotic-server so PlatformOidcBootstrap reads the new value:

  kubectl -n $NAMESPACE rollout restart deployment/kinotic-server
EOF
