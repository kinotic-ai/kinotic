#!/usr/bin/env bash
# ── Dev Reload ────────────────────────────────────────────
# Rebuilds, loads, and restarts the Kinotic servers in the KinD cluster.
#
# Usage:
#   ./dev-reload.sh                  # rebuild the org, system and app servers
#   ./dev-reload.sh app              # rebuild the named servers only: org, system, app
#   ./dev-reload.sh --migration      # rebuild the servers + migration
#   ./dev-reload.sh --all            # rebuild the servers + migration
#
# Assumes:
#   - KinD cluster "kinotic-cluster" is running
#   - Gradle wrapper is at the repo root

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CLUSTER_NAME="${KIND_CLUSTER_NAME:-kinotic-cluster}"
SERVICE_NAMESPACE="${KIND_SERVICE_NAMESPACE:-kinotic}"
VERSION="${KINOTIC_VERSION:-$(sed -n 's/^kinoticVersion=//p' "$REPO_ROOT/gradle.properties")}"
CONTEXT="kind-${CLUSTER_NAME}"

export RUNNING_KIND_CLUSTER="true"

BUILD_MIGRATION=false
SERVERS=()

for arg in "$@"; do
  case "$arg" in
    --migration|--all) BUILD_MIGRATION=true ;;
    org|system|app) SERVERS+=("kinotic-$arg-server") ;;
    --help|-h)
      echo "Usage: $0 [--migration|--all] [org|system|app ...]"
      echo "  --migration, --all  Also rebuild and reload the migration image"
      echo "  org, system, app    Rebuild only these servers (default: all three)"
      exit 0
      ;;
    *) echo "Unknown argument: $arg" >&2; exit 1 ;;
  esac
done
[ ${#SERVERS[@]} -eq 0 ] && SERVERS=(kinotic-server-org kinotic-server-system kinotic-server-app)

cd "$REPO_ROOT"
for server in "${SERVERS[@]}"; do
  echo "==> Building ${server}:${VERSION}"
  ./gradlew ":${server}:bootBuildImage" -q

  echo "==> Loading ${server}:${VERSION} into KinD"
  kind load docker-image "kinoticai/${server}:${VERSION}" --name "$CLUSTER_NAME"
done

if [ "$BUILD_MIGRATION" = true ]; then
  echo "==> Building kinotic-migration:${VERSION}"
  ./gradlew :kinotic-migration:bootBuildImage -q

  echo "==> Loading kinotic-migration:${VERSION} into KinD"
  kind load docker-image "kinoticai/kinotic-migration:${VERSION}" --name "$CLUSTER_NAME"
fi

for server in "${SERVERS[@]}"; do
  echo "==> Restarting the ${server} deployment"
  kubectl rollout restart "deployment/${server}" -n "$SERVICE_NAMESPACE" --context "$CONTEXT"
done
for server in "${SERVERS[@]}"; do
  kubectl rollout status "deployment/${server}" -n "$SERVICE_NAMESPACE" --context "$CONTEXT" --timeout=120s
done

echo "==> Done. The portal is live at https://localhost/"
