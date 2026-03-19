# Firecracker Alpine Builder

This directory contains tooling to build a Firecracker-ready Linux kernel and Alpine root filesystem for microVMs. The main entrypoint is `build.sh`, which assembles everything under `build/`.

## Prerequisites

- Docker (builds the root filesystem)
- `git`, `make`, `gcc`, `bc`, `flex`, `bison`, and standard build tools for kernel compilation
- `sudo` access (mounts/unmounts loopback devices)
- `mksquashfs` (SquashFS tools package)

## Usage

Run the build script from this directory:

```bash
./build.sh
```

### Build Steps

1. **Kernel**: Builds (or reuses) a custom Linux kernel via `build-kernel.sh`, caching in `cache/` and outputting `build/vmlinux`.
2. **Docker Image**: Builds the Alpine base image from `Dockerfile` with OpenRC, networking, and the overlay init script.
3. **Root Filesystem**:
   - Creates a 512 MB ext4 image
   - Mounts it and runs the Docker container to populate the filesystem
   - Converts to **SquashFS** (`build/alpine-rootfs.img`) for a compressed, read-only root
   - Keeps the ext4 version (`build/alpine-rootfs.ext4`) as well
4. **Overlay Disk**: Creates `build/alpine-overlay.ext4` (1 GB) if it doesn't exist—used for writable overlay filesystem
5. **Data Disk**: Creates `build/alpine-data.ext4` (1 GB) if it doesn't exist—additional persistent storage

### Output Files

After a successful build, `build/` contains:

- `vmlinux` - Linux kernel
- `alpine-rootfs.img` - SquashFS root filesystem (read-only)
- `alpine-rootfs.ext4` - ext4 root filesystem (512 MB)
- `alpine-overlay.ext4` - Overlay filesystem (1 GB, persistent across reboots)
- `alpine-data.ext4` - Data disk (1 GB, persistent across reboots)

## Configuration

See `config-overlay-example.json` for a complete Firecracker configuration example.

### Filesystem Architecture

The VM uses an overlay filesystem with three disks:

1. **rootfs** (`/dev/vda`) - Read-only SquashFS base system
2. **overlayfs** (`/dev/vdb`) - Writable ext4 layer for system changes
3. **datafs** (`/dev/vdc`) - Writable ext4 data disk mounted at `/data`

The overlay is set up by `/sbin/overlay-init.sh` during early boot, merging the read-only root with the writable overlay. The data disk is mounted by OpenRC's `mount-data` init script after boot.

### Kernel Boot Args

In addition to standard Firecracker kernel args, the Alpine image expects:

- `init=/sbin/overlay-init.sh` - Custom init to set up overlayfs
- `overlay_root=vdb` - Block device for the overlay filesystem (default: `ram` for tmpfs)
- `data_root=vdc` - Block device for the data disk
- `data_mount=/data` - Mount point for the data disk (default: `/data`)

Example boot_args from `config-overlay-example.json`:

```
console=ttyS0 reboot=k panic=1 pci=off overlay_root=vdb data_root=vdc data_mount=/data init=/sbin/overlay-init.sh ip=172.16.0.2::172.16.0.1:255.255.255.252::eth0:off loglevel=7 earlyprintk=serial
```

    
```

This expects a `config.json` in the parent directory. You can copy `config-overlay-example.json` as a starting point.

### Accessing the VM

- **Console**: Firecracker attaches to the VM console by default (ttyS0)
- **Login**: `root` / `root`
- **Network**: IP address is configured via kernel boot args (example uses `172.16.0.2`)

Note: SSH is not currently configured. To add SSH support, uncomment the TODO section in `build.sh` and add the sshd service to the Dockerfile.

## Modifying the Data Disk

You can mount and modify `build/alpine-data.ext4` on the host when the VM is not running:

```bash
mkdir -p /tmp/data-mount
sudo mount build/alpine-data.ext4 /tmp/data-mount
# Make changes...
sudo umount /tmp/data-mount
```

When the VM boots, it will see your changes commit`/data`.

## Notes

- The overlay and data disks are only created if they don't exist, so rebuilding won't erase them
- To reset the overlay: `rm build/alpine-overlay.ext4` and rebuild
- The SquashFS root provides a clean, immutable base; all changes go to the overlay

## Troubleshooting

- **Kernel build fails**: Clear `cache/linux/` and rerun
- **Ext4 mount errors**: Check `sudo` privileges or stale mounts in `/tmp/alpine-build-*`
- **Docker build fails**: Try `docker system prune` or check Docker daemon status
- **mksquashfs not found**: Install SquashFS tools (`squashfs-tools` on Debian/Ubuntu)
