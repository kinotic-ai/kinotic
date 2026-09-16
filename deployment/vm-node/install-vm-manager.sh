#!/usr/bin/env bash
# Installs the vm-manager on a provisioned node and registers it as a systemd service.
# Idempotent: re-running upgrades the package to VM_MANAGER_VERSION and rewrites the unit.
#
# The service reads its configuration from /etc/kinotic/vm-manager.env and the machine
# credentials from /etc/kinotic/vm-manager.secrets.env, and does not start until the latter
# exists: the SYSTEM machine it connects as is created in the system console once the server
# is up, which is after this script has run on a freshly provisioned node.
set -euo pipefail
fail() { echo "INSTALL FAILED: $*" >&2; exit 1; }

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PREFIX=/opt/kinotic/vm-manager
VM_MANAGER_VERSION="${VM_MANAGER_VERSION:-latest}"

[ "$(id -u)" -eq 0 ] || fail "run as root (sudo $0)"

# The vm-manager uses Bun's runtime API, so it runs under bun rather than node. Bun's
# installer unpacks a zip, and a minimized Ubuntu ships without unzip.
if ! command -v bun >/dev/null 2>&1; then
    apt-get install -y -qq unzip >/dev/null
    curl -fsSL https://bun.sh/install | BUN_INSTALL=/usr/local bash >/dev/null
fi
command -v bun >/dev/null 2>&1 || fail "bun did not install"
echo "  bun $(bun --version)"

mkdir -p "$PREFIX" /etc/kinotic
cd "$PREFIX"
[ -f package.json ] || printf '{\n  "name": "kinotic-vm-manager-node",\n  "private": true\n}\n' > package.json
# The package's peers (core, management-api, system-api) come with it: bun installs peer
# dependencies by default
bun add "@kinotic-ai/vm-manager@$VM_MANAGER_VERSION" >/dev/null
echo "  @kinotic-ai/vm-manager $(node -e "console.log(require('$PREFIX/node_modules/@kinotic-ai/vm-manager/package.json').version)" 2>/dev/null || bun -e "console.log(require('$PREFIX/node_modules/@kinotic-ai/vm-manager/package.json').version)")"

install -m 0644 "$HERE/kinotic-vm-manager.service" /etc/systemd/system/kinotic-vm-manager.service
# The bridge address, where setup-node.sh's dnsmasq answers every workload
BRIDGE_ADDRESS="$(docker network inspect bridge -f '{{range .IPAM.Config}}{{.Gateway}}{{end}}' 2>/dev/null || true)"
[ -f /etc/kinotic/vm-manager.env ] || cat > /etc/kinotic/vm-manager.env <<ENV
# Node configuration read by kinotic-vm-manager.service; see deployment/vm-node/README.md
KINOTIC_VM_PROVIDER=CLOUD_HYPERVISOR
KINOTIC_NODE_ID=
KINOTIC_SERVER_HOST=
KINOTIC_SERVER_PORT=58503
KINOTIC_SERVER_USE_SSL=true
KINOTIC_WORKLOAD_DATA_DIR=/var/lib/kinotic/workloads
KINOTIC_WORKLOAD_DNS=${BRIDGE_ADDRESS:-172.17.0.1}
KINOTIC_LOKI_URL=
KINOTIC_TEMPO_URL=
KINOTIC_MIMIR_URL=
ENV
systemctl daemon-reload
systemctl enable kinotic-vm-manager >/dev/null 2>&1
if [ -f /etc/kinotic/vm-manager.secrets.env ]; then
    systemctl restart kinotic-vm-manager
    echo "  kinotic-vm-manager restarted"
else
    echo "  kinotic-vm-manager enabled; it starts once /etc/kinotic/vm-manager.secrets.env holds KINOTIC_CLIENT_ID and KINOTIC_CLIENT_SECRET"
fi
