#!/usr/bin/env bash
set -euo pipefail

error() {
  echo ""
  echo "ERROR: $1" >&2
  echo "Build failed — see above for details."
  exit 1
}

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${BASE_DIR}/build"
CACHE_DIR="${BASE_DIR}/cache"
KERNEL_VERSION="v6.6"

mkdir -p "${BUILD_DIR}" "${CACHE_DIR}"
cd "${BASE_DIR}"

echo "Building Firecracker Alpine VM..."

# ── 1. KERNEL — smart, resumable ─────────────────────────────
if [[ -f "${BUILD_DIR}/vmlinux" ]]; then
  echo "Using existing kernel from build directory"
else
  echo "Building kernel ${KERNEL_VERSION} (first time: ~8–12 min)..."

  if [[ ! -d "${CACHE_DIR}/linux" ]]; then
    git clone --depth 1 -b "${KERNEL_VERSION}" https://github.com/torvalds/linux.git "${CACHE_DIR}/linux" || \
      error "git clone failed"
  fi

  cp microvm-kernel-ci-x86_64-6.1.config "${CACHE_DIR}/linux/.config"
  cd "${CACHE_DIR}/linux"
  make olddefconfig || error "make olddefconfig failed"
  make -j$(nproc) vmlinux || error "make vmlinux failed"
  mv vmlinux "${BUILD_DIR}/vmlinux"
fi

cd "${BASE_DIR}"