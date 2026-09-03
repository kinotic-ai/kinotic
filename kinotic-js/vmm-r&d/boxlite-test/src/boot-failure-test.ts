import { SimpleBox, getJsBoxlite, type SimpleBoxOptions } from "@boxlite-ai/boxlite";
import { existsSync, mkdtempSync, readFileSync, readdirSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { join } from "node:path";

// Two boot failures turned up on a KVM host, both reported only as a generic "failed to
// start". This probe isolates each by varying one option at a time against a box that
// otherwise boots, and dumps the WHOLE error — the shim's trace is carried inside the
// error message, and earlier probes truncated it to the first line.
//
//  Phase 1 — network mode. A box with network: { mode: 'disabled' } failed to start while
//    'enabled' and an omitted policy both booted. The vm-manager offers DISABLED through
//    Workload.network, so if the mode itself is unbootable that option is a trap. Varies
//    the policy alone, then repeats without the entrypoint override in case the two
//    interact.
//
//  Phase 2 — how many devices a box may have. Three volumes fail with libkrun status=-22
//    while two boot. Whether that ceiling counts volumes only, or some shared device
//    budget, decides what a workload may declare: the vm-manager already spends one volume
//    on the log mount, and workloads also map ports. Sweeps volumes alone, volumes with a
//    port, ports alone, and volumes with a sized rootfs.
//
// Needs virtualization (KVM or Hypervisor.framework) and no privileges. All boxes are
// removed in cleanup; the host directories are plain temp dirs.

const RUN = Date.now().toString(36);
const runtime = getJsBoxlite().withDefaultConfig();
const BOXLITE_HOME = process.env.BOXLITE_HOME ?? join(homedir(), ".boxlite");
const hostDirs = Array.from({ length: 4 }, () => mkdtempSync(join(tmpdir(), `bootvol-${RUN}-`)));

interface Attempt {
    label: string;
    started: boolean;
    /** Full error text, which is where the shim trace lives. */
    error: string;
    /** Anything readable from the box directory before boxlite removed it. */
    diagnostics: string;
}

async function removeIfPresent(name: string) {
    try {
        if (await runtime.getInfo(name)) {
            await runtime.remove(name, true);
        }
    } catch {
        // a box that never started may have no record to remove
    }
}

/**
 * Reads whatever boxlite left behind for a failed box. The id is recovered from the error
 * text because a box that fails to start never returns a handle to ask, and boxlite removes
 * the directory on failure, so this often finds nothing — which is itself worth reporting.
 */
function collectDiagnostics(error: string): string {
    const id = error.match(/Box ([A-Za-z0-9]+) failed/)?.[1];
    if (!id) {
        return "(no box id in the error)";
    }
    const boxDir = join(BOXLITE_HOME, "boxes", id);
    if (!existsSync(boxDir)) {
        return `(box dir ${boxDir} already removed)`;
    }
    const parts: string[] = [`box dir ${boxDir}: ${readdirSync(boxDir).join(" ")}`];
    for (const relative of ["shim.stderr", "logs/console.log"]) {
        const path = join(boxDir, relative);
        if (existsSync(path)) {
            parts.push(`--- ${relative} ---\n${readFileSync(path, "utf-8").trim()}`);
        }
    }
    return parts.join("\n");
}

/**
 * Creates a box with the given options and forces it to boot, reporting whether it came up.
 * The exec is what boots the VM (getId only writes the record), so it doubles as the proof
 * the guest is alive.
 */
async function attemptBoot(label: string, options: Partial<SimpleBoxOptions>): Promise<Attempt> {
    const name = `boot-${RUN}-${label.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}`;
    try {
        const box = new SimpleBox({
            image: "alpine:latest",
            name,
            runtime,
            autoRemove: false,
            ...options,
        });
        await box.getId();
        const alive = await box.exec("sh", "-c", "echo booted");
        return {
            label,
            started: alive.exitCode === 0 && alive.stdout.includes("booted"),
            error: "",
            diagnostics: "",
        };
    } catch (error) {
        const text = String(error);
        return { label, started: false, error: text, diagnostics: collectDiagnostics(text) };
    } finally {
        await removeIfPresent(name);
    }
}

function report(attempt: Attempt): void {
    console.log(`  ${attempt.label.padEnd(38)} ${attempt.started ? "STARTED" : "FAILED"}`);
    if (!attempt.started) {
        console.log(attempt.error.split("\n").map(line => `      | ${line}`).join("\n"));
        console.log(attempt.diagnostics.split("\n").map(line => `      > ${line}`).join("\n"));
    }
}

const IDLE = { entrypoint: ["sleep", "600"], cmd: [] as string[] };

async function phaseNetwork(): Promise<Map<string, boolean>> {
    console.log("=== Phase 1: which network policy can boot ===");
    const cases: Array<[string, Partial<SimpleBoxOptions>]> = [
        ["baseline, no network option", { ...IDLE }],
        ["mode enabled", { ...IDLE, network: { mode: "enabled" } }],
        ["mode disabled", { ...IDLE, network: { mode: "disabled" } }],
        ["mode disabled, image entrypoint", { network: { mode: "disabled" } }],
        ["mode disabled, allowNet listed", { ...IDLE, network: { mode: "disabled", allowNet: ["example.com"] } }],
    ];
    const outcomes = new Map<string, boolean>();
    for (const [label, options] of cases) {
        const attempt = await attemptBoot(label, options);
        outcomes.set(label, attempt.started);
        report(attempt);
    }
    console.log();
    return outcomes;
}

async function phaseDevices(): Promise<Map<string, boolean>> {
    console.log("=== Phase 2: how many devices a box may have ===");
    const volumes = (count: number) =>
        hostDirs.slice(0, count).map((hostPath, index) => ({ hostPath, guestPath: `/v${index}` }));
    // hostPort is left unset so boxlite assigns a free one and the sweep cannot fail on a
    // port that happens to be taken
    const ports = (count: number) => Array.from({ length: count }, (_, index) => ({ guestPort: 8000 + index }));

    const cases: Array<[string, Partial<SimpleBoxOptions>]> = [
        ["1 volume", { ...IDLE, volumes: volumes(1) }],
        ["2 volumes", { ...IDLE, volumes: volumes(2) }],
        ["3 volumes", { ...IDLE, volumes: volumes(3) }],
        ["2 volumes + 1 port", { ...IDLE, volumes: volumes(2), ports: ports(1) }],
        ["2 volumes + 3 ports", { ...IDLE, volumes: volumes(2), ports: ports(3) }],
        ["0 volumes + 4 ports", { ...IDLE, ports: ports(4) }],
        ["2 volumes + diskSizeGb", { ...IDLE, volumes: volumes(2), diskSizeGb: 1 }],
        ["3 volumes + diskSizeGb", { ...IDLE, volumes: volumes(3), diskSizeGb: 1 }],
    ];
    const outcomes = new Map<string, boolean>();
    for (const [label, options] of cases) {
        const attempt = await attemptBoot(label, options);
        outcomes.set(label, attempt.started);
        report(attempt);
    }
    console.log();
    return outcomes;
}

async function main() {
    const boxliteVersion = (await import("@boxlite-ai/boxlite/package.json")).default.version as string;
    console.log(`boxlite version : ${boxliteVersion}`);
    console.log(`Platform        : ${process.platform} (${process.arch})  runtime: Bun ${Bun.version}`);
    console.log(`BOXLITE_HOME    : ${BOXLITE_HOME}\n`);

    const network = await phaseNetwork();
    const devices = await phaseDevices();

    console.log("=== REPORT ===");
    const yn = (key: string, map: Map<string, boolean>) =>
        map.has(key) ? (map.get(key) ? "boots" : "FAILS") : "(not run)";

    console.log(`(a) mode 'disabled' boots at all:            ${yn("mode disabled", network)}`);
    console.log(`(b) ... with the image's own entrypoint:     ${yn("mode disabled, image entrypoint", network)}`);
    console.log(`(c) ... with an allowlist alongside:         ${yn("mode disabled, allowNet listed", network)}`);
    console.log(`(d) control — mode 'enabled':                ${yn("mode enabled", network)}`);
    console.log(`(e) 2 volumes / 3 volumes:                   ${yn("2 volumes", devices)} / ${yn("3 volumes", devices)}`);
    console.log(`(f) do ports share the volume budget:        2v+1p ${yn("2 volumes + 1 port", devices)}, 2v+3p ${yn("2 volumes + 3 ports", devices)}, 0v+4p ${yn("0 volumes + 4 ports", devices)}`);
    console.log(`(g) does a sized rootfs cost a slot:         2v ${yn("2 volumes + diskSizeGb", devices)}, 3v ${yn("3 volumes + diskSizeGb", devices)}`);
}

main().catch((error) => {
    console.error("PROBE FAILED:", error);
    process.exit(1);
});
