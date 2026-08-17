# Kata Containers on Firecracker — capability evaluation

The `../kata-ch-test` evaluation repeated against Firecracker, on the same host, so the two
can be compared directly. Everything except the shared-host-volume capability was run.

## 1. Environment

| | |
|---|---|
| Cloud | Azure, resource group `rg-kata-probe`, VM `vm-kata-probe` |
| Region / size | `eastus` zone 1 / `Standard_D4s_v3` (4 vCPU, nested virt) |
| Image | Ubuntu 22.04 (`Ubuntu2204`) |
| Host kernel | `6.8.0-1064-azure` |
| `/dev/kvm` | present |

| Component | Version | Asset |
|---|---|---|
| kata-containers | `4.0.0` | `kata-static-4.0.0-amd64.tar.zst` |
| nerdctl | `v2.3.5` | `nerdctl-full-2.3.5-linux-amd64.tar.gz` |
| firecracker | `v1.12.1` | (bundled in kata-static) |
| containerd | `v2.3.3` | (bundled in nerdctl-full) |
| Guest kernel | `6.18.35` | (bundled in kata-static) |
| Snapshotter | `devmapper` | thin-pool `kata-fc-pool`, 40 G data / 4 G metadata |

Runtime `io.containerd.kata-fc.v2`. No Docker daemon; standard OCI images from Docker Hub.

## 2. Verbatim stdout

### 2.1 `sudo ./setup-ubuntu.sh`

```
=== Base packages

=== Resolving latest releases
kata-containers : 4.0.0  (kata-static-4.0.0-amd64.tar.zst)
nerdctl         : v2.3.5  (nerdctl-full-2.3.5-linux-amd64.tar.gz)

=== Installing Kata Containers (static bundle: shim, guest kernel, guest image, firecracker)

=== Installing containerd, CNI and nerdctl (nerdctl-full bundle)

=== Creating the devmapper thin-pool (40G data, 4G metadata)
thin-pool kata-fc-pool already present, reusing it

=== Configuring containerd for the devmapper snapshotter

=== Starting containerd

=== Verifying the pieces
nerdctl version 2.3.5
kata-runtime  : 4.0.0
   commit   : cf82bb35c80320178bf7570252fe75d6fb263209
   OCI specs: 1.2.1
firecracker path from configuration-fc.toml: /opt/kata/bin/firecracker
Firecracker v1.12.1

=== Pulling the probe images

=== Smoke test: one container on the Kata Firecracker runtime
time="2026-08-17T01:13:06Z" level=warning msg="cannot set cgroup manager to \"systemd\" for runtime \"io.containerd.kata-fc.v2\""
host kernel  : 6.8.0-1064-azure
guest kernel : 6.18.35
hypervisor   : firecracker (1 process(es) verified, qemu: 0, clh: 0)

SETUP OK — kata 4.0.0, nerdctl v2.3.5, guest kernel 6.18.35, snapshotter devmapper
```

Exit code 0. A `forward signal child exited / InitProcessNotFound` error line from the shim
is emitted between the smoke test and the verification container; it is noise from a
short-lived container exiting and does not affect the result.

### 2.2 `sudo bun run src/capability-test.ts`

```
Runtime         : io.containerd.kata-fc.v2
Snapshotter     : devmapper
nerdctl         : nerdctl version 2.3.5
kata-runtime    : kata-runtime  : 4.0.0
firecracker     : Firecracker v1.12.1
Host            : Linux 6.8.0-1064-azure

=== Phase 0: is a workload actually a VM here, booted by Firecracker? ===
  host kernel           : 6.8.0-1064-azure
  guest kernel          : 6.18.35
  firecracker procs     : 1   (qemu: 0, cloud-hypervisor: 0)

=== Phase 1: OCI image semantics ===
  entrypoint override   : exit=0 "from-entrypoint"
  environment           : exit=0 "abc"
  working directory     : exit=0 "/tmp"

=== Phase 2: is a host bind mount a live share, or a copy? ===
  content present at boot: before-start
  host write -> guest    : NOT-VISIBLE
  guest write -> host    : NOT-VISIBLE
  => a boot-time copy, not a shared filesystem
  (configuration-fc.toml has no shared_fs; configuration-clh.toml sets virtio-fs)

=== Phase 3: is the entrypoint's stdout captured? ===
  nerdctl logs          : exit=0 stdout="line-one" stderr="line-two"
  host log file         : /var/lib/nerdctl/1935db59/containers/default/a98180745d8cf752dd1b81705ffdfbcc24c5d7051838a5df34273680bfedf6f6/a98180745d8cf752dd1b81705ffdfbcc24c5d7051838a5df34273680bfedf6f6-json.log
  file type             : regular file size=158

=== Phase 4: lifecycle and IVmProvider.recover ===
  batch status/exit code: exited 42
  restart in place      : exit=0, /root/boots.log has 2 line(s)
  visible to a new proc : keep-mswjltnq Up
  stable id for logs    : bdae0aef2448d7677ceb3b7b

=== Phase 5: are resource limits honoured? ===
  cgroup memory.max     : 536870912 (512 MiB) for --memory 512m
  cgroup cpu.max        : 200000 100000 (2 cpus) for --cpus 2
  but the guest sees    : 2489 MiB / 3 cpus
  guest df /            : /dev/vdb                  9.7G      8.6M      9.2G   0% /
  guest rootfs mount    : /dev/vdb on / type ext4 (rw,relatime,stripe=16)
  host thin-pool        : Name:              kata-fc-pool State:             ACTIVE Read Ahead:        256

=== Phase 6: network ===
  default egress        : exit=0
  --network none        : booted=YES exit=1
  published port 18080  : HTTP 200
  workload address      : 10.4.0.222
  (a host-side address is what lets a firewall allow only the api-gateway)

=== Phase 7: cold boot latency (the dev edit/redeploy cycle) ===
  run-to-exit, 3 samples: 2542 ms, 2264 ms, 2183 ms

=== REPORT: what the vm-manager needs, and whether this stack provides it ===
  workloads are real VMs                  : YES (firecracker seen: YES)
  OCI entrypoint / env / workdir          : YES / YES / YES
  bind mount content present at boot      : YES
  ...and it is a LIVE shared filesystem   : NO   (clh: YES, via virtio-fs)
  entrypoint stdout/stderr captured       : YES / YES
  stdout reaches a tailable host file     : YES
  run-to-completion exit code             : YES
  restart in place, rootfs intact         : YES
  state survives the manager process      : YES
  memory / vcpu limits honoured           : YES / YES
  egress open by default                  : YES
  no-egress mode boots                    : YES   and denies: YES
  published ports reachable from the host : YES
  workload has a host-side address        : YES
```

Exit code 0.

## 3. Firecracker vs Cloud Hypervisor

| | Cloud Hypervisor | Firecracker |
|---|---|---|
| Real VM per workload | YES | YES |
| OCI entrypoint / env / workdir | YES | YES |
| Live shared host directory | YES, up to 4 mounts | **NO** — boot-time copy only |
| readOnly mount enforced | YES | n/a, no live mount to enforce |
| Host watcher sees guest writes | YES, 61 ms | **NO** — nothing propagates |
| stdout/stderr captured to a host file | YES | YES |
| Run-to-completion exit code | YES | YES |
| Restart in place, state survives manager | YES | YES |
| Memory / vCPU limits enforced | YES | YES |
| No-egress mode boots and denies | YES | YES |
| Published ports, host-side address | YES | YES |
| Snapshotter | overlayfs | devmapper thin-pool (required) |
| Guest rootfs | host filesystem, 61.8 G shared | `/dev/vdb` ext4, 9.7 G per workload |
| Cold boot | ~1650–1780 ms | ~2180–2540 ms |

### 3.1 The bind mount is a copy, and that is the deciding difference

`-v` is accepted and the directory's contents are present at boot, so a shallow check reports
a working mount. Nothing propagates afterwards, in either direction:

```
  content present at boot: before-start
  host write -> guest    : NOT-VISIBLE
  guest write -> host    : NOT-VISIBLE
```

This removes two things the vm-manager design depends on. The Alloy log path built on a host
watcher over a mounted directory cannot work, because guest writes never reach the host. And
the edit/redeploy loop — one container pulling new app code into a shared location for a
running microservice VM to pick up — cannot work either, because the running VM's copy is
fixed at boot. On Cloud Hypervisor the data propagated in 0–1 ms; here it does not propagate
at all.

Container stdout is unaffected: it reaches a real file on the host
(`/var/lib/nerdctl/.../<id>-json.log`) through the shim rather than through a shared
filesystem, so log shipping via `nerdctl logs` or by tailing that file still works.

### 3.2 Disk is per-workload here, which Cloud Hypervisor does not give

The Firecracker rootfs is a thin-pool block device rather than a directory on the host
filesystem:

```
  guest df /            : /dev/vdb                  9.7G      8.6M      9.2G   0% /
  guest rootfs mount    : /dev/vdb on / type ext4 (rw,relatime,stripe=16)
```

Under Cloud Hypervisor the same line reads `61.8G` — the host's own filesystem, with no
per-workload cap at all. Firecracker's `base_image_size` gives each workload a bounded disk
without needing the XFS project-quota mechanism, which is a genuine advantage for the
multi-tenant case.

### 3.3 Boot latency

`2542 / 2264 / 2183 ms` against Cloud Hypervisor's `1783 / 1676 / 1716 ms` on the same host —
roughly 30–40% slower, despite Firecracker's reputation for fast boots. The devmapper
snapshotter has to provision a thin device per container, which the overlayfs path does not.

## 4. Deviations and setup friction

Firecracker needed materially more work than Cloud Hypervisor. Each item below is a change
now carried by `setup-ubuntu.sh`.

1. **devmapper thin-pool.** Firecracker cannot use overlayfs. The script creates a 40 G/4 G
   loopback-backed thin-pool via `dmsetup`. Loop devices do not survive a reboot.
2. **`containerd config default` already declares an empty devmapper table.** Appending a
   second one makes containerd refuse to start:
   `failed to unmarshal TOML: table io.containerd.snapshotter.v1.devmapper already exists`.
   The script fills in the existing table instead.
3. **`unpack_config` is required.** containerd 2.x only unpacks images for
   `(platform, snapshotter)` pairs listed there and generates none, so every pull into
   devmapper failed with `no unpack platforms defined: invalid argument` — via `ctr` as well
   as `nerdctl`, which is what showed it was containerd and not the CLI.
4. **The bundle ships `firecracker` mode 0744**, so it is not executable by a non-root
   caller; the script chmods it and `jailer` to 0755.
5. **`vhost_vsock` must be loaded** for the shim to reach the guest agent.
6. **The VMM check has to compare basenames.** Kata launches Firecracker through the
   **jailer**, which chroots it, so `/proc/PID/exe` resolves to `/firecracker` rather than
   `/opt/kata/bin/firecracker`. A full-path comparison reports zero Firecracker processes on
   a correctly running host — this fired during setup and was a true negative worth keeping
   the assertion for.

One bug in this probe was caught the same way: phase 2 originally recorded a bind mount as
working because the boot-time copy made the content visible. Testing propagation instead of
visibility turned that YES into the NO in §3.1.
