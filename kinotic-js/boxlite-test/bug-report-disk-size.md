# Additional data for the disk-size ceiling

Written to be added as a comment on the existing upstream issue about `RLIMIT_FSIZE` being
fixed at 1 GiB regardless of the disk size accepted in the same options. It is not a new
report — it adds a sweep the original does not have, a reproducer with no dependency but the
SDK, and two consequences that look worse than a killed VM.

The reproducer is inlined in full at the bottom. It needs only `@boxlite-ai/boxlite` and Bun,
boots one box per size, writes the same payload into each, and removes every box afterwards.

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

---

## Reproducer

Save as `repro-disk-size.ts` and run it. The only dependency is the SDK:

```bash
bun add @boxlite-ai/boxlite
bun run repro-disk-size.ts
```

Needs virtualization (KVM or Hypervisor.framework) and no privileges. Peak disk use is about
1.5 GiB, one box at a time, and every box is removed on the way out.

```typescript
import { SimpleBox, getJsBoxlite } from "@boxlite-ai/boxlite";
import { existsSync, readdirSync, statSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

// Standalone reproducer for a box whose disk is smaller than the size it was created with.
// The same write is attempted at several values of diskSizeGb, varying nothing else, so the
// boundary is visible rather than inferred. diskSizeGb 1 is the control: its filesystem
// fills at ~940 MiB, which looks healthy and is what hides the behaviour at larger sizes.
//
// For each size it reports what the guest was told, how much data landed, whether the VM
// still answers afterwards, and how large the box directory grew on the host. That last
// figure is the one that matters — it is what was actually allocated, as opposed to what
// the guest's df claims. A VM that merely filled its disk keeps answering exec.
//
// Deliberately free of any dependency but @boxlite-ai/boxlite, so it runs anywhere the SDK
// does:
//
//   bun add @boxlite-ai/boxlite && bun run repro-disk-size.ts
//
// Needs virtualization (KVM or Hypervisor.framework) and no privileges. Every box is
// removed in cleanup. Peak disk use is about 1.5 GiB, one box at a time.

const RUN = Date.now().toString(36);
const runtime = getJsBoxlite().withDefaultConfig();
const BOXLITE_HOME = process.env.BOXLITE_HOME ?? join(homedir(), ".boxlite");

/** Disk sizes to sweep, in GB. 1 is the control: its filesystem fills before the ceiling. */
const DISK_SIZES_GB = [1, 2, 4, 8];
/** Written into every box, chosen to pass 1 GiB without filling any disk above 1 GB. */
const WRITE_MB = 1536;

function boxDirBytes(boxId: string): string {
    const dir = join(BOXLITE_HOME, "boxes", boxId);
    if (!existsSync(dir)) {
        return "(removed)";
    }
    let total = 0;
    const walk = (path: string) => {
        for (const name of readdirSync(path)) {
            const child = join(path, name);
            const stat = statSync(child);
            if (stat.isDirectory()) {
                walk(child);
            } else {
                total += stat.size;
            }
        }
    };
    try {
        walk(dir);
    } catch {
        // the directory can be removed underneath the walk when the box dies
        return "(removed mid-read)";
    }
    return `${(total / 1024 / 1024).toFixed(0)} MiB`;
}

async function removeIfPresent(name: string) {
    try {
        if (await runtime.getInfo(name)) {
            await runtime.remove(name, true);
        }
    } catch {
        // a box whose monitor died may have no record left to remove
    }
}

async function attempt(diskSizeGb: number): Promise<void> {
    console.log(`--- diskSizeGb: ${diskSizeGb} ---`);
    const name = `repro-disk-${RUN}-${diskSizeGb}`;
    let boxId = "";
    try {
        const box = new SimpleBox({
            image: "alpine:latest",
            name,
            runtime,
            autoRemove: false,
            diskSizeGb,
            entrypoint: ["sleep", "900"],
            cmd: [],
        });
        boxId = await box.getId();

        const df = await box.exec("sh", "-c", "df -h / | tail -n 1");
        console.log(`  guest df /           : ${df.stdout.trim()}`);

        // dd's status is echoed rather than read from the exec: a shell reports the status
        // of the last command in a pipeline, so piping dd into tail would always look
        // successful. conv=fsync so the failure is not deferred past dd's own exit
        const write = await box.exec("sh", "-c",
            `dd if=/dev/zero of=/root/fill bs=1M count=${WRITE_MB} conv=fsync 2>/tmp/dd.err; `
            + `echo "dd-exit=$?"; tail -n 2 /tmp/dd.err`);
        const ddExit = write.stdout.match(/dd-exit=(\d+)/)?.[1] ?? "(not reported)";
        console.log(`  dd exit              : ${ddExit}`);
        console.log(`  dd said              : ${write.stdout.replace(/dd-exit=\d+/, "").trim().split("\n").filter(Boolean).pop() ?? ""}`);

        const size = await box.exec("sh", "-c", "ls -l /root/fill 2>/dev/null | awk '{print $5}'");
        const landed = Number(size.stdout.trim() || 0);
        console.log(`  bytes landed         : ${(landed / 1024 / 1024).toFixed(0)} MiB of ${WRITE_MB} MiB`);

        const ping = await box.exec("sh", "-c", "echo alive");
        console.log(`  VM alive after write : ${ping.stdout.includes("alive") ? "YES" : "NO"}`);
    } catch (error) {
        // The whole error is printed: the shim trace and exit status live inside its message
        console.log(String(error).split("\n").map(line => `  | ${line}`).join("\n"));
        console.log(`  VM alive after write : NO — the box stopped answering`);
    } finally {
        if (boxId) {
            console.log(`  host box dir         : ${boxDirBytes(boxId)}`);
        }
        await removeIfPresent(name);
        console.log();
    }
}

async function main() {
    const boxliteVersion = (await import("@boxlite-ai/boxlite/package.json")).default.version as string;
    console.log(`boxlite version : ${boxliteVersion}`);
    console.log(`Platform        : ${process.platform} (${process.arch})  runtime: Bun ${Bun.version}`);
    console.log(`Writing ${WRITE_MB} MiB into each box; only diskSizeGb varies\n`);

    for (const diskSizeGb of DISK_SIZES_GB) {
        await attempt(diskSizeGb);
    }

    console.log("Compare the host box dir figures across the sizes. If they are equal while");
    console.log("the guest's df scales, the backing store stopped at a fixed point that the");
    console.log("guest was never told about, and what each box did on hitting it is secondary.");
}

main().catch((error) => {
    console.error("REPRO FAILED:", error);
    process.exit(1);
});
```
