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
