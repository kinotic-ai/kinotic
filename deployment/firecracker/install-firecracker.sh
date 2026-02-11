#!/usr/bin/env bash
# =============================================================================
# Firecracker Installer for Ubuntu/Debian (works in nested VMs)
# Tested on Ubuntu 20.04 / 22.04 / 24.04 inside Hyper-V, VirtualBox, VMware, WSL2
# Latest version: 2025-11-09
# =============================================================================

set -euo pipefail

# Colors for pretty output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log()   { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }

# Check we're on a supported distro
if ! command -v apt-get >/dev/null 2>&1; then
    error "This script only supports Debian/Ubuntu-based systems."
    exit 1
fi

log "Updating package index..."
sudo DEBIAN_FRONTEND=noninteractive apt-get update -qq

log "Installing required dependencies..."
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
    curl \
    wget \
    jq \
    screen \
    tmux \
    cpu-checker \
    qemu-kvm \
    libvirt-daemon-system \
    libvirt-clients \
    bridge-utils \
    virtinst

log "Checking KVM/nested virtualization support..."
if [ -c /dev/kvm ] && kvm-ok >/dev/null 2>&1; then
    log "KVM is available and working!"
else
    warn "KVM not working. In a nested VM?"
    warn "   → Make sure nested virtualization is enabled on your Windows host."
    warn "     Hyper-V:   Set-VMProcessor -VMName <YourVM> -ExposeVirtualizationExtensions $true"
    warn "     VirtualBox: VBoxManage modifyvm <YourVM> --nested-hw-virt on"
    warn "     VMware:     Add vhv.enable = \"TRUE\" to .vmx"
    warn "     WSL2:       Add nestedVirtualization=true to .wslconfig"
fi

# Add current user to relevant groups (idempotent)
for grp in kvm libvirt; do
    if ! id -Gn | grep -qw "$grp"; then
        log "Adding $USER to $grp group..."
        sudo adduser "$USER" "$grp"
    fi
done

# Determine architecture
ARCH=$(uname -m)
case $ARCH in
    x86_64)  FC_ARCH="x86_64" ;;
    aarch64) FC_ARCH="aarch64" ;;
    *) error "Unsupported architecture: $ARCH"; exit 1 ;;
esac

log "Detected architecture: $FC_ARCH"

# Get latest release tag from GitHub
LATEST_TAG=$(curl -s https://api.github.com/repos/firecracker-microvm/firecracker/releases/latest | jq -r '.tag_name')
if [[ -z "$LATEST_TAG" || "$LATEST_TAG" == "null" ]]; then
    error "Could not fetch latest release. Using fallback v1.8.0"
    LATEST_TAG="v1.8.0"
fi

log "Latest Firecracker release: $LATEST_TAG"

FC_BINARY="firecracker-${LATEST_TAG}-${FC_ARCH}"
FC_TGZ="${FC_BINARY}.tgz"
DOWNLOAD_URL="https://github.com/firecracker-microvm/firecracker/releases/download/${LATEST_TAG}/${FC_TGZ}"

TARGET_BIN="/usr/local/bin/firecracker"
TARGET_JAILER="/usr/local/bin/jailer"

# Download & install binary
if [[ ! -f "$TARGET_BIN" ]] || [[ "$($TARGET_BIN --version 2>/dev/null || echo old)" != *"$LATEST_TAG"* ]]; then
    log "Downloading $DOWNLOAD_URL ..."
    curl -L -o "/tmp/$FC_TGZ" "$DOWNLOAD_URL"

    log "Extracting..."
    tar -xzf "/tmp/$FC_TGZ" -C /tmp

    log "Installing firecracker binary..."
    sudo install -m 0755 "/tmp/release-${LATEST_TAG}-${FC_ARCH}/firecracker-${LATEST_TAG}-${FC_ARCH}" "$TARGET_BIN"

    log "Installing jailer binary..."
    sudo install -m 0755 "/tmp/release-${LATEST_TAG}-${FC_ARCH}/jailer-${LATEST_TAG}-${FC_ARCH}" "$TARGET_JAILER"

    log "Cleaning up..."
    rm -rf "/tmp/$FC_TGZ" "/tmp/release-${LATEST_TAG}-${FC_ARCH}"
else
    log "Firecracker $LATEST_TAG already installed."
fi

# Create convenience symlink if missing
if [[ ! -L /usr/local/bin/fc ]]; then
    sudo ln -s "$TARGET_BIN" /usr/local/bin/fc
    log "Created symlink: fc → firecracker"
fi

# Verify installation
log "Installation complete!"
echo
echo "Firecracker version:"
"$TARGET_BIN" --version
echo
echo "Jailer version:"
"$TARGET_JAILER" --version
echo
log "Quick test: Run a hello-world microVM (no config needed)"
log "   curl -fsSL https://raw.githubusercontent.com/firecracker-microvm/firecracker/main/tools/run-hello.sh | bash"

echo
log "All done! You can now run Firecracker inside this Linux VM."
log "Tip: Re-login or run 'newgrp kvm' for group changes to take effect."