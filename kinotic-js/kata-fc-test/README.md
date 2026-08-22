# kata-fc-test — Kata Containers on Firecracker

The `../kata-ch-test` probe asked of a different hypervisor, so the two reports can be read
side by side. Same phases, same assertions, one capability missing by construction.

## What Firecracker changes

Firecracker has no filesystem sharing. `configuration-fc.toml` carries no `shared_fs`
setting where `configuration-clh.toml` sets `shared_fs = "virtio-fs"`, and that single
difference drives the rest of the setup:

- A workload's rootfs must arrive as a **block device**, so containerd needs the `devmapper`
  snapshotter over a thin-pool rather than the default `overlayfs`.
- `nerdctl` must be told `--snapshotter devmapper` on every command.
- containerd 2.x only unpacks images for `(platform, snapshotter)` pairs listed in
  `unpack_config`, and generates none — without an explicit entry every pull into devmapper
  fails with `no unpack platforms defined`.
- A `-v` bind mount is still **accepted**, but it is satisfied by copying the directory in at
  boot. It reads like a working mount until something writes; see phase 2.

The thin-pool is backed by sparse files on loop devices, which do not survive a reboot.
Re-run `setup-ubuntu.sh` after one — it reuses an existing pool and is safe to repeat.

## Running it

```bash
sudo apt-get install -y unzip          # bun's installer needs it on a stock image
curl -fsSL https://bun.sh/install | bash
~/.bun/bin/bun install

sudo ./setup-ubuntu.sh
sudo /home/azureuser/.bun/bin/bun run src/capability-test.ts
```

Needs a host with nested virtualization and `/dev/kvm`. Setup ends in `SETUP OK` and prints
every resolved version with the release asset it chose.

## Verifying the hypervisor, not the config

Setup asserts that a Firecracker process is actually running while a container is up, rather
than trusting that the config named one. Two obvious ways to check this are both wrong here:

- `comm` is truncated to 15 characters, so it never equals a longer binary name.
- A `pgrep -f` pattern matches the checking script's own command line.

Both report success on a host running the wrong hypervisor. The check reads `/proc/PID/exe`
and compares basenames — basenames rather than full paths because kata launches Firecracker
through the **jailer**, which chroots it, so the link resolves to `/firecracker` rather than
`/opt/kata/bin/firecracker`.
