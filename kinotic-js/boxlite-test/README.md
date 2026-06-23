# boxlite-test

Evaluation harness for [BoxLite](https://docs.boxlite.ai/) — a set of standalone
scripts probing detach behavior, named-box reuse, and shared-volume semantics.

**Verified against:** `@boxlite-ai/boxlite@0.9.5`, Bun 1.3.10, macOS arm64
(`@boxlite-ai/boxlite-darwin-arm64`). The original detach bug was filed on
Linux/WSL2; findings here reproduce cross-platform.

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
