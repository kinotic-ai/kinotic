#!/usr/bin/env bash
# Builds the portal and the system console for an environment and uploads them into its sites
# account, where the sites Worker serves each from sites/<hostname>/ exactly as it serves a
# published site: the cluster's at portal.<zone> and console.<zone>, the development server's at
# portal.<zone> and console.<zone>. Run after the root's `terraform apply`, and again
# whenever kinotic-frontend changes; the account's blob role comes from the root, and the edge
# keeps a site's index for a minute and its hashed assets by name, so a new build shows within
# the minute.
#
#   deploy-ui.sh <root> <mode>                 # pnpm build --mode <mode> for both apps, then upload
#   deploy-ui.sh <root> <mode> --deploy-only   # upload the existing dist/ directories
#
#   deploy-ui.sh cluster azure                 # the cluster: .env.azure names its API hosts
#   deploy-ui.sh dev-server dev-server         # the development server: .env.dev-server
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND="$HERE/../../../kinotic-frontend"
ROOT="$HERE/${1:?usage: deploy-ui.sh <root> <mode> [--deploy-only]}"
MODE="${2:?usage: deploy-ui.sh <root> <mode> [--deploy-only]}"
DEPLOY_ONLY=false
[[ "${3:-}" == "--deploy-only" ]] && DEPLOY_ONLY=true

endpoint=$(terraform -chdir="$ROOT" output -raw sites_storage_blob_endpoint)
account=$(sed -E 's#https://([^.]+)\..*#\1#' <<<"$endpoint")

if [[ "$DEPLOY_ONLY" == "false" ]]; then
  (cd "$FRONTEND" && pnpm install --frozen-lockfile)
fi

# The tree the build came from, recorded in each app's version.json the way a published site's
# is, so redeploy.sh in the Proxmox root can tell whether the deployed UIs are current
tree=$(git -C "$FRONTEND" rev-parse HEAD:kinotic-frontend)
[[ -n "$(git -C "$FRONTEND" status --porcelain -- .)" ]] && tree="$tree-dirty"

deploy() {
  local app="$1" hostname="$2"
  if [[ "$DEPLOY_ONLY" == "false" ]]; then
    echo "==> Building $app for $hostname"
    (cd "$FRONTEND/apps/$app" && pnpm build --mode "$MODE")
    printf '{"commitSha":"%s"}\n' "$tree" > "$FRONTEND/apps/$app/dist/version.json"
  fi
  echo "==> Uploading $app to sites/$hostname/"
  az storage blob upload-batch --auth-mode login --account-name "$account" \
    --destination sites --destination-path "$hostname" \
    --source "$FRONTEND/apps/$app/dist" --overwrite --only-show-errors --no-progress >/dev/null 2>&1
}

portal_hostname=$(terraform -chdir="$ROOT" output -raw portal_hostname)
console_hostname=$(terraform -chdir="$ROOT" output -raw console_hostname)
deploy portal "$portal_hostname"
deploy system "$console_hostname"
echo "Deployed: https://$portal_hostname and https://$console_hostname"
