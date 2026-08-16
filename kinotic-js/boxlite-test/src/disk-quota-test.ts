import { SimpleBox, getJsBoxlite } from "@boxlite-ai/boxlite";
import { spawnSync } from "node:child_process";

// Can a kinotic node bound the disk a workload consumes? Two surfaces, two mechanisms:
//
//   rootfs  — SimpleBoxOptions.diskSizeGb, "Disk size in GB for container rootfs (sparse,
//             grows as needed)". The vm-manager now passes Workload.diskSizeMb through as
//             this, but whether it is a cap the guest cannot exceed is untested.
//   volumes — a bind mount has no size in the SDK at all, so anything the guest writes to
//             a mounted host directory lands on the host filesystem unbounded. Every
//             workload has at least one such mount (/var/log/kinotic), so this is not
//             limited to volumes we choose to hand out. The proposed cap is an XFS project
//             quota on the host directory, which is only meaningful if the quota survives
//             the trip through virtiofsd — the guest's writes are performed on the host by
//             that process, not by the guest kernel.
//
// Phases:
//  A. diskSizeGb — boot with a 1GB rootfs, ask the guest what df reports, then write past
//     it. Does the write fail, at what size, and with what error?
//  B. project quota — build a loopback XFS mounted with prjquota, put a 64MB hard limit on
//     one directory, mount it into a box, and write past the limit from inside the guest.
//     Does the write fail, what does the guest see, does the guest's df show the quota or
//     the host filesystem, and does the host-side quota report agree?
//  C. cost — write the same payload into a quota'd and a non-quota'd directory on that
//     same filesystem, to size the overhead of accounting on the write path.
//
// Phase A runs anywhere boxlite runs. B and C need Linux, root, and xfsprogs
// (apt-get install -y xfsprogs); they are skipped with a notice otherwise. The loopback
// image is self-contained — the host's own filesystem does not need to be XFS — and is
// unmounted and deleted in cleanup.

const RUN = Date.now().toString(36);
const runtime = getJsBoxlite().withDefaultConfig();

const IMAGE_PATH = `/tmp/kinotic-quota-${RUN}.img`;
const MOUNT_POINT = `/mnt/kinotic-quota-${RUN}`;
const CAPPED_PROJECT_ID = 4242;
const PERF_PROJECT_ID = 4243;
const CAPPED_LIMIT_MB = 64;
const PERF_PAYLOAD_MB = 256;

function sh(command: string, args: string[]): { ok: boolean; output: string } {
    const result = spawnSync(command, args, { encoding: "utf-8" });
    const output = `${result.stdout ?? ""}${result.stderr ?? ""}`.trim();
    return { ok: result.status === 0, output };
}

function requireOk(command: string, args: string[]): void {
    const result = sh(command, args);
    if (!result.ok) {
        throw new Error(`${command} ${args.join(" ")} failed: ${result.output}`);
    }
}

function have(tool: string): boolean {
    return sh("sh", ["-c", `command -v ${tool}`]).ok;
}

async function removeIfPresent(name: string) {
    try {
        if (await runtime.getInfo(name)) {
            await runtime.remove(name, true);
        }
    } catch (error) {
        console.error(`  cleanup of ${name} failed: ${error}`);
    }
}

/** First line of whichever stream the guest reported the failure on. */
function failureDetail(result: { stdout: string; stderr: string }): string {
    return (result.stderr.trim() || result.stdout.trim()).split("\n").filter(Boolean).pop() ?? "";
}

async function phaseA(): Promise<void> {
    console.log("=== Phase A: diskSizeGb as a rootfs cap ===");
    const name = `disk-${RUN}`;
    try {
        const box = new SimpleBox({
            image: "alpine:latest",
            name,
            runtime,
            autoRemove: false,
            diskSizeGb: 1,
            entrypoint: ["sleep", "600"],
            cmd: [],
        });
        await box.getId();

        const df = await box.exec("sh", "-c", "df -h / | tail -n 1");
        console.log(`  guest df /            : ${df.stdout.trim()}`);

        // Well past the 1GB rootfs; fsync so the failure is not hidden by page cache
        const fill = await box.exec("sh", "-c",
            "dd if=/dev/zero of=/root/fill bs=1M count=1500 conv=fsync 2>&1 | tail -n 2");
        const written = await box.exec("sh", "-c", "ls -l /root/fill 2>/dev/null | awk '{print $5}'");
        const bytes = Number(written.stdout.trim() || 0);

        console.log(`  dd exit               : ${fill.exitCode}`);
        console.log(`  dd said               : ${failureDetail(fill)}`);
        console.log(`  bytes actually landed : ${(bytes / 1024 / 1024).toFixed(0)} MiB`);
        console.log(`  => diskSizeGb caps the rootfs: ${bytes > 0 && bytes < 1400 * 1024 * 1024 ? "YES" : "NO — the guest wrote past it"}`);
    } finally {
        await removeIfPresent(name);
    }
    console.log();
}

function setUpQuotaFilesystem(): void {
    requireOk("truncate", ["-s", "4G", IMAGE_PATH]);
    requireOk("mkfs.xfs", ["-q", IMAGE_PATH]);
    requireOk("mkdir", ["-p", MOUNT_POINT]);
    requireOk("mount", ["-o", "loop,prjquota", IMAGE_PATH, MOUNT_POINT]);

    for (const dir of ["capped", "perf-quota", "perf-plain"]) {
        requireOk("mkdir", ["-p", `${MOUNT_POINT}/${dir}`]);
    }

    // project -s applies the id to the tree and sets the inherit flag, so files created
    // later by virtiofsd are accounted without any further work
    requireOk("xfs_quota", ["-x", "-c", `project -s -p ${MOUNT_POINT}/capped ${CAPPED_PROJECT_ID}`, MOUNT_POINT]);
    requireOk("xfs_quota", ["-x", "-c", `limit -p bhard=${CAPPED_LIMIT_MB}m ${CAPPED_PROJECT_ID}`, MOUNT_POINT]);
    requireOk("xfs_quota", ["-x", "-c", `project -s -p ${MOUNT_POINT}/perf-quota ${PERF_PROJECT_ID}`, MOUNT_POINT]);
    requireOk("xfs_quota", ["-x", "-c", `limit -p bhard=2g ${PERF_PROJECT_ID}`, MOUNT_POINT]);
}

function tearDownQuotaFilesystem(): void {
    sh("umount", [MOUNT_POINT]);
    sh("rmdir", [MOUNT_POINT]);
    sh("rm", ["-f", IMAGE_PATH]);
}

async function phaseBandC(): Promise<void> {
    console.log(`=== Phase B: XFS project quota (${CAPPED_LIMIT_MB}MB hard limit) through a volume mount ===`);
    const name = `quota-${RUN}`;
    try {
        const box = new SimpleBox({
            image: "alpine:latest",
            name,
            runtime,
            autoRemove: false,
            entrypoint: ["sleep", "900"],
            cmd: [],
            volumes: [
                { hostPath: `${MOUNT_POINT}/capped`, guestPath: "/capped" },
                { hostPath: `${MOUNT_POINT}/perf-quota`, guestPath: "/perf-quota" },
                { hostPath: `${MOUNT_POINT}/perf-plain`, guestPath: "/perf-plain" },
            ],
        });
        await box.getId();

        const df = await box.exec("sh", "-c", "df -h /capped | tail -n 1");
        console.log(`  guest df /capped      : ${df.stdout.trim()}`);
        console.log(`  (does it report the ${CAPPED_LIMIT_MB}M quota, or the whole 4G filesystem?)`);

        const overrun = await box.exec("sh", "-c",
            `dd if=/dev/zero of=/capped/fill bs=1M count=${CAPPED_LIMIT_MB * 2} conv=fsync 2>&1 | tail -n 2`);
        const written = await box.exec("sh", "-c", "ls -l /capped/fill 2>/dev/null | awk '{print $5}'");
        const bytes = Number(written.stdout.trim() || 0);

        console.log(`  dd exit               : ${overrun.exitCode}`);
        console.log(`  dd said               : ${failureDetail(overrun)}`);
        console.log(`  bytes actually landed : ${(bytes / 1024 / 1024).toFixed(1)} MiB (limit ${CAPPED_LIMIT_MB} MiB)`);

        const report = sh("xfs_quota", ["-x", "-c", "report -p -N -b", MOUNT_POINT]);
        console.log(`  host quota report     :\n${report.output.split("\n").map(l => `      ${l}`).join("\n")}`);
        const stopped = bytes > 0 && bytes <= CAPPED_LIMIT_MB * 1024 * 1024 * 1.05;
        console.log(`  => the quota stops guest writes: ${stopped ? "YES" : "NO — the guest wrote past the limit"}`);
        console.log();

        console.log(`=== Phase C: write cost of quota accounting (${PERF_PAYLOAD_MB}MB, same filesystem) ===`);
        for (const [label, guestPath] of [["with quota", "/perf-quota"], ["no quota", "/perf-plain"]] as const) {
            const started = Date.now();
            const result = await box.exec("sh", "-c",
                `dd if=/dev/zero of=${guestPath}/payload bs=1M count=${PERF_PAYLOAD_MB} conv=fsync 2>&1 | tail -n 1`);
            const seconds = (Date.now() - started) / 1000;
            console.log(`  ${label.padEnd(11)}: ${seconds.toFixed(2)}s  ${(PERF_PAYLOAD_MB / seconds).toFixed(1)} MiB/s  exit=${result.exitCode}`);
            console.log(`               dd: ${failureDetail(result)}`);
        }
    } finally {
        await removeIfPresent(name);
    }
    console.log();
}

async function main() {
    const boxliteVersion = (await import("@boxlite-ai/boxlite/package.json")).default.version as string;
    console.log(`boxlite version : ${boxliteVersion}`);
    console.log(`Platform        : ${process.platform} (${process.arch})  runtime: Bun ${Bun.version}\n`);

    await phaseA();

    const isRoot = process.getuid?.() === 0;
    const missing = ["mkfs.xfs", "xfs_quota"].filter(tool => !have(tool));
    if (process.platform !== "linux" || !isRoot || missing.length > 0) {
        console.log("=== Phases B and C skipped ===");
        console.log(`  platform : ${process.platform}${process.platform === "linux" ? "" : " (project quotas are Linux only)"}`);
        console.log(`  root     : ${isRoot ? "yes" : "no — mounting the loopback filesystem needs it"}`);
        console.log(`  tooling  : ${missing.length ? `missing ${missing.join(", ")} — apt-get install -y xfsprogs` : "present"}`);
        return;
    }

    try {
        setUpQuotaFilesystem();
        await phaseBandC();
    } finally {
        tearDownQuotaFilesystem();
    }
}

main().catch((error) => {
    console.error("PROBE FAILED:", error);
    tearDownQuotaFilesystem();
    process.exit(1);
});
