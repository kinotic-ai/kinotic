#!/usr/bin/env bash
# Builds the portal and the system console for the development server and uploads them into
# the sites account, where Front Door serves each from sites/<hostname>/ exactly as it serves
# a published site. Run after `terraform apply` here, and again whenever kinotic-frontend
# changes; the account's blob role comes from this root, and Front Door's cache is purged.
#
#   deploy-ui.sh                # build + upload both
#   deploy-ui.sh --deploy-only  # upload the existing dist/ directories
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND="$HERE/../../../../kinotic-frontend"
DEPLOY_ONLY=false
[[ "${1:-}" == "--deploy-only" ]] && DEPLOY_ONLY=true

group=$(terraform -chdir="$HERE" output -raw resource_group_name 2>/dev/null || echo rg-kinotic-dev)
endpoint=$(terraform -chdir="$HERE" output -raw sites_storage_blob_endpoint)
account=$(sed -E 's#https://([^.]+)\..*#\1#' <<<"$endpoint")
profile=$(terraform -chdir="$HERE" output -raw sites_frontdoor_profile)
afd_endpoint=$(terraform -chdir="$HERE" output -raw sites_frontdoor_endpoint)

if [[ "$DEPLOY_ONLY" == "false" ]]; then
  (cd "$FRONTEND" && pnpm install --frozen-lockfile)
fi

deploy() {
  local app="$1" hostname="$2"
  if [[ "$DEPLOY_ONLY" == "false" ]]; then
    echo "==> Building $app for $hostname"
    (cd "$FRONTEND/apps/$app" && pnpm build --mode dev-server)
  fi
  echo "==> Uploading $app to sites/$hostname/"
  az storage blob upload-batch --auth-mode login --account-name "$account" \
    --destination sites --destination-path "$hostname" \
    --source "$FRONTEND/apps/$app/dist" --overwrite --only-show-errors --no-progress >/dev/null 2>&1
}

deploy portal "$(terraform -chdir="$HERE" output -raw portal_hostname)"
deploy system "$(terraform -chdir="$HERE" output -raw console_hostname)"

echo "==> Purging Front Door"
az afd endpoint purge --resource-group "$group" --profile-name "$profile" --endpoint-name "$afd_endpoint" \
  --domains "$(terraform -chdir="$HERE" output -raw portal_hostname)" "$(terraform -chdir="$HERE" output -raw console_hostname)" \
  --content-paths '/*' --only-show-errors
echo "Deployed: https://$(terraform -chdir="$HERE" output -raw portal_hostname) and https://$(terraform -chdir="$HERE" output -raw console_hostname)"
