#!/usr/bin/env bash
# ── Deploy kinotic-frontend to Azure Static Web App ───────
#
# Usage:
#   ./deploy.sh                    # build + deploy
#   ./deploy.sh --deploy-only      # deploy existing dist/
#
# Requires: pnpm, az cli, terraform (for deployment token)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="${SCRIPT_DIR}/../../../../kinotic-frontend"

DEPLOY_ONLY=false
[[ "${1:-}" == "--deploy-only" ]] && DEPLOY_ONLY=true

# Build
if [[ "$DEPLOY_ONLY" == "false" ]]; then
  echo "==> Building kinotic-frontend"
  cd "$FRONTEND_DIR"
  pnpm install
  pnpm build --mode production
  echo "==> Build complete: $(du -sh dist | cut -f1)"
fi

# Get deployment token from terraform
cd "$SCRIPT_DIR"
TOKEN=$(terraform output -raw deployment_token 2>/dev/null)
if [[ -z "$TOKEN" ]]; then
  echo "ERROR: No deployment token. Run 'terraform apply' first."
  exit 1
fi

# Deploy
echo "==> Deploying to Static Web App"
cd "$FRONTEND_DIR"
npx @azure/static-web-apps-cli deploy dist \
  --deployment-token "$TOKEN" \
  --env production

echo "==> Deployed to $(cd "$SCRIPT_DIR" && terraform output -raw portal_url)"
