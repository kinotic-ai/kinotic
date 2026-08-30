# Context: choosing a microVM runtime for Kinotic

I run customer Kinotic applications (TypeScript on Bun) in microVMs. This is the layer
customers' developers use during their dev cycle, so it is a **production multi-tenant
boundary**: one customer's developer's half-finished code runs on the same infrastructure as
another's, and these environments hold live credentials to our api-gateway.

Requirements: (1) isolate customer code in a microVM, multi-tenant; (2) ship workload logs to
Loki via a host-side Grafana Alloy process; (3) let customer code reach our api-gateway and as
little else as possible; (4) support a fast edit/redeploy loop where one container pulls latest
app code into a shared location and an already-running microservice VM picks it up without a
full redeploy.

Current runtime is **boxlite** (a libkrun wrapper), now on 0.10.0. We probed it hard on real
Azure hardware with nested virtualization, then evaluated **Kata Containers on Cloud
Hypervisor** as a replacement on the same class of hardware. Everything below is measured, not
inferred.

---

# PART 1 — boxlite findings

Measured on **0.9.7**, all reproduced on Azure D4s_v3, kernel 6.8.0-1064-azure. Each finding
carries its status against **0.10.0** (released 2026-08-28), read from the 0.10.0 source and its
commit range. Three are fixed, one of them only halfway; two are not:

| # | Finding | 0.10.0 |
|---|---|---|
| 1.1 | 3+ volume mounts exhaust the VM's IRQs | **open** — [issue #935](https://github.com/boxlite-ai/boxlite/issues/935), unfixed |
| 1.2 | `network: { mode: 'disabled' }` cannot boot | **fixed** — [#1367](https://github.com/boxlite-ai/boxlite/pull/1367) |
| 1.3 | An empty egress allowlist fails open | **open** — by design, documented as "Empty `allow_net` = full access" |
| 1.4 | A rootfs above ~1 GiB is never allocated | **fixed** — [#1231](https://github.com/boxlite-ai/boxlite/pull/1231) |
| 1.5 | A finished workload looks like a running one | **fixed** — [#988](https://github.com/boxlite-ai/boxlite/pull/988), exit code still unreachable from Node |

The vm-manager's `BoxliteProvider` is on 0.10.0 and carries a workaround for each open one.

## 1.1 A box cannot boot with 3 or more volume mounts — OPEN on 0.10.0

Swept with plain host directories, nothing exotic:

    1 volume(s): STARTED   guest sees: /v0 /var
    2 volume(s): STARTED   guest sees: /v0 /v1 /var
    3 volume(s): FAILED    VM failed to start (libkrun status=-22)
    4 volume(s): FAILED    VM failed to start (libkrun status=-22)

Root cause, from the shim trace:

    [krun] krun_start_enter called
    [ERROR krun] Building the microVM failed: RegisterNetDevice(IrqsExhausted)
    [krun] krun_start_enter returned (status=-22, elapsed=43ms)

So it is **IRQ exhaustion during virtio device registration**, not a volume cap per se. Probing
what shares that budget:

    2 volumes + 1 port        : STARTED
    2 volumes + 3 ports       : STARTED
    0 volumes + 4 ports       : STARTED
    2 volumes + diskSizeGb    : STARTED
    3 volumes + diskSizeGb    : FAILED (same -22)

Published ports do NOT draw from the same budget, and a sized rootfs costs nothing. Only
volume mounts do. Every workload already spends one mount on /var/log/kinotic, so a workload
gets exactly one of its own — this blocks the design we want (read-only code in, writable work
dir, logs).

**Unchanged on 0.10.0.** The libkrun submodule is pinned to the same commit in both releases
(`e12b9b3780ffa8df9f3e1797b217d13453479167`), so the VMM that raises the error is byte-identical;
`git log v0.9.7..v0.10.0` contains no commit touching IRQ or MMIO device registration, and one
virtiofs device per volume is unchanged. x86_64 still has 11 IRQs
(`arch/src/x86_64/layout.rs`: `IRQ_BASE = 5`, `IRQ_MAX = 15`), one per `register_mmio_device`.
Reported upstream as [#935](https://github.com/boxlite-ai/boxlite/issues/935) (filed 2026-07-07,
still open, one comment). The provider still caps a workload at one declared mount.

## 1.2 `network: { mode: 'disabled' }` cannot boot — FIXED in 0.10.0

Distinct failure from the -22 above — no krun error line at all:

    Error: engine reported an error: Box ... failed to start
    Exit code: 159 (unknown signal)
    Console output: empty (no kernel or guest messages captured)
    [shim] T+0ms: main() entered
    [shim] T+3ms: logging initialized
    [shim] T+4ms: engine created
    [shim] T+4ms: instance created (krun FFI calls done)
    [shim] T+5ms: entering VM (krun_start_enter)
    [krun] krun_start_enter called

Note the absence of a `gvproxy created` line that the -22 traces have — it dies inside
`krun_start_enter`. Also `mode: 'disabled'` combined with `allowNet` is rejected at config
validation before a box exists:

    Error: configuration error: network.mode="disabled" is incompatible with allow_net.

**Root cause, fixed in 0.10.0.** Exit 159 is 128+31 = SIGSYS: the VMM seccomp filter is applied
only when no network backend is configured, which is exactly why only `disabled` died and why
the trace has no `gvproxy created` line —

    // src/shim/src/main.rs:157
    if config.security.jailer_enabled
        && config.security.seccomp_enabled
        && config.network_backend_spec.is_none()   // no gvproxy => apply filter
    {
        seccomp::apply_vmm_filter(&config.box_id)?;

The filter's default action is `trap`, and glibc's `get_nprocs()` calls `time(NULL)` during
vCPU thread init. [#1367](https://github.com/boxlite-ai/boxlite/pull/1367) adds `time` to the
x86_64 allowlist. Scoped to `x86_64-unknown-linux-gnu`; aarch64 never had the syscall.

The provider now maps `NetworkMode.DISABLED` to boxlite's own `outbound: { mode: 'disabled' }`,
which is stronger than the allowlist workaround below — the guest gets no interface at all
rather than one filtered down to nothing. **Still owed: this is verified only on macOS arm64,
where the seccomp filter that caused the failure does not apply. Re-run it on the Azure node.**
boxlite also refuses to publish ports on a box with no network. `CLOUD_HYPERVISOR` accepts the
same record and silently drops the bindings, so rather than let a workload's fate depend on the
node it lands on, the vm-manager now rejects `mode: DISABLED` with `portMappings` before either
provider sees it (`DefaultVmManager.startWorkload`).

## 1.3 The egress allowlist fails open when empty — OPEN on 0.10.0, by design

Four cases, all boxes booted, probing an allowed host / an unlisted host / a raw IP:

    E. network option omitted entirely   -> all 4 targets REACHED
    B. mode 'enabled', no allowNet       -> all 4 targets REACHED
    F. mode 'enabled', allowNet: []      -> all 4 targets REACHED   <-- byte-identical to B and E
    C/D. allowNet ['example.com']        -> example.com REACHED, cloudflare.com blocked,
                                            raw IP 1.1.1.1 blocked

`allowNet: []` is indistinguishable from not setting it. A policy that computes down to an
empty array silently grants unrestricted egress.

A *populated* allowlist does enforce properly, including by raw IP (so it is not DNS-only).
Blocked names are sinkholed:

    dns:cloudflare.com  REACHED exit=0  Name: cloudflare.com | Address: 0.0.0.0
    http://cloudflare.com  blocked exit=1  wget: can't connect to remote host (0.0.0.0): Connection refused
    http://1.1.1.1         blocked exit=1  wget: error getting response

Note DNS resolution itself succeeds (nslookup exit=0) and is answered 0.0.0.0 — the block
happens on the connection.

Workaround for no-egress on 0.9.7, where `mode: 'disabled'` won't boot (§1.2) and `[]` is open:
a populated allowlist naming only something unreachable. Both tested and both work:

    G. allowNet ['192.0.2.1']         (RFC 5737 TEST-NET-1) -> all 3 targets blocked
    H. allowNet ['no-egress.invalid'] (RFC 2606)            -> all 3 targets blocked

G is the tighter of the two: it yields a uniform `Connection refused` (raw IP refused at its
real address, not sinkholed) and doesn't depend on how the resolver treats a `.invalid` name.

**Unchanged on 0.10.0, and intentional.** The doc comment on the option is verbatim in both
releases (`src/boxlite/src/runtime/options.rs:957`):

    /// Network enabled. Empty `allow_net` = full access.
    /// Non-empty = only listed hosts/IPs allowed (DNS sinkhole for others).

and the filter is only installed when the list is non-empty
(`libgvproxy-sys/gvproxy-bridge/main.go:466`), leaving `filter == nil` to take the unfiltered
forward path (`forked_tcp.go:58`). The provider still sends `192.0.2.1` for an ENABLED policy
whose `allowedHosts` is empty.

What did change is enforcement on the *populated* path we depend on — three breaking hardening
fixes: `allow_net` now applies to UDP egress ([#1090](https://github.com/boxlite-ai/boxlite/pull/1090)),
to the host alias IP ([#1106](https://github.com/boxlite-ai/boxlite/pull/1106)), and host checks
bind to the resolved destination ([#1330](https://github.com/boxlite-ai/boxlite/pull/1330)).

## 1.4 A rootfs above ~1 GiB is reported to the guest but never allocated — FIXED in 0.10.0

Same 1536 MiB write into each box, only `diskSizeGb` varying:

    diskSizeGb 1: guest df 943.3M | dd exit 1 | landed 930 MiB | VM alive YES | host box dir 978 MiB
    diskSizeGb 2: guest df 1.9G   | dd exit 1 "I/O error" | landed 1536 MiB | VM alive YES | host box dir 1071 MiB
    diskSizeGb 4: guest df 3.7G   | dd exit 1 | (no bytes line, box died) | VM alive NO | host box dir 1071 MiB
    diskSizeGb 8: guest df 7.5G   | dd exit 1 "Read-only file system" | landed 1536 MiB | VM alive NO | host box dir 1071 MiB

`host box dir` is **1071 MiB for 2, 4 and 8 alike** — identical across a 4x spread. Only
diskSizeGb 1 comes in lower (978 MiB) because its own 930 MiB cap binds first. The backing
store stops at a fixed ~1071 MiB regardless of what was requested.

The damaging part: `guest df` scales correctly (1.9G/3.7G/7.5G), so the guest is told it has
room that does not exist, and at sizes 2 and 8 the guest believes the full 1536 MiB write
succeeded while the host directory only ever reached 1071 MiB. A file reports a length whose
bytes are not there.

Three inconsistent failure modes above the ceiling: I/O error (box survives) at 2 GiB, silent
death mid-write at 4 GiB, read-only remount + death at 8 GiB. The death error carries no shim
trace at all:

    Error: internal error: spawn_failed: internal error: build failed: failed to execute workload

**Root cause, fixed in 0.10.0.** The ceiling was a hard-coded `RLIMIT_FSIZE` on the jailed shim,
blind to the disk size accepted in the same options; the deaths above it were `SIGXFSZ`.

    // 0.9.7 — src/boxlite/src/runtime/advanced_options.rs:235
    max_file_size: Some(1024 * 1024 * 1024), // 1GB

    // 0.10.0 — src/boxlite/src/runtime/options.rs:677, in BoxOptions::sanitize
    self.advanced.security.resource_limits.max_file_size = Some(
        Bytes::from_gib(
            self.disk_size_gb
                .unwrap_or(DEFAULT_DISK_SIZE_GB)        // 10
                .saturating_mul(FSIZE_DISK_MULTIPLIER), // 2
        ).as_bytes(),
    );

Reported upstream as [#1152](https://github.com/boxlite-ai/boxlite/issues/1152), fixed by
[#1231](https://github.com/boxlite-ai/boxlite/pull/1231). Note the 0.9.7 ceiling bound *every*
box, not only sized ones — a workload that never declared a disk size was capped at 1 GiB of
writes too.

One residual, called out as an accepted regression in that PR: the COW disk is sized
`max(disk_size_gb, base image size)`, so asking for less than the image silently widens, and an
image more than twice the requested size still dies with `EFBIG`. Declare a disk at least as
large as the image.

The provider's `MAX_WORKLOAD_DISK_MB` cap is removed; `diskSizeMb` now passes straight through.
**Still owed: the 1/2/4/8 GiB sweep with the host-dir check, re-run on the Azure node.**

## 1.5 A finished workload was indistinguishable from a running one — FIXED in 0.10.0

Reported upstream as [#933](https://github.com/boxlite-ai/boxlite/issues/933): when a box's
entrypoint exited, the VM stayed up, `getInfo` kept reporting `state.running: true` for a full
60 seconds past a 3-second entrypoint, and the only external signal was a later `exec` failing.

Fixed by [#988](https://github.com/boxlite-ai/boxlite/pull/988), which made COMMAND the
container's init rather than a tenant beside one built from the image's own ENTRYPOINT+CMD. The
box now lives exactly as long as that command, and a host-side watcher records the transition
(`src/boxlite/src/litebox/watcher.rs:180` → `record_main_command_exit`, neither of which exists
in the 0.9.7 tree). The provider now refreshes a live workload's status on `getWorkload` and
`listWorkloads`, so a guest that ended on its own is reported instead of staying RUNNING forever.

That PR also fixes a live bug for our long-running services: `oven/bun` prints help and exits, so
under 0.9.7 the image's own short-lived init died and SIGKILLed the workload's server — RSTing
its connections — while the box still reported Running.

**The exit code did not come with it.** The core records it (`runtime/types.rs:485`) and the CLI
reads it via `inspect .State.ExitCode`, but the Node binding drops the field —
`sdks/node/src/info.rs:160` is byte-identical to 0.9.7 (`status`, `running`, `pid`).
[PR #1237](https://github.com/boxlite-ai/boxlite/pull/1237) is the binding half and is still
open.

Until it merges the provider reads the record the guest writes, at
`{BOXLITE_HOME}/boxes/{boxId}/shared/containers/{containerId}/exit.json` — one container per
box, `{"exit_code":42}`, removed by `Container.Init` before each run, so its presence means the
run is over. That is boxlite's private layout rather than its API, so a missing or unreadable
record is treated as an unknown outcome and reported FAILED, never as success. With it the
provider supports run-to-completion workloads: a foreground `start` resolves at run end with
the status and code, measured at `exit 0` → STOPPED/0 and `exit 42` → FAILED/42.

## 1.6 What boxlite gets RIGHT (don't lose these)

- `diskSizeGb` genuinely caps the rootfs at 1 GiB and below (930 MiB landed, dd exit 1).
- XFS project quotas enforce through a virtiofs volume mount: `guest df /capped` reports the
  64M quota rather than the 4G filesystem, the write stops at exactly 64.0 MiB, dd exits 1,
  and the host quota report confirms `#4242 65536 / 65536`.
- `mount -o loop,prjquota` works fine on kernel 6.8.0-1064-azure — that was never a problem.
- A clean, maintained Node SDK. This is a real advantage and the thing Kata does worst.

Quota accounting cost is NOT measurable: across ordered repeats, round 2 no-quota (47.5 MiB/s)
came in slower than round 2 with-quota (103.1 MiB/s). Earlier single-pair readings suggesting
~36% or ~13% were first-write warm-up artifacts. Treat quota overhead as noise.

## 1.7 Method warning that applies to everything above

Two of boxlite's own probes originally masked their exit codes by piping into `tail` (a shell
reports the LAST command's status), which made `dd` and `nslookup` look successful when they
had failed. Fixed by `cmd; echo "exit=$?"` and reading that. Before the fix I reported
"dd exits 0 while being capped, so exit codes can't detect quota exhaustion" — that was
entirely an artifact. dd exits 1 correctly on both the rootfs cap and the project quota.

---

# PART 2 — Kata Containers on Cloud Hypervisor

## 2.1 Exact stack

    kata-containers  : 4.0.0     asset kata-static-4.0.0-amd64.tar.zst
    nerdctl          : v2.3.5    asset nerdctl-full-2.3.5-linux-amd64.tar.gz
    cloud-hypervisor : v51.1     (bundled in kata-static)
    containerd       : v2.3.3    (bundled in nerdctl-full)
    guest kernel     : 6.18.35
    host kernel      : 6.8.0-1064-azure   Azure Standard_D4s_v3, Ubuntu 22.04, 4 vCPU, 16 GB
    runtime name     : io.containerd.kata-clh.v2

**No Docker daemon** — `docker`/`dockerd` not installed. Standard OCI images pulled from
Docker Hub (`docker.io/library/alpine:latest`, `oven/bun:latest`). Chain is
nerdctl -> containerd -> containerd-shim-kata-v2 -> cloud-hypervisor. cgroup v2, overlayfs
snapshotter. nerdctl is a Docker-CLI-compatible frontend, which is why the commands look like
`docker run`.

## 2.2 Full probe report

    === REPORT: what the vm-manager needs, and whether this stack provides it ===
      workloads are real VMs                  : YES (cloud-hypervisor seen: YES)
      OCI entrypoint / env / workdir          : YES / YES / YES
      three volume mounts (boxlite: NO)       : YES   four: YES
      readOnly mount enforced                 : YES
      host inotify sees guest writes          : YES   host reads content: YES
      entrypoint stdout/stderr captured       : YES / YES   (boxlite: NO)
      run-to-completion exit code (boxlite: NO): YES
      restart in place, rootfs intact         : YES
      state survives the manager process      : YES
      memory / vcpu limits honoured           : YES / YES
      egress open by default                  : YES
      no-egress mode boots (boxlite: NO)      : YES   and denies: YES
      published ports reachable from the host : YES
      workload has a host-side address        : YES

Supporting phase detail:

    Phase 2: 1/2/3/4 volumes all STARTED; readOnly enforced (write exit=1)
    Phase 3: host inotify saw the guest write after 61 ms, host read "hello"
    Phase 4: nerdctl logs exit=0 stdout="line-one" stderr="line-two"
    Phase 5: batch status/exit code: exited 42; restart in place OK, boots.log has 2 lines;
             state visible to a new process; stable 24-char id for logs
    Phase 6: cgroup memory.max = 536870912 (512 MiB) for --memory 512m;
             cgroup cpu.max = 200000 100000 (2 cpus) for --cpus 2;
             but the guest sees 2500 MiB / 3 cpus;
             guest df / = 61.8G (the HOST's filesystem — no per-workload disk at all)
    Phase 7: default egress open; --network none booted=YES and denied (exit=1);
             published port 18080 -> HTTP 200; workload address 10.4.0.147
    Phase 8: cold run-to-exit 1783 / 1676 / 1716 ms

So it fixes **every** boxlite defect in Part 1: 4 mounts boot, no-egress boots and denies,
exit codes are visible, stdout is captured, and the disk-size lie does not reproduce.

The `(boxlite: NO)` annotations in that report were measured against 0.9.7 and are the reason
this evaluation was run. Against **0.10.0** only two of them still hold:

| Report line | 0.9.7 | 0.10.0 |
|---|---|---|
| three volume mounts | NO | **NO** — [#935](https://github.com/boxlite-ai/boxlite/issues/935) open |
| no-egress mode boots | NO | YES — [#1367](https://github.com/boxlite-ai/boxlite/pull/1367) |
| run-to-completion exit code | NO | YES with a workaround — the box stops and records the code ([#988](https://github.com/boxlite-ai/boxlite/pull/988)); the Node SDK drops it ([#1237](https://github.com/boxlite-ai/boxlite/pull/1237) open), so the provider reads it off disk (§1.5) |
| entrypoint stdout/stderr captured | NO | NO — unchanged; workloads still write log files |

The disk-size lie is fixed too ([#1231](https://github.com/boxlite-ai/boxlite/pull/1231)). So
Kata's remaining advantages over boxlite are narrower than this section reads: unlimited mounts,
host-side stdout capture, a fail-closed egress default, and an exit code that arrives through a
supported API rather than off another project's private layout.

## 2.3 Resource limits are enforced

Inside a `--memory 512m --cpus 2` guest the cgroup carries exactly what was requested:

    memory.max : 536870912          = 512 MiB exactly
    cpu.max    : 200000 100000      = 2.0 CPUs exactly

Enforcement is real and deterministic:

    bs=256M (under limit)  exit=0    exit=0    exit=0
    bs=700M (over limit)   exit=137  exit=137  exit=137
    guest dmesg: dd invoked oom-killer: gfp_mask=0xcc0(GFP_KERNEL), oom_score_adj=-997
                 oom_kill_process+0xef/0x1f0

CPU cap binds directionally on fixed work across 4 workers (each sample includes ~1.6 s
constant boot, so ratios understate): --cpus 1 = 5755 ms, --cpus 2 = 4797 ms, --cpus 4 = 3970 ms.

**A 512 MB limit does restrict the workload to 512 MB, with the config as shipped.**

## 2.4 But the VM's size cannot be made to track the limit

Tested against a `--memory 512m` workload:

    as shipped (default_memory = 2048)      guest free 2372 MiB | cgroup 512 | host RSS 167 MiB
    static_sandbox_resource_mgmt = true     guest free 2490 MiB | cgroup 512 | host RSS 171 MiB
    default_memory = 512                    guest free  990 MiB | cgroup 512 | host RSS 136 MiB

`static_sandbox_resource_mgmt = true` does NOT size the sandbox from the workload's limit.
`default_memory` shrinks the VM but is global, not per-workload, and doesn't land on the
requested figure. vCPUs do track the request (`default_vcpus = 1` + requested: --cpus 1 -> 2,
--cpus 2 -> 3, --cpus 4 -> 4); memory does not.

## 2.5 Memory is backed lazily — density is better than it looks, with a ratchet

    baseline, no workloads                  host MemAvailable 15297 MiB | CH RSS 0 MiB
    1 idle workload (guest reports 2500)    host MemAvailable 15116 MiB | CH RSS 166 MiB
    after touching 400 MiB inside           host MemAvailable 14708 MiB | CH RSS 568 MiB (delta 402)
    after FREEING it inside the guest       CH RSS 569 MiB   <-- not returned
    4 workloads                             CH RSS 1063 MiB total; per-VM 569/164/167/163 MiB

An idle workload costs ~165 MiB of host RAM despite reporting 2500 MiB inside, and touched
pages are backed 1:1 on demand. So the guest's reported size is address space, not a
reservation, and a 16 GB host carries roughly twenty 512 MiB workloads, not six.

**The real constraint is the ratchet**: freed guest memory is never returned to the host (no
balloon / free page reporting configured), so each VM's host footprint is a high-water mark of
everything it has ever touched. Spiky dev workloads will drift toward their cgroup ceiling and
stay there.

## 2.6 What Bun sees (this is what we actually deploy)

Bun 1.3.14 inside a `--memory 512m --cpus 2` Kata guest:

    CORRECT (reads the cgroup):
      process.constrainedMemory()      -> 512 MiB
      navigator.hardwareConcurrency    -> 2

    WRONG (reports the VM):
      os.totalmem()                    -> 2500 MiB
      os.freemem()                     -> 2407 MiB
      process.availableMemory()        -> 2407 MiB
      os.cpus().length                 -> 3

Better than the JVM situation — Bun exposes the cgroup truth, you just have to ask for it. A
Kinotic app sizing a worker pool from `navigator.hardwareConcurrency` or a cache budget from
`process.constrainedMemory()` is correct with no configuration. One sizing from `os.totalmem()`
or `os.cpus().length` over-commits ~5x and dies. `process.availableMemory()` is the trap: it
sounds cgroup-aware, sits next to `constrainedMemory()`, and returns the VM's free memory.

A Bun process growing past its limit is killed abruptly at the boundary:

      allocated  448 MiB   rss=476 MiB
      allocated  480 MiB   rss=508 MiB
    EXIT=137

No `exit` handler ran, no SIGTERM first — SIGKILL from the OOM killer, uncatchable. Workloads
cannot flush logs, close connections or checkpoint. The vm-manager must read exit 137 as an
OOM kill, not a crash.

## 2.7 Logging: two viable designs, one simpler than today's

Host-side inotify sees guest writes to a mounted directory (61 ms), so the existing Alloy
design carries over unchanged.

Additionally, container stdout/stderr lands on the host as a plain regular file:

    /var/lib/nerdctl/<hash>/containers/default/<container-id>/<container-id>-json.log
    regular file, mode 0600 root-owned, on the host's ext4 root fs

    {"log":"stdout-1\n","stream":"stdout","time":"2026-08-16T05:58:27.530909668Z"}
    {"log":"stderr-1\n","stream":"stderr","time":"2026-08-16T05:58:27.531224069Z"}

Docker-compatible JSON-lines, stdout/stderr distinguished, each line timestamped. Confirmed
inotify-tailable (MODIFY fires, new line readable). **Alloy can tail container logs directly**,
which would retire the log-file contract workloads carry today.

## 2.8 Egress can be restricted to one destination (host firewall, not runtime config)

Kata has no allowlist of its own. Against workload address 10.4.0.55:

    iptables -I FORWARD 1 -s 10.4.0.55 -d 172.66.147.243 -j ACCEPT   # the one allowed destination
    iptables -I FORWARD 2 -s 10.4.0.55 -j DROP                       # everything else

    BEFORE: allowed -> HTTP 403 (connected); unlisted -> HTTP 403 (connected); raw IP -> exit 0
    AFTER : allowed -> HTTP 403 (still connected); unlisted -> timed out; raw IP -> timed out

(The 403 is the origin rejecting a raw-IP Host header — it is an HTTP response, so TCP
completed and the destination is reachable.)

Stronger than boxlite's allowNet: enforced on the host where customer code cannot reach it,
and it fails closed (an empty allowlist means only the DROP rule exists) — still true on 0.10.0,
where an empty `allow_net` remains full access by design (§1.3). Cost: the vm-manager owns
firewall lifecycle, keyed on a CNI address assigned per container (10.4.0.22, .47, .55 across
runs), so rules must be installed after the address is known and removed on teardown.

## 2.9 The redeploy loop must poll, not watch

Two containers sharing a host directory. Container B watching /shared does NOT see container
A's write, over a full 20-second window:

    inotifywait -e create,modify,close_write,attrib -t 20 /shared
      Setting up watches. / Watches established. / EXIT=2      (2 = timeout, no event)

Two controls confirm the watcher itself is fine:

    write made INSIDE B      -> /shared/ CREATE local.js   EXIT=0
    inotify on the HOST      -> host saw: CREATE hostwatch.js

The bytes do arrive, essentially instantly — propagation measured from host-side nanosecond
mtimes (busybox `date` ignores %N, so in-guest stamps were useless):

    0.0 ms, 1.0 ms, 1.0 ms, 0.0 ms, 0.0 ms

Same limitation as boxlite: data visible, cross-guest events not. So either poll inside the
guest (poll interval sets the entire reload latency, and data is there within 1 ms), or have
the host watch and signal the guest — the host DOES receive the event.

## 2.10 The main ongoing cost: no Bun/Node containerd client

The real provider would talk to containerd's gRPC API on /run/containerd/containerd.sock.
On npm, the obvious names are unpublished:

    containerd, containerd-client, node-containerd, @containerd/client, containerd-node -> all HTTP 404

The only two real candidates are abandoned:

    @containers-js/containerd  0.0.1  published 2021-07-07  1 version  134 downloads/mo
    containerd-js              0.0.1  published 2021-04-26  1 version    7 downloads/mo

against containerd v2.3.3. The socket is reachable from Bun and speaks gRPC:

    /run/containerd/containerd.sock  srw-rw---- root:root
    bun can open the socket : YES (node:net unix socket connect)
    server speaks HTTP/2    : YES (SETTINGS frame received)

So generating stubs from containerd's protos with @grpc/grpc-js + @grpc/proto-loader is
viable, or we shell out to nerdctl permanently. Note the socket is root:root 0660. This is the
largest ongoing engineering expense and the one thing boxlite clearly does better.

---

# PART 3 — Method warning, and what is still unverified

Several conclusions were overturned during this work, all the same way: a measurement's verdict
was reported without checking what it actually measured.

1. "dd exits 0 while being capped" — an artifact of the boxlite probe piping dd into `tail`.
2. "quota accounting costs ~36%" — first-write warm-up; there is no measurable cost.
3. "resource limits are not honoured" and "each VM reserves 2.4 GB" — the first read the VM
   instead of the cgroup, the second read address space instead of committed memory.
4. The stack ran QEMU for an entire probe run while every version string reported
   cloud-hypervisor, and the harness's own guard could not detect it: it matched a 16-character
   name against a `comm` field the kernel truncates to 15.

Items 1, 3 and 4 are now fixed in the harness and re-run; the reports above are post-fix. The
pattern is worth carrying forward regardless: **a check that looks like it measures the thing
often does not**, so the remaining YES verdicts deserve the same scrutiny before anything rests
on them — particularly the two doing security work, `readOnly mount enforced` and `no-egress
mode boots and denies`, neither of which has had its measurement independently confirmed.

Also worth noting phase 6 reports `guest df / : 61.8G` — the workload sees the HOST's
filesystem, so there is no per-workload disk cap in this stack at all, and XFS project quotas
(which do work) remain the answer for disk.

# Artifacts

Full writeup with verbatim probe output: `kinotic-js/kata-ch-test/RESULTS.md` on branch
`claude/vm-manager-workload-concurrency-c07ssf` (repo `kinotic-ai/kinotic`, based on `develop`).
Harness: `kinotic-js/kata-ch-test/`. boxlite probes: `kinotic-js/boxlite-test/`.
A working Kata+CH VM is still up: resource group `rg-kata-probe`, VM `vm-kata-probe`, eastus,
Standard_D4s_v3.

# What I want help with

Whether to move from boxlite to Kata on Cloud Hypervisor, and if so how to structure the
provider: the containerd client question (generate gRPC stubs vs shell out), VM sizing so
guests don't misreport, firewall lifecycle for per-workload egress, disk capping given there
is no per-workload disk, whether the redeploy loop polls in-guest or the host watches and
signals, and how to handle uncatchable OOM kills (exit 137) for workloads that need to flush
state.

boxlite 0.10.0 changes the weight of that question rather than settling it. Two of the four
Part 1 defects are gone and a finished workload is now observable, so what remains against
boxlite is: one workload volume mount instead of the three the redeploy design wants, an egress
allowlist that fails open when empty, no exit code from the Node SDK, and no host-side stdout
capture. Kata answers all four, and costs a containerd client we would have to build.
