#!/usr/bin/env bash
#
# One-time environment setup for Kinotic KinD development.
# Run this before your first `terraform apply`.
#
# Usage:
#   ./setup.sh              # Check prerequisites only
#   ./setup.sh --hosts      # Add kinotic.local to /etc/hosts
#   ./setup.sh --all        # Check prerequisites + hosts + mkcert CA install

set -euo pipefail

HOSTNAME="kinotic.local"

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
        warn "mkcert (optional) — install: brew install mkcert nss"
        warn "  Then run: mkcert -install"
        warn "  Terraform will use mkcert automatically if available."
        warn "  Without it, cert-manager generates self-signed certs (browser will warn)."
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

# ── mkcert CA install ────────────────────────────────────

setup_mkcert_ca() {
    echo "Setting up mkcert local CA..."

    if ! command -v mkcert &>/dev/null; then
        warn "mkcert is not installed — skipping CA setup."
        echo ""
        echo "  Install with:"
        echo "    brew install mkcert nss    # macOS"
        echo "    mkcert -install            # one-time CA setup"
        echo ""
        info "Terraform will fall back to cert-manager self-signed certs."
        echo ""
        return 0
    fi

    # Install the local CA into system/browser trust stores (idempotent)
    info "Installing mkcert local CA (may require sudo)"
    mkcert -install

    ok "mkcert CA installed. Terraform will generate browser-trusted certs on apply."
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

# ── Main ──────────────────────────────────────────────────

usage() {
    cat <<EOF
Kinotic KinD Environment Setup

Usage: $(basename "$0") [options]

Options:
  (none)       Check prerequisites only
  --hosts      Add ${HOSTNAME} to /etc/hosts (required for OIDC flows)
  --all        Check prerequisites + hosts + mkcert CA install
  --help       Show this help

Workflow:
  1. ./setup.sh --all                    # prerequisites + hosts + mkcert CA
  2. cd terraform && terraform init      # initialize Terraform
  3. terraform apply                     # create cluster + deploy (mkcert certs auto-generated)

Note:
  TLS certificate generation is handled by Terraform (see tls.tf).
  This script only installs the mkcert CA into your system trust store.

EOF
}

do_hosts=false
do_mkcert_ca=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --hosts)   do_hosts=true; shift ;;
        --all)     do_hosts=true; do_mkcert_ca=true; shift ;;
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

if [[ "$do_mkcert_ca" == "true" ]]; then
    setup_mkcert_ca
fi

if [[ "$do_mkcert_ca" == "false" && "$do_hosts" == "false" ]]; then
    echo ""
    echo "Next steps:"
    info "./setup.sh --all                        # hosts + mkcert CA"
    info "cd terraform && terraform init && terraform apply"
    echo ""
    info "For CLI tools after deploy, add to your shell profile:"
    echo "    export NODE_EXTRA_CA_CERTS=\${HOME}/.kinotic/kind/ca.crt"
    echo ""
fi
