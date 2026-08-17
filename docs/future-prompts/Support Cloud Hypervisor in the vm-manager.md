# Add a Cloud Hypervisor provider to the vm-manager

## What we are doing and why

The vm-manager currently runs customer workloads on **boxlite** (a libkrun wrapper) via
`BoxliteProvider`. This layer is a **production multi-tenant boundary**: one customer's
developer's half-finished code runs on the same node as another's, and these environments
hold live credentials to our api-gateway.

We probed boxlite hard on real hardware and it cannot support the design we need. We then
probed **Kata Containers on Cloud Hypervisor** on the same hardware, and it can. This is the
implementation of that decision: a second `IVmProvider` for `VmProviderType.CLOUD_HYPERVISOR`,
running alongside the existing boxlite provider rather than replacing it.

Everything asserted in this document was measured on an Azure `Standard_D4s_v3` with nested
virtualization. The probe harnesses and full write-ups are in the repo — read them before
starting:

**`kinotic-js/kata-ch-test/`** — the Cloud Hypervisor harness. The most useful thing in the
repo for this work:

- `src/capability-test.ts` establishes **what this stack can and cannot do**, phase by phase,
  and each phase names the property it is asserting. It drives the stack with `nerdctl`, which
  the provider will **not** do — the provider targets CRI (see below) — so read it for the
  capability answers and the assertion style, not as a model for the provider's transport.
- `setup-ubuntu.sh` is a working node provisioning script: resolves release assets from the
  GitHub API, installs the config override, and asserts the running VMM.
- `RESULTS.md` has the verbatim probe output behind every claim below.

**`kinotic-js/kata-fc-test/`** — the same probe against Firecracker. `RESULTS.md` §3 is the
side-by-side comparison, and its `setup-ubuntu.sh` shows the devmapper thin-pool,
`unpack_config`, and containerd config edits that this work needs. Firecracker itself was
rejected — it has no `shared_fs`, so a bind mount is satisfied by a boot-time copy and nothing
propagates in either direction afterwards — but the storage plumbing carries over.

**`kinotic-js/boxlite-test/`** — the boxlite probes that produced the findings this replaces.
`README.md` and the `bug-report-*.md` files record what boxlite could not do.

## What the workload shape must be

1. **App code, read-only.** A host directory mounted read-only into the guest. Verified through
   CRI: content readable, write refused `Read-only file system`, host file never created.
2. **A writable work directory, size-capped.** Live shared with the host in both directions,
   bounded by `VolumeMount.sizeLimitMb`.
3. **The container rootfs** — `/tmp`, `/root`, anywhere in the image. Must be capped, or a
   workload can exhaust the host disk and take down every other tenant on the node.
4. **Logs need no mount, and are configured per workload.** The runtime writes the workload's
   stdout/stderr to a host path the caller chooses, in the standard Kubernetes CRI log format
   that Alloy already understands. The log-file contract used with boxlite — where workloads
   write their own files into a mounted log directory — goes away. Size and retention are set
   per workload through `Workload.logPolicy`, and the vm-manager performs the rotation itself
   (see below — nothing else will).

Boxlite could not express this at all: a workload gets exactly one mount of its own, because
the log mount consumes the only other slot before the VM fails to boot.

## Storage limits: two surfaces, two mechanisms

cgroups do not bound storage. The Kata documentation says so directly — *"Since cgroups are
not able to set limits on storage allocation, if you wish to constrain the amount of storage a
container uses, consider using an existing facility such as quota(1) limits or device mapper
limits."* — and the probes confirmed it: a `--memory 512m` workload is OOM-killed at exactly
512 MiB, while a 3 GiB write to its rootfs succeeds and consumes 3 GiB of host disk.

So each surface needs its own mechanism, and both are verified working:

| Surface | Mechanism | Driven by | Evidence |
|---|---|---|---|
| RW volume mount (work dir) | XFS project quota | `VolumeMount.sizeLimitMb` | 64 MiB limit → `dd-exit=1`, exactly 67108864 bytes landed |
| Container rootfs | devmapper `base_image_size` | `Workload.diskSizeMb` | 10 GiB cap → `dd-exit=1`, stops at 10438172672 bytes, guest reports 100% full |
| RO volume mount (app code) | read-only bind mount | `VolumeMount.readOnly` | write refused `Read-only file system`, host file never appeared |

**Do not** try to cap the rootfs with a project quota on the overlayfs upperdir. It requires
relocating containerd's snapshotter root onto XFS (which orphans containerd's metadata DB —
`target snapshot ...: already exists`), reaching into containerd's internal snapshot layout to
find each container's upperdir, and applying a project ID before the workload's first write.
devmapper does the same job as a supported configuration option with no race. This was
attempted and abandoned; do not repeat it.

## Model changes

`VolumeMount` gains a size cap so it can be specified server-side. Add to **all four** places —
the TS and Java models are a wire contract, and the migration DDL is strict, so an entity field
missing from its CREATE TABLE fails the first save at runtime:

```ts
// kinotic-js/workspace/packages/os-api/src/api/model/workload/VolumeMount.ts
export interface VolumeMount {
    hostPath: string
    guestPath: string
    readOnly?: boolean
    /** Hard cap in megabytes on what the guest may write here. Unset means uncapped. */
    sizeLimitMb?: number
}
```

```java
// kinotic-orchestrator/src/main/java/org/kinotic/orchestrator/api/model/workload/VolumeMount.java
private Integer sizeLimitMb;
```

```sql
-- kinotic-migration/src/main/resources/migrations/V1__init.sql  (edit in place, see below)
volumeMounts OBJECT (hostPath KEYWORD, guestPath KEYWORD, readOnly BOOLEAN, sizeLimitMb INTEGER),
```

`Workload` gains a log policy, so log size and retention are set server-side per workload.
Mirror the existing `NetworkPolicy` shape — a class on `Workload`, not loose primitives:

```ts
// kinotic-js/workspace/packages/os-api/src/api/model/workload/LogPolicy.ts
export class LogPolicy {
    /** Rotate the workload's log once it reaches this size. */
    public maxSizeMb: number = 10
    /** How many rotated files to keep before the oldest is discarded. */
    public maxFiles: number = 3
}
```

```sql
-- V1__init.sql, alongside the existing network OBJECT column
logPolicy OBJECT (maxSizeMb INTEGER, maxFiles INTEGER),
```

with the matching Java `LogPolicy` and a `logPolicy` field on both `Workload` classes.

`Workload.diskSizeMb` already exists on both models and in the DDL and is currently unused by
the boxlite path in any meaningful way; wire it to devmapper's per-container size for this
provider.

`VmProviderType.CLOUD_HYPERVISOR` already exists in both the TS and Java enums. No new enum
value is needed.

## Findings the implementation depends on

All measured. Each of these was a real surprise at least once, so do not re-derive them.

### The hypervisor is selected by config file, not by runtime name

The kata shim does **not** derive its config from the name it was invoked under. `kata-runtime`
reads `/etc/kata-containers/configuration.toml` first, then the bundle default — and the bundle
ships that default as a symlink to `configuration-qemu.toml`. A node with a `clh`-suffixed shim
symlink and no override **runs QEMU while every version string reports cloud-hypervisor**. Node
provisioning must install:

```bash
ln -sf /opt/kata/share/defaults/kata-containers/configuration-clh.toml \
       /etc/kata-containers/configuration.toml
```

### Verify the running VMM, never the configured one

A differing guest kernel proves a VM booted, not which hypervisor booted it. Identify the
process by its executable via `/proc/PID/exe`. Both obvious alternatives give wrong answers:
`comm` is truncated to 15 characters so it never equals `cloud-hypervisor`, and a `pgrep -f`
pattern matches the checking process's own command line. Compare **basenames**, since kata may
launch a VMM through the jailer, which chroots it.

`setup-ubuntu.sh` in `kata-ch-test/` already implements this correctly — reuse it.

### containerd `unpack_config` is all-or-nothing

containerd 2.x only unpacks images for `(platform, snapshotter)` pairs listed in
`unpack_config`, and `containerd config default` generates none — overlayfs works from a
builtin default only while the list is empty. Adding a devmapper entry **silently disables
overlayfs**, so already-pulled images keep working and the next pull of a new image fails with
`no unpack platforms defined`. This is a delayed-failure trap. List every snapshotter used:

```toml
[[plugins.'io.containerd.transfer.v1.local'.unpack_config]]
  platform = 'linux/amd64'
  snapshotter = 'devmapper'

[[plugins.'io.containerd.transfer.v1.local'.unpack_config]]
  platform = 'linux/amd64'
  snapshotter = 'overlayfs'
```

Also note `containerd config default` already emits an **empty** devmapper table; appending a
second one makes containerd refuse to start (`table ... already exists`). Fill in the existing
one.

### Capability results, Cloud Hypervisor

- **Mounts**: 1, 2, 3 and 4 volumes all boot. Boxlite's ≥3 failure does not apply.
- **readOnly**: enforced. Write refused, and the host file never appeared.
- **Live sharing**: virtio-fs propagates both directions in 0–1 ms.
- **Cross-guest inotify does NOT fire.** Container B never sees container A's write to a shared
  directory (20 s window, `inotifywait` exit 2), though inotify works within a guest and a
  **host** watcher does see guest writes. The redeploy loop must poll, or the host must watch
  and signal the guest.
- **stdout/stderr captured** to a plain JSON-lines file on the host at
  `/var/lib/nerdctl/<hash>/containers/default/<id>/<id>-json.log`, confirmed inotify-tailable.
  Alloy can tail it directly — workloads no longer strictly need the log-file contract.
- **Exit codes visible**: a run-to-completion workload reports `exited 42`.
- **Restart in place** keeps rootfs state; workload state survives the manager process.
- **cgroup limits enforced**: `memory.max = 536870912` and `cpu.max = 200000 100000` for
  `--memory 512m --cpus 2`.
- **`--network none` boots and denies**, including by raw IP, TCP and ICMP — only `lo`, no
  default route.
- **Egress restriction is host firewall work.** Kata has no allowlist. Two rules against the
  workload's CNI address restrict it to one destination:
  ```bash
  iptables -I FORWARD 1 -s <workload-ip> -d <api-gateway-ip> -j ACCEPT
  iptables -I FORWARD 2 -s <workload-ip> -j DROP
  ```
  This fails closed and is enforced where customer code cannot reach it. The address is
  assigned per container, so rules must be installed once it is known and removed on teardown.
- **Cold boot**: ~1750 ms on overlayfs, ~2060 ms on devmapper.

### The guest misreports its own size

The sandbox VM is sized independently of the cgroup limit, so a workload asked for 512 MiB sees
~2500 MiB. Kinotic apps run on Bun, and its APIs split:

| API | Reports | Correct? |
|---|---|---|
| `process.constrainedMemory()` | 512 MiB | yes — reads the cgroup |
| `navigator.hardwareConcurrency` | 2 | yes — reads the cgroup |
| `os.totalmem()` / `os.freemem()` / `process.availableMemory()` | ~2500 MiB | no — the VM |
| `os.cpus().length` | 3 | no — the VM |

`process.availableMemory()` is the trap: it sounds cgroup-aware and is not. Anything sizing a
pool or cache from the wrong ones over-commits ~5x and is **SIGKILLed with no exit handler and
no prior SIGTERM**. The provider must read exit 137 as an OOM kill, not a crash.

### Base images are shared; only writes cost disk

Four VMs from a 307 MB image grew the devmapper pool by 1 MiB total (~0 MiB per VM) — each
container is a thin CoW snapshot. Only what a VM writes consumes pool space, and freed space
returns on **teardown**, not on delete inside the guest. So node capacity is
`base image + Σ(each live VM's peak writes)`, and `base_image_size` caps virtual size per VM
but all VMs draw from one pool — pool capacity must cover concurrent VMs, and needs monitoring.

### Drive containerd through CRI, not the CLI and not containerd's native API

There is no maintained Node/Bun containerd client: `containerd`, `containerd-client`,
`node-containerd`, `@containerd/client` are all unpublished on npm, and
`@containers-js/containerd` / `containerd-js` are single-version 0.0.1 packages from 2021 (134
and 7 downloads/month) against containerd v2.3.3.

That does **not** mean shelling out to `nerdctl`. containerd serves the **CRI gRPC API** on the
same socket, and it is the right target here. It was validated end to end on this stack:

```
crictl --runtime-endpoint unix:///run/containerd/containerd.sock version
  RuntimeName: containerd   RuntimeVersion: v2.3.3   RuntimeApiVersion: v1

runp --runtime kata-clh  ->  sandbox bb21ca7cf5ba...
create + start           ->  container 2c94d87cb2a9...
guest kernel: 6.18.35        cloud-hypervisor procs: 1
memory.max: 536870912        cpu.max: 200000 100000
```

Why CRI rather than containerd's native services (`containers`, `tasks`, `snapshots`,
`content`, `diff`, `transfer`):

- **Small, stable, versioned surface.** `RuntimeApiVersion: v1`, protos published by Kubernetes
  as `k8s.io/cri-api` `runtime.proto`. The native API would mean driving image pull, snapshot
  preparation, container creation and task lifecycle separately, **and constructing the OCI
  runtime spec ourselves** — that spec construction is the bulk of what `nerdctl` does.
- **Runtime handler selection is first-class.** `RunPodSandboxRequest.runtime_handler` selects
  `kata-clh` directly, and the handler binds its snapshotter in containerd config, so the
  snapshotter is never a per-call concern.
- **Mounts, resources and log paths are structured fields**, all verified working (below).
- **One sandbox per workload maps cleanly onto one microVM**, because with Kata the sandbox
  *is* the VM.

Generate the client from the CRI protos with maintained tooling — `@grpc/grpc-js` (1.14.x),
`@grpc/proto-loader` (0.8.x), or `ts-proto` (2.12.x) for typed stubs. Vendor `runtime.proto`
at a pinned CRI version and check the generated code in, rather than generating at build time.

`crictl` (cri-tools v1.36.0) is the debugging tool for this path — install it on nodes for
support work, but the provider must not shell out to it.

### CRI: the pieces the provider needs, all verified

**Mounts.** `ContainerConfig.mounts[]` with `host_path`, `container_path`, `readonly`:

```
RO mount : content readable | write refused "Read-only file system" | host file never created
RW mount : host write visible in guest | guest write visible on host   (live, both directions)
```

**Logs.** `PodSandboxConfig.log_directory` + `ContainerConfig.log_path` — the caller chooses
where logs land, per workload. containerd writes the standard CRI format, which is what every
Kubernetes node produces and what Alloy parses natively:

```
2026-08-17T06:41:57.760457422Z stdout F 6.18.35
2026-08-17T06:41:57.760487222Z stdout F CRI-STDOUT-MARKER
```

Fields are RFC3339Nano timestamp, stream (`stdout`/`stderr`), a full/partial tag, then the
line. This replaces the log mount entirely.

**Nothing rotates these logs.** In Kubernetes the kubelet rotates container logs; containerd's
CRI plugin does not, and there is no rotation setting in its config — only
`max_container_log_line_size = 16384`, which truncates long lines. Measured: a container
emitting ~6 MB produced a single 5,982,469-byte file and zero rotated files. Left alone this
is an unbounded write to the host filesystem, i.e. the same host-exhaustion vector the rootfs
cap closes.

So **the vm-manager owns rotation**, driven by `Workload.logPolicy`. The mechanism is the one
kubelet uses, and it is part of the CRI API being generated anyway: rename (and optionally
compress) the current file, then call **`ReopenContainerLog`** so the runtime reopens at the
original path. Renaming alone is not enough — containerd holds the file descriptor and would
keep writing to the renamed inode. Confirmed present in both `crictl` and `containerd`:

```
ReopenContainerLog   *v1.ReopenContainerLogRequest   *v1.ReopenContainerLogResponse
```

**Resources.** `ContainerConfig.linux.resources` — `memory_limit_in_bytes`, `cpu_quota`,
`cpu_period` land in the guest cgroup exactly as given (verified `536870912` and
`200000 100000`).

### CRI node configuration

Two things must be configured or CRI fails, both hit during validation.

A runtime handler binding the shim and its snapshotter:

```toml
[plugins.'io.containerd.cri.v1.runtime'.containerd.runtimes.kata-clh]
  runtime_type = 'io.containerd.kata-clh.v2'
  snapshotter = 'overlayfs'          # or 'devmapper' when the rootfs cap is wanted
  privileged_without_host_devices = true
```

And the CNI plugin path. CRI defaults to `bin_dirs = ['/opt/cni/bin']`, but the nerdctl-full
bundle installs plugins to `/usr/local/libexec/cni`, so every `RunPodSandbox` fails with
`failed to find plugin "bridge" in path [/opt/cni/bin]`:

```toml
[plugins.'io.containerd.cri.v1.runtime'.cni]
  bin_dirs = ['/usr/local/libexec/cni', '/opt/cni/bin']
```

## What must exist when this is done

Unordered — sequencing is yours to propose.

- **Model and wire contract.** `VolumeMount.sizeLimitMb` and `Workload.logPolicy` in the TS and
  Java models and the migration DDL, with `website/content/**` reconciled. A `sizeLimitMb` on a
  `readOnly` mount is rejected — nothing writes there, so accepting it would be a lie.
- **A CRI client.** Vendored `runtime.proto` at a pinned version, generated stubs checked in,
  wrapped in a thin internal client over the containerd socket. This layer knows nothing about
  `Workload`; it is transport.
- **`CloudHypervisorProvider` implementing `IVmProvider`**, registered in `DefaultVmManager`'s
  provider map, mapping `Workload` onto CRI config: image, entrypoint/cmd, env, `memoryMb` and
  `vcpus` onto `linux.resources`, volume mounts onto `ContainerConfig.mounts`, port mappings,
  and the `kata-clh` runtime handler.
- **`recover()` and `restart()`** with the semantics `BoxliteProvider` already implements —
  read what it does and stay consistent. containerd owns this state, so recovery can query it
  rather than trusting a local file; confirm which the existing provider assumes.
- **A host quota service** that applies and releases XFS project quotas for writable mounts:
  allocate a project id, apply `bhard`, release on destroy. This requires the backing directory
  to be on an XFS filesystem mounted `-o prjquota`, so it carries a node requirement.
- **`listLogTargets()` and the `AlloyManager` wiring** over the CRI log path, plus **log
  rotation** enforcing `Workload.logPolicy` via rename + `ReopenContainerLog`.
- **`NetworkPolicy` translated to host firewall rules** against the workload's CNI address,
  including lifecycle: install once the address is known, remove on teardown, and fail closed.
- **Node provisioning** for a Cloud Hypervisor node, derived from
  `kinotic-js/kata-ch-test/setup-ubuntu.sh`: the hypervisor config override, the CRI runtime
  handler, the CNI `bin_dirs` fix, `unpack_config` entries for every snapshotter used, the XFS
  filesystem backing writable mounts, and the VMM assertion.

## How to sequence the work

**Plan the phases yourself.** Read the code first, then propose a breakdown before writing
anything. The requirements below are the constraints on that plan, not a plan themselves.

- **Roughly 10 changed files per phase.** A phase materially larger than that is too big to
  review usefully; much smaller wastes a review cycle.
- **After each phase, stop and wait for review.** Do not begin the next phase until the
  reviewer approves. Report what changed, why, and anything you found that contradicts this
  document — that last part matters, because several claims here were wrong before they were
  measured.
- **Each phase must build additively on the last.** No phase may rewrite, refactor, or
  restructure code an earlier phase produced. If a later phase would force that, the earlier
  phase drew its boundary in the wrong place — say so and re-plan rather than churning the
  code. Getting this right means sequencing so that each phase's output is something the next
  phase consumes unchanged, which usually means the shared contracts land before their
  consumers.
- **Every phase should leave the tree working**: it compiles, existing tests pass, and nothing
  half-wired is left behind a flag.

Start by proposing the phase list with a one-line scope for each and the file count you
expect, and wait for approval on the plan before implementing the first one.

## Conventions

`CLAUDE.md` governs and is not optional. In particular for this work:

- **Package structure**: `api/` for anything another module or node uses, `internal/api/` for
  implementations mirroring it, `internal/model/` for module-private DTOs. Every top-level type
  gets its own file — no types nested in interfaces.
- **Enums, not magic strings**, for any constrained value.
- **Migrations stay in sync with persisted entities**, and while `kinoticVersion` is a
  `-SNAPSHOT` you edit `V1__init.sql` in place rather than adding a versioned file.
- **Docs stay in sync in the same change** — `website/content/**` must reflect the current
  shape of the system.
- **Tests serve the code**: never widen visibility or add a seam whose only consumer is a test.
  Prefer one behavioral test against real infrastructure, gated to skip when unavailable, over
  unit tests that each cost a structural concession.
- **No speculative generality.** Build for the two providers that exist, not for a third.

## Two things this does not solve

Both are known, both stay on the design list, and neither should be worked around in this
change:

1. **The redeploy loop must poll.** Cross-guest inotify does not fire on any combination
   tested. Data propagates in 0–1 ms once written, so the poll interval sets the reload
   latency. The alternative is a host-side watcher signalling the guest.
2. **Uncatchable OOM kills.** A workload exceeding its memory cgroup is SIGKILLed with no
   opportunity to flush logs or checkpoint. The provider can only observe exit 137 after the
   fact.
