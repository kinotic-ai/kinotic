# Kata Containers on Cloud Hypervisor — capability evaluation

Evaluation of Kata Containers + Cloud Hypervisor as a replacement microVM runtime for
boxlite, run on a dedicated Azure VM with nested virtualization.

## 1. Environment

| | |
|---|---|
| Cloud | Azure, resource group `rg-kata-probe`, VM `vm-kata-probe` |
| Region / size | `eastus` zone 1 / `Standard_D4s_v3` (4 vCPU, nested virt) |
| Image | Ubuntu 22.04 (`Ubuntu2204`) |
| Host kernel | `6.8.0-1064-azure` |
| Root filesystem | `/dev/root` ext4, 62 G |
| `/dev/kvm` | present, `crw-rw---- root:kvm` |
| Repo commit | `aa7afaa1f` (`origin/develop`) |

Versions resolved by `setup-ubuntu.sh`, with the release asset each one selected:

| Component | Version | Asset |
|---|---|---|
| kata-containers | `4.0.0` | `kata-static-4.0.0-amd64.tar.zst` |
| nerdctl | `v2.3.5` | `nerdctl-full-2.3.5-linux-amd64.tar.gz` |
| cloud-hypervisor | `v51.1` | (bundled in kata-static) |
| containerd | `v2.3.3` | (bundled in nerdctl-full) |
| Guest kernel | `6.18.35` | (bundled in kata-static) |

Host kernel `6.8.0-1064-azure` vs guest kernel `6.18.35` — workloads are genuinely
separate VMs, not host-kernel containers.

## 2. Verbatim stdout

### 2.1 `sudo ./setup-ubuntu.sh`

```
=== Base packages

=== Resolving latest releases
kata-containers : 4.0.0  (kata-static-4.0.0-amd64.tar.zst)
nerdctl         : v2.3.5  (nerdctl-full-2.3.5-linux-amd64.tar.gz)

=== Installing Kata Containers (static bundle: shim, guest kernel, guest image, cloud-hypervisor)

=== Installing containerd, CNI and nerdctl (nerdctl-full bundle)

=== Starting containerd

=== Verifying the pieces
nerdctl version 2.3.5
kata-runtime  : 4.0.0
   commit   : cf82bb35c80320178bf7570252fe75d6fb263209
   OCI specs: 1.2.1
cloud-hypervisor path from configuration-clh.toml: /opt/kata/bin/cloud-hypervisor
cloud-hypervisor v51.1

=== Pulling the probe images

=== Smoke test: one container on the Kata Cloud Hypervisor runtime
time="2026-08-16T05:52:29Z" level=warning msg="cannot set cgroup manager to \"systemd\" for runtime \"io.containerd.kata-clh.v2\""
host kernel  : 6.8.0-1064-azure
guest kernel : 6.18.35

SETUP OK — kata 4.0.0, nerdctl v2.3.5, guest kernel 6.18.35
```

Exit code 0.

### 2.2 `sudo bun run src/capability-test.ts`

This is the run **after** the Cloud Hypervisor fix in section 3.2. An earlier run of the
same probe, before that fix, executed under QEMU and is not reported as the result.

```
Runtime         : io.containerd.kata-clh.v2
nerdctl         : nerdctl version 2.3.5
kata-runtime    : kata-runtime  : 4.0.0
Host            : Linux 6.8.0-1064-azure

=== Phase 0: is a workload actually a VM here? ===
  host kernel           : 6.8.0-1064-azure
  guest kernel          : 6.18.35
  cloud-hypervisor procs: 0

=== Phase 1: OCI image semantics ===
  entrypoint override   : exit=0 "from-entrypoint"
  environment           : exit=0 "abc"
  working directory     : exit=0 "/tmp"

=== Phase 2: mounts (boxlite cannot boot three) ===
  1 volume(s)           : STARTED  guest sees: /v0 /var
  2 volume(s)           : STARTED  guest sees: /v0 /v1 /var
  3 volume(s)           : STARTED  guest sees: /v0 /v1 /v2 /var
  4 volume(s)           : STARTED  guest sees: /v0 /v1 /v2 /v3 /var
  readOnly enforced     : exit=1

=== Phase 3: does a host watcher see guest writes (the Alloy design)? ===
  inotify saw the write : YES after 61 ms
  host reads the content: "hello"

=== Phase 4: is the entrypoint's stdout captured? (boxlite discards it) ===
  nerdctl logs          : exit=0 stdout="line-one" stderr="line-two"

=== Phase 5: lifecycle and IVmProvider.recover ===
  batch status/exit code: exited 42
  restart in place      : exit=0, /root/boots.log has 2 line(s)
  visible to a new proc : keep-msve70jb Up
  stable id for logs    : 7cf8ce06f9083dab9e2d3523

=== Phase 6: are resource limits honoured? ===
  guest RAM for 512m    : 2372 MiB
  guest nproc for 2 cpus: 3
  guest df /            : none                     61.8G      9.7G     52.1G  16% /
  host snapshotter dirs : /var/lib/containerd/io.containerd.snapshotter.v1.blockfile /var/lib/containerd/io.containerd.snapshotter.v1.btrfs /var/lib/containerd/io.containerd.snapshotter.v1.erofs /var/lib/containerd/io.containerd.snapshotter.v1.native /var/lib/containerd/io.containerd.snapshotter.v1.overlayfs
  host fs for that dir  : /dev/root      ext4   62G  9.7G   53G  16% /

=== Phase 7: network ===
  default egress        : exit=0
  --network none        : booted=YES exit=1
  published port 18080  : HTTP 200
  workload address      : 10.4.0.47
  (a host-side address is what lets a firewall allow only the api-gateway)

=== Phase 8: cold boot latency (the dev edit/redeploy cycle) ===
  run-to-exit, 3 samples: 1761 ms, 1631 ms, 1633 ms

=== REPORT: what the vm-manager needs, and whether this stack provides it ===
  workloads are real VMs                  : YES (cloud-hypervisor seen: NO)
  OCI entrypoint / env / workdir          : YES / YES / YES
  three volume mounts (boxlite: NO)       : YES   four: YES
  readOnly mount enforced                 : YES
  host inotify sees guest writes          : YES   host reads content: YES
  entrypoint stdout/stderr captured       : YES / YES   (boxlite: NO)
  run-to-completion exit code (boxlite: NO): YES
  restart in place, rootfs intact         : YES
  state survives the manager process      : YES
  memory / vcpu limits honoured           : NO / NO
  egress open by default                  : YES
  no-egress mode boots (boxlite: NO)      : YES   and denies: YES
  published ports reachable from the host : YES
  workload has a host-side address        : YES
```

Exit code 0.

## 3. Deviations

Every command run beyond the scripted steps, and why.

### 3.1 `unzip` for the bun installer

```bash
sudo apt-get install -y unzip
```

`bun`'s installer aborts with `error: unzip is required to install bun` on a stock Ubuntu
22.04 image. Already noted in the scripted steps.

### 3.2 Pointing Kata at the Cloud Hypervisor config (**the significant one**)

```bash
sudo mkdir -p /etc/kata-containers
sudo ln -sf /opt/kata/share/defaults/kata-containers/configuration-clh.toml \
            /etc/kata-containers/configuration.toml
sudo systemctl restart containerd
```

Without this, **workloads run under QEMU, not Cloud Hypervisor**, even though the runtime
is named `io.containerd.kata-clh.v2` and setup prints a cloud-hypervisor version.

`setup-ubuntu.sh:39-42` assumes the shim picks its config from the name it is invoked
under:

```bash
# The kata shim selects its config from the name it was invoked under, so the clh-suffixed
# link is what makes containerd's io.containerd.kata-clh.v2 runtime use Cloud Hypervisor
ln -sf /opt/kata/bin/containerd-shim-kata-v2 /usr/local/bin/containerd-shim-kata-clh-v2
```

Kata 4.0.0 does not do this. Its own binary reports the only two paths it consults:

```
$ kata-runtime --show-default-config-paths
/etc/kata-containers/configuration.toml
/opt/kata/share/defaults/kata-containers/configuration.toml
```

and the bundle ships `configuration.toml` as a symlink to `configuration-qemu.toml`, so
the shim silently resolved QEMU:

```
$ kata-runtime env | grep -A1 '^\[Hypervisor\]'
[Hypervisor]
  Path = "/opt/kata/bin/qemu-system-x86_64"
```

Proof it was actually QEMU running the workload, before the fix:

```
$ ps -eo pid,comm,args | grep -iE 'cloud-hyper|qemu-system'
10521 qemu-system-x86 /opt/kata/bin/qemu-system-x86_64 -name sandbox-f8d987abd663...
```

and after:

```
$ ps -eo pid,comm,args | grep -iE 'cloud-hyper|qemu-system'
11148 cloud-hyperviso /opt/kata/bin/cloud-hypervisor --api-socket /run/vc/vm/ee5f2f02...

$ kata-runtime env | grep -A2 '^\[Hypervisor\]'
[Hypervisor]
  MachineType = "q35"
  Version = "cloud-hypervisor v51.1"
```

Cold boot latency corroborates the switch: ~2100 ms per run under QEMU, ~1650 ms under
Cloud Hypervisor.

### 3.3 `inotify-tools` for investigations A and C

```bash
sudo apt-get install -y inotify-tools          # host
nerdctl exec shB apk add --no-cache inotify-tools   # inside the guest
```

Diagnostic tooling for the investigations; not needed by the probe.

### 3.4 Run from `develop`

Run against `origin/develop` at `aa7afaa1f`, which is the intended base for this work.

### 3.5 Probe measurement bug: `cloud-hypervisor procs: 0`

Not a change — a caveat about the output above. Phase 0 counts with:

```ts
const ps = run("sh", ["-c", "ps -eo comm= | grep -c cloud-hypervisor || true"]);  // capability-test.ts:93
```

The kernel truncates `comm` to 15 characters (`TASK_COMM_LEN` is 16 including the NUL), so
the process appears as `cloud-hyperviso` and the 16-character pattern never matches:

```
$ ps -eo comm= | grep -i cloud
cloud-hyperviso
$ pgrep -c cloud-hypervisor        # comm match
0
$ pgrep -c -f cloud-hypervisor     # full cmdline match
2
```

So `cloud-hypervisor seen: NO` is a false negative on **any** host, correctly configured or
not. This is why phase 0 did not catch the QEMU substitution in 3.2 — its only real guard is
the host-vs-guest kernel comparison, which passes under QEMU too. The probe was left
unedited; `pgrep -f` is the working check.

## 4. Investigations

### A. Where does a container's stdout land on the host?

Container logs are written by nerdctl to a plain per-container file:

```
/var/lib/nerdctl/1935db59/containers/default/<container-id>/<container-id>-json.log
```

```
$ stat -c '%F  size=%s  %A' <path>
regular file  size=474  -rw-------

$ cat <path>
{"log":"stdout-1\n","stream":"stdout","time":"2026-08-16T05:58:27.530909668Z"}
{"log":"stderr-1\n","stream":"stderr","time":"2026-08-16T05:58:27.531224069Z"}
{"log":"stderr-2\n","stream":"stderr","time":"2026-08-16T05:58:29.521935995Z"}
{"log":"stdout-2\n","stream":"stdout","time":"2026-08-16T05:58:29.522214395Z"}
{"log":"stderr-3\n","stream":"stderr","time":"2026-08-16T05:58:31.526826861Z"}
{"log":"stdout-3\n","stream":"stdout","time":"2026-08-16T05:58:31.527082862Z"}
```

A regular file on the host's ext4 root filesystem, Docker-compatible JSON-lines, with
stdout and stderr distinguished by a `stream` field and each line timestamped. Mode `0600`,
root-owned.

It is inotify-tailable:

```
$ inotifywait -e modify --format '%T %e %w' --timefmt '%H:%M:%S' <path>
Setting up watches.
Watches established.
05:59:20 MODIFY /var/lib/nerdctl/.../9810abf0...-json.log

$ tail -2 <path>
{"log":"LATE-LINE\n","stream":"stdout","time":"2026-08-16T05:59:20.546102056Z"}
```

**Alloy can tail container logs directly.** The log-file contract used with boxlite —
where workloads write their own files into a mounted log directory — is no longer required.

### B. Can egress be restricted to a single destination?

Kata has no allowlist of its own, so this is host firewall work against the address from
phase 7. Rules used, against a workload at `10.4.0.55`:

```bash
iptables -I FORWARD 1 -s 10.4.0.55 -d 172.66.147.243 -j ACCEPT   # the one allowed destination
iptables -I FORWARD 2 -s 10.4.0.55 -j DROP                       # everything else from this workload
```

Resulting chain:

```
Chain FORWARD (policy ACCEPT)
num  target     prot opt source               destination
1    ACCEPT     all  --  10.4.0.55            172.66.147.243
2    DROP       all  --  10.4.0.55            0.0.0.0/0
3    CNI-ISOLATION-STAGE-1  all  --  0.0.0.0/0            0.0.0.0/0
4    CNI-FORWARD  all  --  0.0.0.0/0            0.0.0.0/0
```

Verified from inside the guest, using raw IPs so the result does not depend on DNS:

```
=== baseline: no rules ===
  allowed   172.66.147.243     exit=1 wget: server returned error: HTTP/1.1 403 Forbidden
  unlisted  104.16.133.229     exit=1 wget: server returned error: HTTP/1.1 403 Forbidden
  raw ip    1.1.1.1            exit=0

=== after rules ===
  allowed   172.66.147.243     exit=1 wget: server returned error: HTTP/1.1 403 Forbidden
  unlisted  104.16.133.229     exit=1 wget: download timed out
  raw ip    1.1.1.1            exit=1 wget: download timed out
```

The `403 Forbidden` on the allowed destination is the origin server rejecting a raw-IP
`Host` header — it is an HTTP response, so the TCP connection completed and the destination
is reachable. The unlisted host and the raw IP change from reachable to `download timed
out`, i.e. dropped at the network layer.

**Egress can be restricted to exactly one destination.** Unlike boxlite's `allowNet`, this
is enforced on the host where customer code cannot reach it, and it fails closed: the DROP
rule is unconditional and an empty allowlist means only the DROP rule exists.

One caveat for implementation: rules are keyed on the workload's CNI address, which is
assigned per container (`10.4.0.22`, `10.4.0.47`, `10.4.0.55` across runs). The vm-manager
must install rules after the address is known and remove them on teardown, or pin addresses
via CNI configuration.

### C. Does the shared-directory reload flow work?

Two containers mounting the same host directory. **Data propagates; cross-guest inotify
events do not.**

Container B watching the shared directory does not see a write made by container A, over a
full 20-second window (`inotifywait` exits 2 on timeout with no event):

```
=== 1. inotify inside B: full 20s window ===
    Setting up watches.
    Watches established.
    EXIT=2
```

Two controls confirm that is specific to the cross-guest path, not a broken watcher:

```
=== 1b. control: write made INSIDE B ===
    Setting up watches.
    Watches established.
    /shared/ CREATE local.js
    EXIT=0

=== 1c. control: inotify on the HOST, A's write ===
    Watches established.
    host saw: CREATE hostwatch.js
```

inotify works inside the guest, and a host watcher does see guest writes — only
guest-to-guest event propagation is missing. The bytes themselves arrive:

```
=== 2. does B see the bytes? ===
  B reads /shared/app.js : v1-payload
  host reads it          : v1-payload
```

Propagation latency, measured from host-side nanosecond mtimes (busybox `date` in the guest
ignores `%N`, so in-guest stamps have only 1-second resolution and were discarded):

```
  sample 1: A wrote 1786860247.673094680, B reacted 1786860247.673094680  -> 0.0 ms
  sample 2: A wrote 1786860252.799101720, B reacted 1786860252.800101721  -> 1.0 ms
  sample 3: A wrote 1786860257.922108592, B reacted 1786860257.923108594  -> 1.0 ms
  sample 4: A wrote 1786860263.051115533, B reacted 1786860263.051115533  -> 0.0 ms
  sample 5: A wrote 1786860268.181123169, B reacted 1786860268.181123169  -> 0.0 ms
```

**Same conclusion as boxlite: the redeploy loop must poll, not watch.** Data is visible
within 0–1 ms once the writer has written, so a poll interval sets the entire reload
latency. A host-side watcher is a viable alternative trigger, since 1c shows the host does
receive the event — the vm-manager could watch on the host and signal the guest.

### E. Are resource limits actually enforced? (follow-up to phase 6)

Phase 6 reports `memory / vcpu limits honoured : NO / NO`. **That verdict is wrong**, and
the probe's assertion is measuring the wrong quantity:

```ts
const mem  = kata(["--rm", "--memory", "512m", IMAGE, "sh", "-c", "free -m | awk '/Mem:/ {print $2}'"]);
const cpus = kata(["--rm", "--cpus", "2", IMAGE, "nproc"]);
record("cpus", cpus.stdout === "2");   // capability-test.ts:211-217
```

`free -m` and `nproc` report the **sandbox VM's** size. What constrains the workload is the
container's cgroup inside the guest, which carries exactly what was requested:

```
$ nerdctl run --rm --runtime io.containerd.kata-clh.v2 --memory 512m --cpus 2 alpine:latest sh -c '...'
  cgroup version : v2
  memory.max     : 536870912          <- 512 MiB exactly
  cpu.max        : 200000 100000      <- 2.0 CPUs exactly
  free -m total  : 2500 MiB           <- what phase 6 measures
  nproc          : 3                  <- what phase 6 measures
```

Memory enforcement is real — an allocation past the limit is OOM-killed:

```
  alloc 256M  : 268435456 bytes (256.0MB) copied, 0.721476 seconds, 354.8MB/s   rc=0
  alloc 800M  : Killed
```

CPU enforcement binds directionally, on fixed work across 4 workers:

```
  --cpus 1 : 5755 ms wall
  --cpus 2 : 4797 ms wall
  --cpus 4 : 3970 ms wall
```

(Each sample includes ~1.6 s of constant container boot, so the ratios understate the
effect; `cpu.max = 200000 100000` is the precise evidence.)

The VM's own sizing behaves differently for the two resources:

```
  --memory 512m  -> guest total 2372 MiB      --cpus 1 -> guest nproc 2
  --memory 1024m -> guest total 2372 MiB      --cpus 2 -> guest nproc 3
  --memory 2048m -> guest total 2372 MiB      --cpus 4 -> guest nproc 4
```

vCPUs scale with the request (`default_vcpus = 1` plus what was asked for), while guest RAM
stays flat regardless of `--memory` — the sandbox is sized by `default_memory = 2048` in
`configuration-clh.toml`, independent of the workload's limit.

The guest's reported size is **not** a host reservation. Guest memory is backed lazily, so
the host commits only what the workload actually touches:

```
=== baseline, no workloads ===
  host MemAvailable : 15297 MiB
  cloud-hypervisor  : 0 MiB RSS

=== one idle workload (--memory 512m, guest reports 2500 MiB) ===
  host MemAvailable : 15116 MiB
  cloud-hypervisor  : 166 MiB RSS

=== after touching 400 MiB inside the guest ===
  host MemAvailable : 14708 MiB  (delta 408 MiB)
  cloud-hypervisor  : 568 MiB RSS  (delta 402 MiB)

=== after freeing it inside the guest ===
  cloud-hypervisor  : 569 MiB RSS

=== four workloads ===
  cloud-hypervisor  : 1063 MiB RSS total
  per-workload RSS  : 569 MiB / 164 MiB / 167 MiB / 163 MiB
```

An idle workload costs about **165 MiB** of host RAM despite reporting 2500 MiB inside, and
touched pages are backed 1:1 on demand. Since the cgroup caps the workload at its limit, the
worst case per workload is its limit plus that overhead — roughly 680 MiB for a 512 MiB
workload, so a 16 GB host carries on the order of twenty, not six.

Two consequences follow, neither an isolation failure:

1. **Freed guest memory is not returned to the host.** Dropping the 400 MiB inside the guest
   left host RSS at 569 MiB. Without free page reporting or ballooning, each VM's host
   footprint is a high-water mark of everything it has ever touched, so a workload that
   briefly peaks holds that memory for its lifetime. This, not the declared size, is what
   governs density over time.
2. **Guests misreport their own size.** A workload reading `free`/`nproc` sees 2372 MiB and
   3 CPUs while its cgroup allows 512 MiB and 2 CPUs. This bites Kinotic apps specifically:
   a JVM left to default heap sizing takes ~1/4 of apparent physical memory (~593 MB),
   exceeds the 512 MiB cgroup, and is OOM-killed. Workloads need explicit heap and pool
   sizing, or the VM sized to match the limit.

### D. Can this be driven from Bun without shelling out?

**No usable client exists.** The npm registry has no official containerd client; the exact
names are unpublished:

```
containerd          -> HTTP 404
containerd-client   -> HTTP 404
node-containerd     -> HTTP 404
@containerd/client  -> HTTP 404
containerd-node     -> HTTP 404
```

The two real candidates are both abandoned:

| Package | Latest | Published | Versions | Downloads/mo |
|---|---|---|---|---|
| `@containers-js/containerd` | 0.0.1 | 2021-07-07 | 1 | 134 |
| `containerd-js` | 0.0.1 | 2021-04-26 | 1 | 7 |

Single 0.0.1 releases from 2021, five years stale, against containerd `v2.3.3` here.

The socket is reachable from Bun and speaks gRPC, so generating our own client is viable:

```
$ ls -l /run/containerd/containerd.sock
srw-rw---- 1 root root 0 Aug 16 05:56 /run/containerd/containerd.sock

$ bun run bun-containerd.ts
bun can open the socket : YES (node:net unix socket connect)
server speaks HTTP/2    : YES (SETTINGS frame received)
```

So the options are: generate gRPC stubs from containerd's `.proto` files with
`@grpc/grpc-js` + `@grpc/proto-loader` and maintain them ourselves, or shell out to
`nerdctl` permanently. Note the socket is `root:root 0660`, so the provider runs as root or
in a group granted access.

This is a genuine regression against boxlite's maintained Node SDK, and the largest
non-capability cost in this evaluation.

## 5. Assessment

### 5.1 Does it do the things boxlite gets wrong?

**YES**, on every count.

| Defect in boxlite | Kata + Cloud Hypervisor | Evidence |
|---|---|---|
| Cannot boot ≥3 volume mounts | **Fixed** — 4 mounts boot | phase 2: `3 volume(s) STARTED`, `4 volume(s) STARTED` |
| `network: disabled` cannot boot | **Fixed** — boots and denies | phase 7: `--network none : booted=YES exit=1` |
| Empty allowlist fails open | **Fixed** — host firewall fails closed | investigation B |
| Exit code invisible, VM zombies | **Fixed** | phase 5: `batch status/exit code: exited 42` |
| Entrypoint stdout discarded | **Fixed** | phase 4: `stdout="line-one" stderr="line-two"` |
| Rootfs above 1 GiB not allocated | **Not reproduced** | phase 6: `guest df / : 61.8G`, backed by the host's real 62 G ext4 |

The probe's `memory / vcpu limits honoured : NO / NO` is a **false negative** — see
investigation E. The container cgroup inside the guest carries exactly the requested limits
(`memory.max = 536870912`, `cpu.max = 200000 100000`) and enforces them: an 800 MB
allocation in a 512 MB workload is OOM-killed. Phase 6 asserts on `free -m` and `nproc`,
which describe the sandbox VM rather than the workload. Isolation holds.

The VM is sized independently of the limit, but that costs far less than its reported size
suggests: guest memory is backed lazily, so an idle workload consumes ~165 MiB of host RAM
while reporting 2500 MiB inside. Density is set by what workloads touch, not what they
declare. Two caveats remain — freed guest memory is never returned to the host, so each VM's
footprint ratchets to its high-water mark, and a guest misreports its own size to anything
reading `free`/`nproc`, which will OOM a JVM left on default heap sizing.

Also note the rootfs is **shared host storage**, not a per-workload disk: `guest df /`
reports the host's 62 G filesystem. There is no per-workload disk cap here at all, so disk
quota remains a separate problem — the XFS project-quota mechanism probed against boxlite
would still be the answer.

### 5.2 Does the logging design survive?

**YES**, and it gets simpler.

Both routes work. A host watcher sees guest writes to a mounted directory (`phase 3:
inotify saw the write : YES after 61 ms`, `host reads the content: "hello"`), so the current
Alloy design carries over unchanged. Additionally, entrypoint stdout/stderr is captured to a
plain JSON-lines file on the host (investigation A) that Alloy can tail directly, which
removes the need for workloads to write their own log files.

### 5.3 Can egress be restricted to the api-gateway alone?

**YES.** Investigation B restricts a workload to exactly one destination with two iptables
rules; the allowed destination completes a TCP connection while an unlisted host and a raw
IP both time out.

This is stronger than boxlite's `allowNet`: enforcement is on the host rather than inside
the sandbox, and it fails closed rather than open. The cost is that the vm-manager becomes
responsible for firewall lifecycle — installing rules once the CNI address is known and
removing them on teardown.

### 5.4 What is the cost?

**Install friction: high, and quietly dangerous.** The setup is not
`apt-get install`; it is a static bundle plus containerd, CNI and nerdctl, with release
assets resolved at run time. Two failures were hit:

1. An earlier revision constructed `kata-static-<ver>-amd64.tar.xz` while upstream had moved
   to `.tar.zst`, giving a 404. Already fixed on `develop`.
2. The `clh` shim symlink does not select Cloud Hypervisor. Without
   `/etc/kata-containers/configuration.toml`, **the stack silently runs QEMU while
   reporting a cloud-hypervisor version**, and the probe's own phase 0 guard does not catch
   it (section 3.5). That combination — a wrong hypervisor, a green setup, and a
   passing guard — is the most decision-relevant finding here. Anything automating this
   install must assert the running VMM process, not the configured one.

**Boot latency: good.** `1761 ms, 1631 ms, 1633 ms` cold run-to-exit under Cloud
Hypervisor, versus ~2100 ms under QEMU. Fast enough for the dev edit/redeploy loop,
especially since the reload path (investigation C) reuses a running VM rather than booting
one.

**Driving it from Bun: the real cost.** No maintained Node/Bun containerd client exists
(investigation D). Either we generate and maintain gRPC stubs from containerd's protos, or
we shell out to `nerdctl` indefinitely. Boxlite's clean Node SDK is a genuine advantage that
this stack does not match, and this is the main ongoing engineering expense to weigh against
the capability wins above.
