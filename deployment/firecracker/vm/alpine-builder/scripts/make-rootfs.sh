#!/usr/bin/env ash
set -euo pipefail

for d in bin etc lib root sbin usr opt; do
    tar c "/$d" | tar x -C /rootfs
done
for d in dev proc run sys var; do
    install -d "/rootfs/$d"
done

# Create overlay directories
mkdir -p /rootfs/overlay/root /rootfs/overlay/work /rootfs/mnt /rootfs/rom