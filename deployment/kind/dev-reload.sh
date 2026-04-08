#!/usr/bin/env bash
# ── Dev Reload ────────────────────────────────────────────
# Rebuilds, loads, and restarts kinotic-server in the KinD cluster.
#
# Usage:
#   ./dev-reload.sh                  # rebuild server only
#   ./dev-reload.sh --migration      # rebuild server + migration
#   ./dev-reload.sh --all            # rebuild server + migration
#
# Assumes:
#   - KinD cluster "kinotic-cluster" is running
#   - Gradle wrapper is at the repo root

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CLUSTER_NAME="${KIND_CLUSTER_NAME:-kinotic-cluster}"
SERVICE_NAMESPACE="${KIND_SERVICE_NAMESPACE:-kinotic}"
VERSION="${KINOTIC_VERSION:-4.2.0-SNAPSHOT}"
CONTEXT="kind-${CLUSTER_NAME}"

BUILD_MIGRATION=false

for arg in "$@"; do
  case "$arg" in
    --migration|--all) BUILD_MIGRATION=true ;;
    --help|-h)
      echo "Usage: $0 [--migration|--all]"
      echo "  --migration, --all  Also rebuild and reload the migration image"
      exit 0
      ;;
  esac
done

echo "==> Building kinotic-server:${VERSION}"
cd "$REPO_ROOT"
./gradlew :kinotic-server:bootBuildImage -q

echo "==> Loading kinotic-server:${VERSION} into KinD"
kind load docker-image "kinoticai/kinotic-server:${VERSION}" --name "$CLUSTER_NAME"

if [ "$BUILD_MIGRATION" = true ]; then
  echo "==> Building kinotic-migration:${VERSION}"
  ./gradlew :kinotic-migration:bootBuildImage -q

  echo "==> Loading kinotic-migration:${VERSION} into KinD"
  kind load docker-image "kinoticai/kinotic-migration:${VERSION}" --name "$CLUSTER_NAME"
fi

echo "==> Restarting kinotic-server deployment"
kubectl rollout restart deployment/kinotic-server -n "$SERVICE_NAMESPACE" --context "$CONTEXT"
kubectl rollout status deployment/kinotic-server -n "$SERVICE_NAMESPACE" --context "$CONTEXT" --timeout=120s

echo "==> Done. Server is live at https://localhost/"
