#!/usr/bin/env bash
#
# Environment setup for Kinotic KinD development.
# Installs required tools and configures mkcert for browser-trusted TLS.
#
# Usage:
#   ./setup.sh           # Install everything and verify
#   ./setup.sh --check   # Check only, don't install

set -euo pipefail

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

CHECK_ONLY=false
[[ "${1:-}" == "--check" ]] && CHECK_ONLY=true

echo ""
echo "Kinotic KinD Environment Setup"
echo "=============================="
echo ""

# ── Homebrew ──────────────────────────────────────────────

if ! command -v brew &>/dev/null; then
    if [[ "$CHECK_ONLY" == "true" ]]; then
        fail "Homebrew — install: https://brew.sh"
        exit 1
    fi
    info "Installing Homebrew..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
fi
ok "Homebrew"

# ── Tools ─────────────────────────────────────────────────

install_if_missing() {
    local cmd="$1"
    local pkg="${2:-$1}"

    if command -v "$cmd" &>/dev/null; then
        ok "$cmd"
    elif [[ "$CHECK_ONLY" == "true" ]]; then
        fail "$cmd — install: brew install $pkg"
    else
        info "Installing $pkg..."
        brew install "$pkg"
        ok "$cmd"
    fi
}

install_cask_if_missing() {
    local cmd="$1"
    local pkg="$2"

    if command -v "$cmd" &>/dev/null; then
        ok "$cmd"
    elif [[ "$CHECK_ONLY" == "true" ]]; then
        fail "$cmd — install: brew install --cask $pkg"
    else
        info "Installing $pkg..."
        brew install --cask "$pkg"
        ok "$cmd"
    fi
}

echo "Checking tools..."

# Docker — needs special handling (daemon vs Desktop)
if command -v docker &>/dev/null && docker info &>/dev/null; then
    ok "Docker (running)"
elif command -v docker &>/dev/null; then
    warn "Docker is installed but not running"
    info "Start Docker Desktop or the Docker daemon, then re-run this script"
    exit 1
elif [[ "$CHECK_ONLY" == "false" ]]; then
    echo ""
    echo "  Docker is required but not installed. Choose an option:"
    echo ""
    echo "    1) Install Docker Engine (daemon only, via Homebrew)"
    echo "    2) I'll install Docker Desktop myself (https://docker.com/get-docker)"
    echo ""
    read -rp "  Choice [1/2]: " docker_choice
    echo ""
    if [[ "$docker_choice" == "1" ]]; then
        info "Installing Docker Engine via Homebrew..."
        brew install docker
        brew install colima  # lightweight VM to run Docker daemon on macOS
        info "Starting Docker via Colima..."
        colima start
        ok "Docker Engine (via Colima)"
    else
        info "Install Docker Desktop and re-run this script when it's running."
        exit 0
    fi
else
    fail "Docker — install Docker Desktop or run: brew install docker colima"
fi

install_if_missing terraform terraform
install_if_missing kubectl kubectl
install_if_missing helm helm
install_if_missing kind kind
install_if_missing mkcert mkcert
install_if_missing jq jq

# nss is needed for mkcert to work with Firefox
if [[ "$CHECK_ONLY" == "false" ]] && ! brew list nss &>/dev/null 2>&1; then
    info "Installing nss (mkcert Firefox support)..."
    brew install nss
fi

echo ""

# ── mkcert CA ─────────────────────────────────────────────

if command -v mkcert &>/dev/null; then
    if [[ "$CHECK_ONLY" == "false" ]]; then
        info "Installing mkcert local CA (may require sudo)"
        mkcert -install
        ok "mkcert CA installed — Terraform will generate browser-trusted certs"
    else
        ok "mkcert available"
    fi
fi

echo ""

# ── Summary ───────────────────────────────────────────────

if [[ "$CHECK_ONLY" == "true" ]]; then
    echo "Run without --check to install missing tools."
else
    ok "Setup complete!"
    echo ""
    echo "Next steps:"
    info "cd terraform"
    info "terraform init"
    info "terraform apply"
    echo ""
    info "For CLI tools (curl, Node.js) add to your shell profile:"
    echo "    export NODE_EXTRA_CA_CERTS=\${HOME}/.kinotic/kind/ca.crt"
fi
echo ""
