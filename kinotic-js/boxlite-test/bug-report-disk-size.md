# Additional data for the disk-size ceiling

Written to be added as a comment on the existing upstream issue about `RLIMIT_FSIZE` being
fixed at 1 GiB regardless of the disk size accepted in the same options. It is not a new
report — it adds a sweep the original does not have, a reproducer with no dependency but the
SDK, and two consequences that look worse than a killed VM.

Reproducer: [`src/repro-disk-size.ts`](./src/repro-disk-size.ts). It needs only
`@boxlite-ai/boxlite` and Bun, boots one box per size, writes the same payload into each, and
removes every box afterwards.

---

## Environment

- `@boxlite-ai/boxlite` 0.9.7, Bun 1.3.14
- Ubuntu 22.04, kernel 6.8.0-1064-azure, x86_64, KVM
- Azure `Standard_D4s_v3` (4 vCPU / 16 GiB), 62 GiB ext4 root filesystem with 59 GiB free

Reproduced identically on two separately provisioned hosts of the same shape, and
independently by a second script, so it is not an artifact of one machine or one harness.

## What was run

One box per `diskSizeGb`, nothing else varying, `alpine:latest`, entrypoint `sleep 900`:

```
dd if=/dev/zero of=/root/fill bs=1M count=1536 conv=fsync
```

1536 MiB passes 1 GiB without filling any disk above 1 GB. `dd`'s status is echoed rather
than piped, since a pipeline reports the status of its last command.

## Result

| diskSizeGb | guest `df /` | VM alive after write | file size afterwards | host box dir |
|---|---|---|:---:|---|
| 1 | 943.3M | YES | 930 MiB of 1536 | 978 MiB |
| 2 | 1.9G | YES | 1536 MiB of 1536 | **1071 MiB** |
| 4 | 3.7G | NO — died mid-write | (box died first) | **1071 MiB** |
| 8 | 7.5G | NO — died mid-write | 1536 MiB of 1536 | **1071 MiB** |

The host box directory stops at ~1071 MiB across a 4× spread in declared size. Only
`diskSizeGb: 1` comes in lower, because its own 930 MiB cap binds before the ceiling does.

## Two consequences worth separating from the crash

**The guest is told it has space that was never allocated.** `df` inside the guest scales
correctly to 1.9G / 3.7G / 7.5G. A workload sizing its own behaviour from `df` — a build
cache, a database, a log rotator — will plan against capacity that does not exist.

**A file can report a length whose bytes are not there.** At sizes 2 and 8 the file reads
back as the full 1536 MiB while the host directory only ever grew to 1071 MiB. `dd` did exit
1, so the writer was told, but any later reader sees a full-length file that is not fully
backed. This is the part that seems more serious than the VM dying: a crash is loud, and this
is not.

## Failure above the ceiling is three different behaviours

- **2 GiB** — `dd: /root/fill: I/O error`, box survives.
- **4 GiB** — no message from `dd` at all, box dies mid-write.
- **8 GiB** — `dd: /root/fill: Read-only file system`, the guest having remounted its
  filesystem read-only, then the box dies.

Both deaths surface the same single line, with no `[shim]` or `[krun]` trace, because the box
booted successfully and died later during exec:

```
Error: internal error: spawn_failed: internal error: build failed: failed to execute workload
```

## What would help most

Rejecting a `diskSizeGb` that cannot be honoured, at creation, would turn all of this into a
clear error. Failing that, a guest `df` that reports the size actually available would at
least let a workload plan against the truth.
