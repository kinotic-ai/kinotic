# boxlite-test

Evaluation harness for [BoxLite](https://docs.boxlite.ai/) — a set of standalone
scripts probing detach behavior, named-box reuse, and shared-volume semantics.

**Verified against:** `@boxlite-ai/boxlite@0.9.5` (findings 1–4) and `0.9.7`
(findings 5–8), Bun 1.3.10, macOS arm64 (`@boxlite-ai/boxlite-darwin-arm64`). Findings
9–11 were verified on 0.9.7 under Bun 1.3.14 on an Azure `Standard_D4s_v3`
(Ubuntu 22.04, kernel 6.8, KVM), since they need nested virtualization and XFS. The
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

### 9. A box cannot start with three or more volume mounts (`disk-quota-test.ts`)

Sweeping volume count with plain host directories, nothing else varying:

```
  1 volume(s): STARTED   guest sees: /v0 /var
  2 volume(s): STARTED   guest sees: /v0 /v1 /var
  3 volume(s): FAILED    ... VM failed to start (libkrun status=-22)
  4 volume(s): FAILED    ... VM failed to start (libkrun status=-22)
```

The failure is an opaque `libkrun status=-22` at boot, with no indication that the volume
count caused it. **Design implication:** every kinotic workload already carries the
`/var/log/kinotic` mount, so a workload may declare exactly **one** volume of its own.
`buildBoxOptions` rejects more than that up front rather than letting the VM fail to boot.

### 10. Network policy is a real egress control, and open by default (`network-policy-test.ts`)

Verified on Linux/KVM against 0.9.7:

| Policy | Result |
|---|---|
| omitted entirely | every host reachable — identical to `enabled` |
| `{ mode: 'enabled' }` | every host reachable; no allowlist means unrestricted |
| `{ mode: 'enabled', allowNet: ['example.com'] }` | listed host reachable; unlisted host blocked **by name and by raw IP** |
| `{ mode: 'disabled' }` | **unverified** — the box failed to start |

A blocked name resolves to `0.0.0.0` inside the guest, and connecting to the unlisted
host's literal IP fails too, so the allowlist is enforced on the connection rather than
only at DNS. There is no deny-by-default: a policy with an empty `allowNet` grants
unrestricted egress, so untrusted workloads must always carry a populated allowlist.

`mode: 'disabled'` failing to boot is unexplained and needs its own probe before anything
relies on it.

### 11. Disk caps hold, and the guest is not told it hit them (`disk-quota-test.ts`)

`diskSizeGb: 1` gives the guest a 943.3M rootfs and stops writes at 930 MiB. An XFS project
quota on a bind-mounted host directory holds too, despite the writes being performed on the
host by virtiofsd rather than by the guest kernel:

```
  guest df /capped      : uservol0    64.0M    0    64.0M   0% /capped
  bytes actually landed : 64.0 MiB (limit 64 MiB)
  host quota report     : #4242    65536    0    65536
```

The guest's `df` reports the quota rather than the underlying filesystem, so a workload can
see its own limit. Quota accounting cost ~36% of write throughput on a loopback XFS
(61.0 vs 95.1 MiB/s, single unreplicated pair) — worth re-measuring on a real data disk,
which is why phase C now alternates and repeats the writes.

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
| `network-policy-test.ts` | What `network: { mode, allowNet }` actually enforces: whether `disabled` blocks egress, whether an allowlist blocks unlisted hosts, and whether it can be bypassed by connecting to a raw IP. Needs ordinary outbound internet. | self-cleaning |
| `disk-quota-test.ts` | Whether a workload's disk can be bounded: `diskSizeGb` as a rootfs cap (phase A, runs anywhere), and an XFS project quota on a bind-mounted host directory as a volume cap, including the write cost of the accounting (phases B and C, need Linux + root + `xfsprogs`). | self-cleaning |
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
