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
IMAGE_TAG="local/alpine-node-app:latest"

mkdir -p "${BUILD_DIR}" "${CACHE_DIR}"
cd "${BASE_DIR}"

echo "Building Firecracker Alpine VM..."
./build-kernel.sh

#TODO: Add support for ssh keys 
# ── 2. SSH key ───────────────────────────────────────────────────────
# if [[ ! -f "${HOME}/.ssh/alpine-firecracker" ]]; then
#   ssh-keygen -t ed25519 -C "alpine@firecracker" -f "${HOME}/.ssh/alpine-firecracker" -N "" || \
#     error "ssh-keygen failed"
# fi
# cp "${HOME}/.ssh/alpine-firecracker.pub" key.pub || error "cp key.pub failed"

# ── 3. Docker build ──────────────────────────────────────────────────
echo "Building Docker image..."
docker build -t "${IMAGE_TAG}" . || error "docker build failed"

# ── 4. Build RootFs Using Docker ─────────────────────────────────
TEMP_DIR="/tmp/alpine-build-$$"
mkdir -p "${TEMP_DIR}"
trap 'rm -rf "${TEMP_DIR}" 2>/dev/null || true' EXIT

echo "Creating alpine-rootfs.ext4..."

dd if=/dev/zero of="${TEMP_DIR}/alpine-rootfs.ext4" bs=1M count=512 >/dev/null 2>&1 || \
  error "dd failed"
mkfs.ext4 -F "${TEMP_DIR}/alpine-rootfs.ext4" >/dev/null || \
  error "mkfs.ext4 failed"

mkdir -p "${TEMP_DIR}/alpine-rootfs"  
sudo mount "${TEMP_DIR}/alpine-rootfs.ext4" "${TEMP_DIR}/alpine-rootfs" || \
  error "Failed to mount alpine-rootfs.ext4"

echo "Starting container"
docker run -i --rm -v "${TEMP_DIR}/alpine-rootfs:/rootfs" "${IMAGE_TAG}" || \
  error "docker run failed"

echo "Creating SquashFS"
rm "${BUILD_DIR}/alpine-rootfs.img" || true
sudo mksquashfs "${TEMP_DIR}/alpine-rootfs" "${BUILD_DIR}/alpine-rootfs.img" -noappend || \
  error "mksquashfs failed"

sudo umount "${TEMP_DIR}/alpine-rootfs"
rmdir "${TEMP_DIR}/alpine-rootfs"

rm "${BUILD_DIR}/alpine-rootfs.ext4" || true
mv "${TEMP_DIR}/alpine-rootfs.ext4" "${BUILD_DIR}/alpine-rootfs.ext4"

if [[ ! -f "${BUILD_DIR}/alpine-overlay.ext4" ]]; then
  echo "Create an EXT4 FS for an Overlay"
  dd if=/dev/zero of="${BUILD_DIR}/alpine-overlay.ext4" conv=sparse bs=1M count=1024 >/dev/null 2>&1 || \
    error "dd failed"
  mkfs.ext4 -F "${BUILD_DIR}/alpine-overlay.ext4" >/dev/null || \
    error "mkfs.ext4 failed"
fi

if [[ ! -f "${BUILD_DIR}/alpine-data.ext4" ]]; then
  echo "Create an EXT4 FS for data"
  dd if=/dev/zero of="${BUILD_DIR}/alpine-data.ext4" conv=sparse bs=1M count=1024 >/dev/null 2>&1 || \
    error "dd failed"
  mkfs.ext4 -F "${BUILD_DIR}/alpine-data.ext4" >/dev/null || \
    error "mkfs.ext4 failed"
fi

echo "Finished creating filesystems"

# ── Done ─────────────────────────────────────────────────────────────
echo ""
echo "SUCCESS! VM ready!"
echo "Generated files in: ${BUILD_DIR}"
ls -lh "${BUILD_DIR}"/{vmlinux,alpine-rootfs.ext4}
echo ""
echo "To launch:"
echo "  sudo ./start.sh"
echo ""
echo "SSH: ssh -i ~/.ssh/alpine-firecracker alpine@172.17.0.42"
echo "App: curl 172.17.0.42:3000"