#!/usr/bin/env bash
#
# One-time environment setup for Kinotic KinD development.
# Run this before your first `terraform apply`.
#
# Usage:
#   ./setup.sh              # Check prerequisites only
#   ./setup.sh --mkcert     # Generate browser-trusted TLS certs
#   ./setup.sh --hosts      # Add structures.local to /etc/hosts
#   ./setup.sh --all        # Do everything

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CLUSTER_NAME="${KIND_CLUSTER_NAME:-kinotic-cluster}"
HOSTNAME="structures.local"
CERT_DIR="${HOME}/.kinotic/kind"

# ── Colors ────────────────────────────────────────────────

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m'

ok()   { echo -e "  ${GREEN}✓${NC} $1"; }
warn() { echo -e "  ${YELLOW}!${NC} $1"; }
fail() { echo -e "  ${RED}✗${NC} $1"; }
info() { echo -e "  ${BLUE}→${NC} $1"; }

# ── Prerequisites ─────────────────────────────────────────

check_prerequisites() {
    echo "Checking prerequisites..."
    local missing=0

    if command -v docker &>/dev/null; then
        if docker info &>/dev/null; then
            ok "Docker (running)"
        else
            fail "Docker (installed but not running)"
            missing=1
        fi
    else
        fail "Docker — install: https://docs.docker.com/get-docker/"
        missing=1
    fi

    if command -v terraform &>/dev/null; then
        ok "Terraform $(terraform version -json 2>/dev/null | python3 -c 'import sys,json; print(json.load(sys.stdin)["terraform_version"])' 2>/dev/null || echo '')"
    else
        fail "Terraform — install: brew install terraform"
        missing=1
    fi

    if command -v kubectl &>/dev/null; then
        ok "kubectl"
    else
        warn "kubectl (optional, for port-forward and debugging) — install: brew install kubectl"
    fi

    if command -v mkcert &>/dev/null; then
        ok "mkcert (available for browser-trusted certs)"
    else
        warn "mkcert (optional) — install: brew install mkcert nss && mkcert -install"
    fi

    if command -v helm &>/dev/null; then
        ok "helm"
    else
        warn "helm (optional, Terraform manages Helm releases) — install: brew install helm"
    fi

    echo ""
    if [[ $missing -gt 0 ]]; then
        fail "Missing required tools. Install them and re-run."
        exit 1
    else
        ok "All required prerequisites met."
    fi
}

# ── mkcert TLS Certificates ──────────────────────────────

setup_mkcert() {
    echo "Setting up browser-trusted TLS certificates..."

    if ! command -v mkcert &>/dev/null; then
        fail "mkcert is not installed."
        echo ""
        echo "  Install with:"
        echo "    brew install mkcert nss    # macOS"
        echo "    mkcert -install            # one-time CA setup"
        echo ""
        exit 1
    fi

    # Check if the KinD cluster exists
    if ! kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
        fail "Cluster '${CLUSTER_NAME}' does not exist."
        echo ""
        echo "  Run 'terraform apply' first to create the cluster,"
        echo "  then re-run './setup.sh --mkcert' to install certs."
        echo ""
        exit 1
    fi

    local context="kind-${CLUSTER_NAME}"

    # Generate certificates
    local cert_dir
    cert_dir=$(mktemp -d)
    pushd "$cert_dir" > /dev/null

    info "Generating certificates for localhost, ${HOSTNAME}, 127.0.0.1, ::1"
    mkcert -cert-file cert.pem -key-file key.pem localhost "$HOSTNAME" 127.0.0.1 ::1

    # Create or update Kubernetes TLS secret
    info "Creating TLS secret 'kinotic-tls-secret' in cluster"
    kubectl create secret tls kinotic-tls-secret \
        --cert=cert.pem \
        --key=key.pem \
        --context "$context" \
        --namespace default \
        --dry-run=client -o yaml | kubectl apply -f - --context "$context"

    # Export CA for CLI tools
    mkdir -p "$CERT_DIR"
    cp cert.pem "$CERT_DIR/ca.crt"
    ok "CA exported to ${CERT_DIR}/ca.crt"

    popd > /dev/null
    rm -rf "$cert_dir"

    echo ""
    ok "TLS certificates installed. Ingress will use browser-trusted certs."
    echo ""
    info "For CLI tools (structures sync, etc.), add to your shell profile:"
    echo "    export NODE_EXTRA_CA_CERTS=${CERT_DIR}/ca.crt"
    echo ""
}

# ── /etc/hosts ────────────────────────────────────────────

setup_hosts() {
    echo "Checking /etc/hosts for ${HOSTNAME}..."

    if grep -q "$HOSTNAME" /etc/hosts 2>/dev/null; then
        ok "${HOSTNAME} already in /etc/hosts"
    else
        info "Adding ${HOSTNAME} to /etc/hosts (requires sudo)"
        echo "127.0.0.1 ${HOSTNAME}" | sudo tee -a /etc/hosts > /dev/null
        ok "Added: 127.0.0.1 ${HOSTNAME}"
    fi
    echo ""
}

# ── Port-forward helper ──────────────────────────────────

show_port_forward() {
    echo "Port-forward commands (for direct service access):"
    echo ""
    info "kubectl port-forward svc/kinotic-es-es-http 9200:9200"
    info "kubectl port-forward svc/keycloak-db-postgresql 5432:5432"
    info "kubectl port-forward svc/keycloak 8888:8888"
    echo ""
}

# ── Main ──────────────────────────────────────────────────

usage() {
    cat <<EOF
Kinotic KinD Environment Setup

Usage: $(basename "$0") [options]

Options:
  (none)       Check prerequisites only
  --mkcert     Generate browser-trusted TLS certs (requires running cluster)
  --hosts      Add ${HOSTNAME} to /etc/hosts (for OIDC flows)
  --all        Check prerequisites + hosts + mkcert
  --help       Show this help

Workflow:
  1. ./setup.sh                    # check prerequisites
  2. cd terraform && terraform apply   # create cluster
  3. ./setup.sh --mkcert           # optional: browser-trusted certs
  4. ./setup.sh --hosts            # optional: needed for OIDC

EOF
}

do_mkcert=false
do_hosts=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --mkcert)  do_mkcert=true; shift ;;
        --hosts)   do_hosts=true; shift ;;
        --all)     do_mkcert=true; do_hosts=true; shift ;;
        --help|-h) usage; exit 0 ;;
        *)         echo "Unknown option: $1"; usage; exit 1 ;;
    esac
done

echo ""
echo "Kinotic KinD Environment Setup"
echo "=============================="
echo ""

check_prerequisites

if [[ "$do_hosts" == "true" ]]; then
    setup_hosts
fi

if [[ "$do_mkcert" == "true" ]]; then
    setup_mkcert
fi

if [[ "$do_mkcert" == "false" && "$do_hosts" == "false" ]]; then
    echo ""
    echo "Next steps:"
    info "cd terraform && terraform init && terraform apply"
    echo ""
    info "Optional (after cluster is running):"
    echo "    ./setup.sh --mkcert     # browser-trusted TLS certs"
    echo "    ./setup.sh --hosts      # needed for OIDC/Keycloak"
    echo ""
fi
