# boxlite-test

Evaluation harness for [BoxLite](https://docs.boxlite.ai/) — a set of standalone
scripts probing detach behavior, named-box reuse, and shared-volume semantics.

**Verified against:** `@boxlite-ai/boxlite@0.9.5` (findings 1–4) and `0.9.7`
(findings 5–8), Bun 1.3.10, macOS arm64 (`@boxlite-ai/boxlite-darwin-arm64`). Findings
9–15 were verified on 0.9.7 under Bun 1.3.14 on an Azure `Standard_D4s_v3`
(Ubuntu 22.04, kernel 6.8, KVM), since they need nested virtualization and XFS. Finding 16
was verified on `0.10.0`, which the project now pins, under Bun 1.3.10 on macOS arm64. The
original detach bug was filed on Linux/WSL2; findings here reproduce cross-platform.
Re-run the probes after a boxlite upgrade to confirm the findings still hold.

```bash
bun install
bun run src/<file>.ts
```

---

## Findings

### 1. Detached boxes are now reported correctly (bug fixed in 0.9.5)

The bug in [`bug-report-detach.md`](./bug-report-detach.md): a `detach: true` box
kept running after the process exited, but `listInfo()` wrongly reported it as
`stopped` / `running: false`.

Reproduced with `src/detach-test.ts` (creates the box + an HTTP server, then exits
without `stop()`), then queried after exit:

```jsonc
// listInfo() after the process exited — was "stopped" before the fix:
{
  "id": "UIqfekLFkTqv",
  "state": { "status": "running", "running": true, "pid": 21052 },  // ✅ correct now
  "name": "detach-http-test"
}
```

`curl http://localhost:8080` also returned `200` after exit, confirming the VM
was genuinely alive. **Fixed.**

### 2. Box reuse is keyed on the box `name`

With `reuseExisting: true`, `SimpleBox` calls `runtime.getOrCreate(opts, name)` —
the **name** is the identity key (`simplebox.d.ts:88`: *"reuse an existing box with
the same name"*). Running `src/connect-test.ts` twice:

| Proof | Run #1 | Run #2 | |
|-------|--------|--------|---|
| `box.created` | `true` | `false` | reused, not recreated |
| Box ID / PID | `dwmWoNuE…` / 22645 | same | same VM, not restarted |
| uptime | 0.11s | 35.81s | ran continuously between runs |
| `/root/visits.log` | 1 line | 2 lines | filesystem state persisted |

> Reuse only works if you set a stable `name`. No name → `getOrCreate` has nothing
> to match → you get a fresh box every run.

### 3. Volumes are shared across boxes, but `inotify` does not cross the VM boundary

Two boxes mounting the same `hostPath` share the directory in real time:

```ts
volumes: [{ hostPath: shared, guestPath: "/app" }]  // mounted into BOTH boxes
```

Box B edits `/app/app.ts`; Box A runs a watcher on the same file. Two distinct
questions, two different answers:

| What | Mechanism | Cross-box edit seen? | Test |
|------|-----------|:---:|------|
| **Data** — does A *read* B's new bytes? | virtio-fs share | ✅ **YES** | both |
| **Event** — does `bun --watch` *detect* it? | inotify (kqueue) | ❌ **NO** | `volume-share-test.ts` |
| **Event** — does chokidar `{ usePolling }` *detect* it? | poll (stat) | ✅ **YES** | `volume-poll-test.ts` |

**Why `bun --watch` fails:** `inotify` is a single-kernel, VFS-level event source.
A write from Box B goes through *B's* kernel → host fs → virtio-fs into A's page
cache — A's kernel never *performed* a write, so it emits no event. Reads are
coherent; events are not. This is the same well-known limitation behind Docker
Desktop on Mac/Windows, Vagrant shared folders, and WSL2 `/mnt/c` hot-reload — and
it's documented in `inotify(7)` for network filesystems ("fall back to polling").

**The fix — use a poll-based watcher.** The Bun CLI has no poll mode (`bun --watch`
and `bun --hot` are both event-based). The runtime exposes `fs.watchFile` (poll, but
single-file, no recursion), so for a real codebase use **chokidar with
`usePolling: true`** — it `stat`-polls a directory tree and re-reads fresh metadata
through the share:

```ts
import { watch } from "chokidar"; // chokidar@5
watch("/app", { usePolling: true, interval: 300, ignoreInitial: true })
  .on("all", (event, path) => { /* re-run / reload here */ });
```

`volume-poll-test.ts` proves this end-to-end against chokidar@5 installed inside the
box: Box B's edit to a **nested** file (`/app/src/app.ts`) triggered a recursive
reload in Box A ~4s later. `nodemon --legacy-watch` under Bun wraps the same
chokidar-polling mechanism with process restart. Tune `interval` to trade reload
latency against CPU.

### 4. The *host* DOES see guest writes — inotify fires across the boundary (confirmed)

Finding #3 is guest→guest. The log-shipping design instead has **one Alloy on the host**
tailing per-VM log files the guests write to a shared volume, so the question is whether a
**host-side** watcher is notified when a guest writes. Unlike #3, it is — and the mechanism
is why: a guest write reaches the host via `virtiofsd` performing a real `write()` on the
**host** kernel against the plain (ext4) backing file. The host watcher isn't watching a
FUSE mount; it's watching that backing file, and a real host write emits a real inotify
event. (Grafana's "fsnotify won't work on FUSE" caveat is about watching a FUSE *mount* —
not this case.)

`volume-host-notify-test.ts` confirms it with both `fs.watch` (event/inotify) and
`fs.watchFile` (poll), 9/9 rounds firing both on each platform:

| host | backend | event saw guest write? | event latency | poll latency (300ms interval) |
|------|---------|:---:|---------|--------------|
| **Linux + KVM** (Ubuntu 22.04, kernel 6.8, virtio-fs) — *authoritative* | inotify | ✅ YES | 21–48 ms | 232–319 ms |
| macOS arm64 (Hypervisor.framework) | FSEvents | ✅ YES | 37–93 ms | 127–344 ms |

**Design implication:** host-side Alloy can use **fsnotify** (event-driven, sub-100 ms) —
no polling fallback needed, so no constant re-reads of every per-VM file even at high VM
density.

### 5. `autoRemove: false` makes stop a pause, not an end (`autoremove-restart-test.ts`)

A box is a persistent record (SQLite registry + `~/.boxlite/boxes/<id>/` dir) plus an
optional running VM. What `stop()` means depends entirely on `autoRemove`:

| Behavior | Verified |
|---|---|
| `autoRemove: false` + `stop()` keeps the registry record (`stopped`) and the disks — the box is dormant, not dead | ✅ |
| A dormant box restarts via `runtime.get(name).start()` **or** implicitly via any `exec`; same box id | ✅ |
| Rootfs state (`/root/...`) survives the stop/restart cycle; `/tmp` (tmpfs) does not | ✅ |
| The recorded entrypoint re-runs on every boot — restart restarts the *workload*, not just the VM | ✅ |
| `autoRemove: true` + `stop()` removes record and files — stop is terminal | ✅ |
| A dormant box costs ~45 MiB on disk — almost all per-box copies of `boxlite-shim` + `libkrunfw`; the state disks were <1.5 MiB | ✅ |

Two corollaries: options passed to a `reuseExisting` box are **silently ignored** (a spec
change is a recreate, never a reuse), and `create`/`getId()` alone does *not* boot the VM —
the record sits at status `configured` until the first `exec` or an explicit `start()`.

### 6. Run-to-completion entrypoints zombie the box (`batch-workload-test.ts`)

Batch images (entrypoint does its work and exits — e.g. a db migration) boot reliably,
including instant-exit entrypoints. But completion is invisible:

- After the entrypoint exits, the VM stays up and `getInfo` reports `running: true`
  **indefinitely** — the box never transitions.
- In that zombie state `exec` fails with `spawn_failed: Container init process exited —
  cannot exec ... container status: 'Stopped'` — currently the only external completion
  signal.
- The exit code is not exposed by the API (an `exit.previous` file is written inside the
  box dir).

So completion detection and cleanup are the host's job, and the VM holds its memory until
`stop()`. For observable batch semantics, boot with an idle entrypoint (`sleep infinity`)
and run the work via `exec` — the promise resolves on completion with the exit code and
captured output. A feature request for surfacing container exit is filed upstream.

### 7. The entrypoint is opaque; exec is fully observable (`log-capture-gaps-test.ts`, `console-log-test.ts`)

- Entrypoint stdout/stderr goes nowhere host-visible. `boxes/<id>/logs/console.log`
  (0.9+) holds kernel/guest-agent output only. This is why kinotic workloads must write
  log files to the mounted `/var/log/kinotic` instead — and why a stdio-capture feature
  request is filed upstream.
- `exec` is a separate channel through the guest agent (`docker exec` equivalent): runs
  alongside the entrypoint, streams stdout/stderr, returns `{ exitCode, stdout, stderr }`,
  and does **not** throw on nonzero exit.
- `entrypoint` *replaces* the image ENTRYPOINT; `cmd` *appends to* the image entrypoint.

### 8. SDK sharp edges

- `SimpleBox` is lazy: before the first `exec`/`getId()`, `box.id` throws and `stop()`
  **silently no-ops** — force attachment with `getId()` before trusting a handle.
- `exec` boots a non-running box as a side effect; inspect with `runtime.getInfo(name)`
  to avoid booting.
- `JsBoxlite.withDefaultConfig()` hard-aborts the whole process (uncatchable Rust panic)
  on hosts without virtualization (`/dev/kvm` on Linux).
- Each `SimpleBox` creates its own runtime instance unless one is passed via
  `options.runtime`.
- Port mapping `protocol` matches lowercase `"udp"` only; any other value silently means
  tcp.
- `autoRemove: true` + `detach: true` is rejected at creation ("Detached boxes should use
  auto_remove=false for manual lifecycle control") — auto-remove semantics for detached
  boxes must be implemented by the caller.
- Overriding `entrypoint` without `cmd` still appends the image's CMD (Docker semantics):
  `entrypoint: ["sleep", "600"]` on alpine runs `sleep 600 /bin/sh`. Pass `cmd: []` to
  suppress the image CMD.

### 9. A third volume mount exhausts the VM's IRQs (`disk-quota-test.ts`, `boot-failure-test.ts`)

Sweeping volume count with plain host directories, nothing else varying:

```
  1 volume(s): STARTED   guest sees: /v0 /var
  2 volume(s): STARTED   guest sees: /v0 /v1 /var
  3 volume(s): FAILED    ... VM failed to start (libkrun status=-22)
  4 volume(s): FAILED    ... VM failed to start (libkrun status=-22)
```

`status=-22` is the surface; the cause is in the shim stderr, which the SDK error carries:

```
[krun] krun_start_enter called
[krun] ERROR krun] Building the microVM failed: RegisterNetDevice(IrqsExhausted)
[krun] krun_start_enter returned (status=-22, elapsed=43ms)
```

So the limit is the VM's IRQ budget, not a volume cap — each virtio-fs mount consumes an
IRQ and the net device, registered last, is the one that runs out and reports. What does
*not* draw on it, swept in `boot-failure-test.ts`: ports (`0 volumes + 4 ports` boots,
`2 volumes + 3 ports` boots) and a sized rootfs (`2 volumes + diskSizeGb` boots, and
`3 volumes + diskSizeGb` fails exactly where plain `3 volumes` does).

**Design implication:** every kinotic workload already carries the `/var/log/kinotic`
mount, so a workload may declare exactly **one** volume of its own. `buildBoxOptions`
rejects more than that up front rather than letting the VM fail to boot.

### 10. Network policy is a real egress control, and open by default (`network-policy-test.ts`)

Verified on Linux/KVM against 0.9.7:

| Policy | Result |
|---|---|
| omitted entirely | every host reachable — identical to `enabled` |
| `{ mode: 'enabled' }` | every host reachable; no allowlist means unrestricted |
| `{ mode: 'enabled', allowNet: [] }` | every host reachable — see finding #13 |
| `{ mode: 'enabled', allowNet: ['example.com'] }` | listed host reachable; unlisted host blocked **by name and by raw IP** |
| `{ mode: 'disabled' }` | **cannot boot** — see finding #12 |
| `{ mode: 'disabled', allowNet: [...] }` | rejected at config validation: *"network.mode=\"disabled\" is incompatible with allow_net"* |

A blocked name resolves to `0.0.0.0` inside the guest, and connecting to the unlisted
host's literal IP fails too, so the allowlist is enforced on the connection rather than
only at DNS. There is no deny-by-default: a policy with an empty `allowNet` grants
unrestricted egress, so untrusted workloads must always carry a populated allowlist.

`mode: 'disabled'` failing to boot is finding #12.

### 11. A 1 GiB rootfs cap holds, and so does a project quota on a volume (`disk-quota-test.ts`)

`diskSizeGb: 1` gives the guest a 943.3M rootfs and stops writes at 930 MiB. An XFS project
quota on a bind-mounted host directory holds too, despite the writes being performed on the
host by virtiofsd rather than by the guest kernel:

```
  guest df /capped      : uservol0    64.0M    0    64.0M   0% /capped
  bytes actually landed : 64.0 MiB (limit 64 MiB)
  host quota report     : #4242    65536    0    65536
```

The guest's `df` reports the quota rather than the underlying filesystem, so a workload can
see its own limit. `dd` exits 1 in both cases, so a workload can detect that it was capped from the exit
status alone.

**This holds at `diskSizeGb: 1` and nowhere above it — see finding #15.**

**Quota accounting has no measurable write cost in this data.** Two hosts disagreed on the
direction, which is the answer: the spread is first-write warm-up and host I/O variance, not
accounting.

```
host 1   round 1 with quota  56.4 MiB/s   round 2 with quota   98.3 MiB/s
         round 1 no quota   113.0 MiB/s   round 2 no quota    112.9 MiB/s
host 2   round 1 with quota  29.8 MiB/s   round 2 with quota  103.1 MiB/s
         round 1 no quota    60.3 MiB/s   round 2 no quota     47.5 MiB/s   <- slower than quota'd
```

Earlier readings of 36% and then 13% were both artifacts of an unreplicated ordered pair.
Nothing here argues against a project quota on cost grounds.

### 12. `network: { mode: 'disabled' }` cannot boot a VM (`boot-failure-test.ts`)

Every attempt to boot with the network disabled dies the same way, with the image's own
entrypoint or an override, with or without an allowlist alongside:

```
  mode enabled                           STARTED
  mode disabled                          FAILED
      | Exit code: 159 (unknown signal)
      | Console output: empty (no kernel or guest messages captured)
      | [shim] T+4ms: instance created (krun FFI calls done)
      | [shim] T+4ms: entering VM (krun_start_enter)      <- last line; no krun error follows
```

It fails differently from finding #9: no `gvproxy created` line, no libkrun error, an empty
console, and the shim dies on a signal at VM entry rather than failing device registration.
Pairing the mode with an allowlist is caught earlier still, at config validation:
*"network.mode=\"disabled\" is incompatible with allow_net"*.

**Design implication:** `mode: 'disabled'` is unusable. `NetworkMode.DISABLED` is carried
by an allowlist instead — see finding #14.

### 13. An empty `allowNet` grants everything, it does not deny everything (`network-policy-test.ts`)

`{ mode: 'enabled', allowNet: [] }` is byte-identical to omitting the allowlist and to
omitting the network option entirely — all three reach every target, raw IP included:

```
=== F. mode 'enabled', allowNet: [] ===
  dns:cloudflare.com    REACHED  exit=0  Address: 104.16.133.229   <- the real record,
  http://example.com    REACHED  exit=0                               not the 0.0.0.0
  http://cloudflare.com REACHED  exit=0                               sinkhole a populated
  http://1.1.1.1        REACHED  exit=0                               list produces
```

An empty list means "no allowlist configured", not "permit nothing". **Design implication:**
a policy that computes down to an empty `allowedHosts` silently grants unrestricted egress —
the opposite of the likely intent — so whatever builds a policy for untrusted code must
treat an empty list as a bug rather than as a denial. boxlite will not catch it, and neither
mode can: `disabled` does not boot (#12).

### 14. An allowlist naming only an unreachable address is a working no-egress policy (`network-policy-test.ts`)

A populated allowlist is enforced (#10) and an empty one is not (#13), so permitting exactly
one address that nothing answers on denies everything. Both forms boot and both deny:

```
=== G. mode 'enabled', allowNet ['192.0.2.1'] — an address nothing answers on ===
  http://example.com     blocked  exit=1  wget: can't connect to remote host (0.0.0.0): Connection refused
  http://cloudflare.com  blocked  exit=1  wget: can't connect to remote host (0.0.0.0): Connection refused
  http://1.1.1.1         blocked  exit=1  wget: can't connect to remote host (1.1.1.1): Connection refused

=== H. mode 'enabled', allowNet ['no-egress.invalid'] — a name that cannot resolve ===
  http://1.1.1.1         blocked  exit=1  wget: error getting response
```

TEST-NET-1 (`192.0.2.1`, RFC 5737) is the better of the two: it refuses uniformly with
`Connection refused`, and unlike a `.invalid` name it does not depend on how the guest's
resolver handles a reserved TLD. Note the raw IP in G is refused at its own address rather
than sinkholed to `0.0.0.0`, confirming the filter runs on the connection even where no DNS
step exists.

**Design implication:** this is how `NetworkMode.DISABLED` is implemented — `buildBoxOptions`
sends `{ mode: 'enabled', allowNet: ['192.0.2.1'] }` and discards any allowlist the workload
declared. Revisit once boxlite can boot a genuinely disabled network.

### 15. A rootfs above 1 GiB is reported to the guest but never allocated (`repro-disk-size.ts`)

Sweeping `diskSizeGb` with an identical 1536 MiB write in each box, on two separate hosts:

| diskSizeGb | guest `df /` | VM alive after write | file reports | host box dir |
|---|---|---|:---:|---|
| 1 | 943.3M | YES | 930 MiB of 1536 | 978 MiB |
| 2 | 1.9G | YES | 1536 MiB of 1536 | **1071 MiB** |
| 4 | 3.7G | NO — died mid-write | (box died first) | **1071 MiB** |
| 8 | 7.5G | NO — died mid-write | 1536 MiB of 1536 | **1071 MiB** |

The backing store stops at ~1071 MiB across a 4× spread in declared size, while the guest's
`df` scales correctly to 1.9G/3.7G/7.5G. Only `diskSizeGb: 1` comes in lower, because its own
930 MiB cap binds before the ceiling does.

Two things make this worse than a cap. The guest is told it has room that was never
allocated. And at sizes 2 and 8 the file's own size reads back as the full 1536 MiB while the
host directory only ever grew to 1071 MiB — `dd` did exit 1, so the writer was told, but
anything that later reads that file sees a full-length file whose contents are not all there.

Failure above the ceiling is not one behaviour but three: `I/O error` with the box surviving
(2 GiB), the box dying mid-write with no message (4 GiB), and the guest remounting its
filesystem read-only before the box dies (8 GiB). Both deaths report the same single line,
with no shim or krun trace, because the box booted successfully and died later during exec:

```
Error: internal error: spawn_failed: internal error: build failed: failed to execute workload
```

**Design implication:** `buildBoxOptions` refuses a workload declaring more than 1024MB.
Every larger size hands the workload a disk that silently swallows writes, which is worse
than refusing to run it.

---

### 16. An allowlist naming a CNAME chain's head permits the chain; wildcards and bare addresses do not (`network-policy-test.ts`)

`github.com` and `registry.npmjs.org` resolve directly, but an Azure storage account host is
a CNAME chain — `<account>.blob.core.windows.net` → `blob.<cluster>.store.core.windows.net`
→ address — so the question was whether an allowlist must name every link, or whether boxlite
attributes the answer to the canonical name and refuses the head. Verified on 0.10.0, whose
shim log names the mechanism (`allowNet: DNS sinkhole configured`, `allowNet TCP: handler
overridden with SNI-inspecting forwarder`):

```
=== I. mode 'enabled', allowNet ['kin00aca0a5a87cfeb0a1f11.blob.core.windows.net'] — the head of the CNAME chain only ===
  dns:kin00aca0a5a87cfeb0a1f11.blob.core.windows.net REACHED  exit=0  Name:	kin00aca0a5a87cfeb0a1f11.blob.core.windows.net | Address: 57.150.160.33
  https://kin00aca0a5a87cfeb0a1f11.blob.core.windows.net/ REACHED  exit=1  wget: server returned error: HTTP/1.1 400 Value for one of the query parameters specified in the request URI is invalid.
  https://blob.dsm41prdstr11a.store.core.windows.net/ blocked  exit=1  wget: can't connect to remote host (0.0.0.0): Connection refused

=== I. mode 'enabled', allowNet ['kin00aca0a5a87cfeb0a1f11.blob.core.windows.net', 'blob.dsm41prdstr11a.store.core.windows.net'] — the whole chain ===
  dns:kin00aca0a5a87cfeb0a1f11.blob.core.windows.net REACHED  exit=0  Non-authoritative answer:
  https://kin00aca0a5a87cfeb0a1f11.blob.core.windows.net/ REACHED  exit=1  wget: server returned error: HTTP/1.1 400 Value for one of the query parameters specified in the request URI is invalid.
  https://blob.dsm41prdstr11a.store.core.windows.net/ REACHED  exit=1  wget: server returned error: HTTP/1.1 400 The requested URI does not represent any resource on the server.

=== I. mode 'enabled', allowNet ['*.blob.core.windows.net'] — a wildcard ===
  dns:kin00aca0a5a87cfeb0a1f11.blob.core.windows.net blocked  exit=1  Non-authoritative answer: | *** Can't find kin00aca0a5a87cfeb0a1f11.blob.core.windows.net: Parse error
  https://kin00aca0a5a87cfeb0a1f11.blob.core.windows.net/ blocked  exit=1  wget: bad address 'kin00aca0a5a87cfeb0a1f11.blob.core.windows.net'
  https://blob.dsm41prdstr11a.store.core.windows.net/ blocked  exit=1  wget: can't connect to remote host (0.0.0.0): Connection refused

=== I. mode 'enabled', allowNet ['57.150.160.33'] — the address alone ===
  dns:kin00aca0a5a87cfeb0a1f11.blob.core.windows.net REACHED  exit=0  Non-authoritative answer:
  https://kin00aca0a5a87cfeb0a1f11.blob.core.windows.net/ blocked  exit=1  wget: can't connect to remote host (0.0.0.0): Connection refused
  https://blob.dsm41prdstr11a.store.core.windows.net/ blocked  exit=1  wget: can't connect to remote host (0.0.0.0): Connection refused
```

Naming the head alone is enough: the guest resolves it to the real address and the TLS
connection completes (Azure's `400` on `GET /` is the reply of a reached server; busybox
`wget` exits non-zero on it, which is why the verdict reads the stderr line). The canonical
name is reachable only when it is listed itself, so what the guest can connect to is decided
by the name it asked for — the one carried in the TLS SNI — rather than by the answer's
canonical name. A wildcard is not understood: `*.blob.core.windows.net` breaks name
resolution for the account host outright (`Parse error`), and it reaches nothing. The bare
address lets the name resolve but the connection is still sinkholed to `0.0.0.0`, so an
address entry does not stand in for the name it resolves from.

**Design implication:** a publish workload's allowlist names exactly the host of the URL it
uploads to, as the head of the chain, and nothing more; resolving the CNAME chain at deploy
time would add nothing. Wildcards are not an option on boxlite, and neither is the address.

Two 0.10.0 changes the probe had to absorb: `exec` no longer boots a configured box
(`Cannot exec box …: it is configured, and starting the box would run its main command`),
so a box is started explicitly with `runtime.get(name).start()` before the first exec; and
the legacy `network: { mode, allowNet }` shape is still accepted as an alias of
`network.outbound`. The other scripts in `src/` were written against implicit boot and were
not re-run on 0.10.0.

---

## Scripts (`src/`)

| File | Purpose | Leaves a box running? |
|------|---------|:---:|
| `index.ts` + `app.ts` | Main demo: boot `oven/bun:latest`, inject `app.ts` as the guest payload, serve on port 3000 until Ctrl+C. | no |
| `index-test.ts` | Smoke / latency test: cold VM launch time + repeated `echo`s on `alpine`. | no |
| `connect-test.ts` | Reuse / reconnect test (finding #2). Run it twice. | **yes** (`alpine-persist-test`) |
| `detach-test.ts` | Detach bug repro (finding #1). | **yes** (`detach-http-test`) |
| `volume-share-test.ts` | Two-box shared volume with `bun --watch` — shows the inotify limitation (finding #3). | self-cleaning |
| `volume-poll-test.ts` | Same setup with chokidar `{ usePolling }` (recursive, nested file) — shows the fix works. | self-cleaning |
| `volume-host-notify-test.ts` | **Guest→host** notify (finding #4): a box writes to a host-mounted volume while the host process watches via `fs.watch` (event) vs `fs.watchFile` (poll). Decides whether host-side Alloy can use inotify or must poll. | self-cleaning |
| `autoremove-restart-test.ts` | Stop/restart lifecycle (finding #5): what `autoRemove` keeps or removes, restart via `start()` vs implicit exec-boot, rootfs persistence, entrypoint re-run. | self-cleaning |
| `batch-workload-test.ts` | Run-to-completion semantics (finding #6): the post-entrypoint zombie state, exec failure as the only completion signal, instant-exit boots. | self-cleaning |
| `console-log-test.ts` | Does `boxes/<id>/logs/console.log` capture entrypoint stdout/stderr live? (finding #7 — it does not; kernel/guest-agent output only.) | self-cleaning |
| `console-output-discovery-test.ts` | Sweeps `$BOXLITE_HOME` for any file that receives entrypoint output. | self-cleaning |
| `log-capture-gaps-test.ts` | Demonstrates every entrypoint/exec output-capture gap in one run (basis of the upstream stdio-capture feature request). | self-cleaning |
| `network-policy-test.ts` | What `network: { mode, allowNet }` actually enforces: whether `disabled` blocks egress, whether an allowlist blocks unlisted hosts, whether it can be bypassed by connecting to a raw IP, and how a CNAME chain is matched (finding #16). Needs ordinary outbound internet. An optional label prefix runs a subset: `bun run src/network-policy-test.ts azure`. | self-cleaning |
| `disk-quota-test.ts` | Whether a workload's disk can be bounded: `diskSizeGb` as a rootfs cap and whether a rootfs above 1 GiB survives being filled (phases A and D, run anywhere), and an XFS project quota on a bind-mounted host directory as a volume cap, including the write cost of the accounting (phases B and C, need Linux + root + `xfsprogs`). | self-cleaning |
| `repro-disk-size.ts` | Standalone reproducer, free of any dependency but the SDK, sweeping `diskSizeGb` 1/2/4/8 with the same 1536 MiB write in each to show whether a fixed ceiling applies regardless of the disk requested. Written to be handed to the boxlite maintainers as-is. | self-cleaning |
| `boot-failure-test.ts` | Isolates the two generic "failed to start" errors behind findings #9 and #10 — whether `mode: 'disabled'` is bootable at all, and whether the volume ceiling counts volumes only or a shared device budget that ports and a sized rootfs also draw on. Dumps the full error, which carries the shim trace. | self-cleaning |

The last two probes have no findings recorded above yet — they exist to answer questions the
vm-manager currently depends on. Run them on a Linux host with virtualization and add what
they report to the findings list.

Note: `node/` is a separate mini-project running the smoke test under **Node.js**
(`node --experimental-strip-types`) to confirm cross-runtime support.

## Cleanup

`connect-test.ts` and `detach-test.ts` intentionally leave detached boxes running
(and holding their ports). Remove them by name when done:

```bash
bun -e "import { JsBoxlite } from '@boxlite-ai/boxlite'; \
  const r = JsBoxlite.withDefaultConfig(); \
  await r.remove('detach-http-test', true); \
  await r.remove('alpine-persist-test', true);"

# list everything currently running:
bun -e "import { JsBoxlite } from '@boxlite-ai/boxlite'; \
  const r = JsBoxlite.withDefaultConfig(); \
  console.log(JSON.stringify(await r.listInfo(), null, 2));"
```
