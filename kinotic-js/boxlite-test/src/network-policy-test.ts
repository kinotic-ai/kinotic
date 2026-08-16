import { SimpleBox, getJsBoxlite, type NetworkSpec } from "@boxlite-ai/boxlite";

// What does SimpleBoxOptions.network actually enforce? The kinotic vm-manager now sends a
// policy on every box (Workload.network -> { mode, allowNet }) so that what a guest can
// reach is decided by the workload record rather than a provider default. That code was
// written against the type declaration alone:
//
//   network?: { mode: "enabled" | "disabled"; allowNet?: string[] }   // simplebox.d.ts:62
//
// The declaration says "Outbound allowlist when network is enabled" and nothing more, so
// three things the vm-manager depends on are unverified. This probe answers them:
//
//  A. mode 'disabled'         — is egress actually blocked, and is DNS blocked with it?
//  B. mode 'enabled', no list — unrestricted, as the vm-manager assumes when a workload
//                               lists no allowed hosts?
//  C. mode 'enabled' + list   — is a listed host reachable and an unlisted one blocked?
//  D. allowNet bypass         — with a hostname allowlist in force, can the guest still
//                               reach an unlisted host by raw IP? If yes the allowlist is
//                               a DNS-level control, not an egress control, and it cannot
//                               be relied on for code we do not trust.
//  E. network omitted         — what the default is. The vm-manager always sends a policy
//                               so it never relies on this, but it decides whether the
//                               model's ENABLED default matches boxlite's.
//
// Requires ordinary outbound internet from the host. Every box is removed in cleanup.

const RUN = Date.now().toString(36);
const runtime = getJsBoxlite().withDefaultConfig();

// example.com and cloudflare.com resolve to unrelated networks, so an allowlist naming one
// cannot accidentally permit the other. 1.1.1.1 is cloudflare reached without any name.
const ALLOWED_HOST = "example.com";
const BLOCKED_HOST = "cloudflare.com";
const BLOCKED_IP = "1.1.1.1";

interface Probe {
    /** What the guest was asked to reach. */
    target: string;
    /** Exit code of the attempt inside the guest; 0 means it got through. */
    exitCode: number;
    /** First line of stderr, which is where busybox reports why it failed. */
    detail: string;
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

/**
 * Boots a box under the given network options and reports, for each target, whether the
 * guest could reach it. The entrypoint idles so the work runs through exec, whose exit
 * code and stderr are observable (unlike an entrypoint's, per finding #7).
 */
async function probeEgress(label: string, network: NetworkSpec | undefined): Promise<{ dns: Probe; targets: Probe[] }> {
    const name = `net-${RUN}-${label}`;
    try {
        const box = new SimpleBox({
            image: "alpine:latest",
            name,
            runtime,
            autoRemove: false,
            entrypoint: ["sleep", "600"],
            // finding #8: an entrypoint without cmd still appends the image CMD
            cmd: [],
            ...(network !== undefined ? { network } : {}),
        });
        await box.getId();

        // Separating name resolution from the fetch shows whether the allowlist is
        // enforced at DNS or on the connection itself
        const lookup = await box.exec("sh", "-c", `nslookup ${BLOCKED_HOST} 2>&1 | tail -n 3`);
        const dns: Probe = {
            target: `dns:${BLOCKED_HOST}`,
            exitCode: lookup.exitCode,
            detail: lookup.stdout.trim().split("\n").join(" | ") || lookup.stderr.trim().split("\n")[0] || "",
        };

        const targets: Probe[] = [];
        for (const target of [`http://${ALLOWED_HOST}`, `http://${BLOCKED_HOST}`, `http://${BLOCKED_IP}`]) {
            // -T bounds a silent drop, which is how a blocked connection usually presents
            const result = await box.exec("sh", "-c", `wget -T 8 -q -O /dev/null ${target}`);
            targets.push({
                target,
                exitCode: result.exitCode,
                detail: result.stderr.trim().split("\n")[0] || result.stdout.trim().split("\n")[0] || "",
            });
        }
        return { dns, targets };
    } finally {
        await removeIfPresent(name);
    }
}

function render(probe: Probe): string {
    const verdict = probe.exitCode === 0 ? "REACHED" : "blocked";
    return `${probe.target.padEnd(28)} ${verdict.padEnd(8)} exit=${probe.exitCode}${probe.detail ? `  ${probe.detail}` : ""}`;
}

async function main() {
    const boxliteVersion = (await import("@boxlite-ai/boxlite/package.json")).default.version as string;
    console.log(`boxlite version : ${boxliteVersion}`);
    console.log(`Platform        : ${process.platform} (${process.arch})  runtime: Bun ${Bun.version}`);
    console.log(`Allowed host    : ${ALLOWED_HOST}   blocked host: ${BLOCKED_HOST}   blocked ip: ${BLOCKED_IP}\n`);

    const cases: Array<{ label: string; description: string; network: NetworkSpec | undefined }> = [
        { label: "omitted", description: "E. network option omitted entirely", network: undefined },
        { label: "enabled", description: "B. mode 'enabled', no allowNet", network: { mode: "enabled" } },
        { label: "disabled", description: "A. mode 'disabled'", network: { mode: "disabled" } },
        {
            label: "allowlist",
            description: `C/D. mode 'enabled', allowNet ['${ALLOWED_HOST}']`,
            network: { mode: "enabled", allowNet: [ALLOWED_HOST] },
        },
    ];

    const results = new Map<string, { dns: Probe; targets: Probe[] }>();
    for (const { label, description, network } of cases) {
        console.log(`=== ${description} ===`);
        try {
            const result = await probeEgress(label, network);
            results.set(label, result);
            console.log(`  ${render(result.dns)}`);
            for (const probe of result.targets) {
                console.log(`  ${render(probe)}`);
            }
        } catch (error) {
            console.log(`  PROBE ERROR: ${String(error).split("\n")[0]}`);
        }
        console.log();
    }

    console.log("=== REPORT ===");
    const reached = (label: string, target: string) =>
        results.get(label)?.targets.find(p => p.target.includes(target))?.exitCode === 0;

    const allowlist = results.get("allowlist");
    console.log(`(a) 'disabled' blocks egress:              ${answer(results.has("disabled"), !reached("disabled", ALLOWED_HOST) && !reached("disabled", BLOCKED_IP))}`);
    console.log(`(b) 'enabled' with no allowNet is open:    ${answer(results.has("enabled"), reached("enabled", ALLOWED_HOST) && reached("enabled", BLOCKED_HOST))}`);
    console.log(`(c) allowNet permits the listed host:      ${answer(!!allowlist, reached("allowlist", ALLOWED_HOST))}`);
    console.log(`(d) allowNet blocks an unlisted host:      ${answer(!!allowlist, !reached("allowlist", BLOCKED_HOST))}`);
    console.log(`(e) allowNet ALSO blocks it by raw IP:     ${answer(!!allowlist, !reached("allowlist", BLOCKED_IP))}  <- if NO, allowNet is DNS-level only`);
    console.log(`(f) omitted default matches 'enabled':     ${answer(results.has("omitted"), reached("omitted", ALLOWED_HOST))}`);
    console.log(`\nDNS under the allowlist: ${allowlist ? render(allowlist.dns) : "(not run)"}`);
}

function answer(ran: boolean, condition: boolean): string {
    return !ran ? "(not run)" : condition ? "YES" : "NO";
}

main().catch((error) => {
    console.error("PROBE FAILED:", error);
    process.exit(1);
});
