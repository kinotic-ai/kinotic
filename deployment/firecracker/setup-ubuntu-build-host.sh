#!/usr/bin/env bash
set -euo pipefail

echo "Setting up Firecracker BUILD host (Ubuntu / WSL2)..."
echo "Installing only build-time dependencies..."

sudo apt update && sudo apt upgrade -y

sudo apt install -y \
    build-essential \
    flex \
    bison \
    libelf-dev \
    libssl-dev \
    bc \
    dwarves \
    pahole \
    qemu-utils \
    e2fsprogs \
    dos2unix \
    docker.io \
    docker-buildx \
    git \
    curl

echo ""
echo "BUILD HOST READY!"
echo "You can now run any build.sh script on this machine."