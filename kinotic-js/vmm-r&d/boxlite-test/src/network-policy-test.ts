import { SimpleBox, getJsBoxlite, type NetworkSpec } from "@boxlite-ai/boxlite";
import { resolve4, resolveCname } from "node:dns/promises";

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
//  F. allowNet: []            — mode 'disabled' cannot boot (finding #12), so an empty
//                               allowlist was the first candidate for a workload that
//                               should reach nothing. It is not one: an empty list is
//                               indistinguishable from omitting the option (finding #13).
//  G/H. sink allowlist        — a POPULATED list is enforced, so a list naming only an
//                               address or name nothing answers on is the last candidate
//                               for a no-egress policy. Whether it boots and denies
//                               everything decides if NetworkMode.DISABLED can be
//                               implemented at all on 0.9.7.
//  I. CNAME chain           — github.com and registry.npmjs.org resolve directly, but an
//                               Azure storage account host is a CNAME chain
//                               (<account>.blob.core.windows.net -> blob.<cluster>.store.
//                               core.windows.net -> address). Does an allowlist naming the
//                               head of the chain permit the connection, or does boxlite
//                               attribute the answer to the canonical name? The variants
//                               name the whole chain, a wildcard, and the bare address.
//
// Requires ordinary outbound internet from the host. Every box is removed in cleanup.

const RUN = Date.now().toString(36);
const runtime = getJsBoxlite().withDefaultConfig();

// example.com and cloudflare.com resolve to unrelated networks, so an allowlist naming one
// cannot accidentally permit the other. 1.1.1.1 is cloudflare reached without any name.
const ALLOWED_HOST = "example.com";
const BLOCKED_HOST = "cloudflare.com";
const BLOCKED_IP = "1.1.1.1";
// An allowlist that permits nothing reachable is the only remaining candidate for a
// no-egress workload. Both forms are tried because allowNet is documented for hostnames and
// may not accept an address at all: TEST-NET-1 (RFC 5737) is reserved and never routed, and
// the .invalid TLD (RFC 2606) is guaranteed never to resolve.
const SINK_IP = "192.0.2.1";
const SINK_HOST = "no-egress.invalid";
// A storage account host whose answer is a CNAME chain. The canonical name and address are
// resolved on the host at startup rather than pinned, since Azure moves them.
const AZURE_HOST = "kin00aca0a5a87cfeb0a1f11.blob.core.windows.net";
const AZURE_WILDCARD = "*.blob.core.windows.net";

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
async function probeEgress(label: string,
                           network: NetworkSpec | undefined,
                           lookupHost = BLOCKED_HOST,
                           urls = [`http://${ALLOWED_HOST}`, `http://${BLOCKED_HOST}`, `http://${BLOCKED_IP}`]): Promise<{ dns: Probe; targets: Probe[] }> {
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
        // 0.10 creates the record only; exec no longer boots the VM as a side effect
        await (await runtime.get(name))!.start();

        // Separating name resolution from the fetch shows whether the allowlist is
        // enforced at DNS or on the connection itself
        // The status is echoed rather than read from the exec: a shell reports the exit
        // status of the LAST command in a pipeline, so piping nslookup into tail would
        // always report tail's success and hide a failed lookup
        const lookup = await box.exec("sh", "-c",
            `nslookup ${lookupHost} >/tmp/dns.out 2>&1; echo "lookup-exit=$?"; tail -n 3 /tmp/dns.out`);
        const reported = lookup.stdout.match(/lookup-exit=(\d+)/);
        const dns: Probe = {
            target: `dns:${lookupHost}`,
            exitCode: reported ? Number(reported[1]) : -1,
            detail: lookup.stdout.replace(/lookup-exit=\d+/, "").trim().split("\n").filter(Boolean).join(" | "),
        };

        const targets: Probe[] = [];
        for (const target of urls) {
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

/**
 * Whether the guest got through to the target. Azure answers GET / with a 4xx, on which
 * busybox wget exits non-zero, so an HTTP error response counts: TLS and HTTP happened.
 */
function reachedTarget(probe: Probe | undefined): boolean {
    return probe !== undefined && (probe.exitCode === 0 || /server returned error: HTTP/.test(probe.detail));
}

function render(probe: Probe): string {
    const verdict = reachedTarget(probe) ? "REACHED" : "blocked";
    return `${probe.target.padEnd(28)} ${verdict.padEnd(8)} exit=${probe.exitCode}${probe.detail ? `  ${probe.detail}` : ""}`;
}

async function main() {
    const boxliteVersion = (await import("@boxlite-ai/boxlite/package.json")).default.version as string;
    console.log(`boxlite version : ${boxliteVersion}`);
    console.log(`Platform        : ${process.platform} (${process.arch})  runtime: Bun ${Bun.version}`);
    console.log(`Allowed host    : ${ALLOWED_HOST}   blocked host: ${BLOCKED_HOST}   blocked ip: ${BLOCKED_IP}`);
    const [azureCname] = await resolveCname(AZURE_HOST);
    const [azureIp] = await resolve4(AZURE_HOST);
    console.log(`Azure host      : ${AZURE_HOST} -> ${azureCname} -> ${azureIp}\n`);
    const azureUrls = [`https://${AZURE_HOST}/`, `https://${azureCname}/`];

    const cases: Array<{ label: string; description: string; network: NetworkSpec | undefined; lookupHost?: string; urls?: string[] }> = [
        { label: "omitted", description: "E. network option omitted entirely", network: undefined },
        { label: "enabled", description: "B. mode 'enabled', no allowNet", network: { mode: "enabled" } },
        { label: "disabled", description: "A. mode 'disabled'", network: { mode: "disabled" } },
        {
            label: "allowlist",
            description: `C/D. mode 'enabled', allowNet ['${ALLOWED_HOST}']`,
            network: { mode: "enabled", allowNet: [ALLOWED_HOST] },
        },
        {
            label: "empty-allowlist",
            description: "F. mode 'enabled', allowNet: [] — deny-all, or the same as omitting it?",
            network: { mode: "enabled", allowNet: [] },
        },
        {
            label: "sink-ip",
            description: `G. mode 'enabled', allowNet ['${SINK_IP}'] — an address nothing answers on`,
            network: { mode: "enabled", allowNet: [SINK_IP] },
        },
        {
            label: "sink-name",
            description: `H. mode 'enabled', allowNet ['${SINK_HOST}'] — a name that cannot resolve`,
            network: { mode: "enabled", allowNet: [SINK_HOST] },
        },
        {
            label: "azure-head",
            description: `I. mode 'enabled', allowNet ['${AZURE_HOST}'] — the head of the CNAME chain only`,
            network: { mode: "enabled", allowNet: [AZURE_HOST] },
            lookupHost: AZURE_HOST,
            urls: azureUrls,
        },
        {
            label: "azure-chain",
            description: `I. mode 'enabled', allowNet ['${AZURE_HOST}', '${azureCname}'] — the whole chain`,
            network: { mode: "enabled", allowNet: [AZURE_HOST, azureCname] },
            lookupHost: AZURE_HOST,
            urls: azureUrls,
        },
        {
            label: "azure-wildcard",
            description: `I. mode 'enabled', allowNet ['${AZURE_WILDCARD}'] — a wildcard`,
            network: { mode: "enabled", allowNet: [AZURE_WILDCARD] },
            lookupHost: AZURE_HOST,
            urls: azureUrls,
        },
        {
            label: "azure-ip",
            description: `I. mode 'enabled', allowNet ['${azureIp}'] — the address alone`,
            network: { mode: "enabled", allowNet: [azureIp] },
            lookupHost: AZURE_HOST,
            urls: azureUrls,
        },
    ];

    // An optional label prefix runs a subset, e.g. `azure` for the CNAME cases alone
    const only = process.argv[2];
    const results = new Map<string, { dns: Probe; targets: Probe[] }>();
    for (const { label, description, network, lookupHost, urls } of cases) {
        if (only && !label.startsWith(only)) {
            continue;
        }
        console.log(`=== ${description} ===`);
        try {
            const result = await probeEgress(label, network, lookupHost, urls);
            results.set(label, result);
            console.log(`  ${render(result.dns)}`);
            for (const probe of result.targets) {
                console.log(`  ${render(probe)}`);
            }
        } catch (error) {
            // Printed whole: a failed boot carries the shim trace and the exit status
            // inside the error message, which is the only place they appear
            console.log(String(error).split("\n").map(line => `  | ${line}`).join("\n"));
        }
        console.log();
    }

    console.log("=== REPORT ===");
    const reached = (label: string, target: string) =>
        reachedTarget(results.get(label)?.targets.find(p => p.target.includes(target)));

    const allowlist = results.get("allowlist");
    console.log(`(a) 'disabled' blocks egress:              ${answer(results.has("disabled"), !reached("disabled", ALLOWED_HOST) && !reached("disabled", BLOCKED_IP))}`);
    console.log(`(b) 'enabled' with no allowNet is open:    ${answer(results.has("enabled"), reached("enabled", ALLOWED_HOST) && reached("enabled", BLOCKED_HOST))}`);
    console.log(`(c) allowNet permits the listed host:      ${answer(!!allowlist, reached("allowlist", ALLOWED_HOST))}`);
    console.log(`(d) allowNet blocks an unlisted host:      ${answer(!!allowlist, !reached("allowlist", BLOCKED_HOST))}`);
    console.log(`(e) allowNet ALSO blocks it by raw IP:     ${answer(!!allowlist, !reached("allowlist", BLOCKED_IP))}  <- if NO, allowNet is DNS-level only`);
    console.log(`(f) omitted default matches 'enabled':     ${answer(results.has("omitted"), reached("omitted", ALLOWED_HOST))}`);
    const empty = results.get("empty-allowlist");
    const denies = (label: string) =>
        answer(results.has(label), !reached(label, ALLOWED_HOST) && !reached(label, BLOCKED_HOST) && !reached(label, BLOCKED_IP));
    console.log(`(g) an empty allowNet denies everything:   ${answer(!!empty, !reached("empty-allowlist", ALLOWED_HOST) && !reached("empty-allowlist", BLOCKED_IP))}  <- if YES, it is the no-egress mode 'disabled' cannot provide`);
    console.log(`(h) a reserved-IP allowlist denies all:     ${denies("sink-ip")}`);
    console.log(`(i) an unresolvable-name allowlist denies:  ${denies("sink-name")}`);
    console.log(`    (either YES is a usable no-egress policy, since a populated list is enforced)`);
    console.log(`(j) naming the chain's head alone reaches:   ${answer(results.has("azure-head"), reached("azure-head", AZURE_HOST))}`);
    console.log(`(k) naming the whole chain reaches:         ${answer(results.has("azure-chain"), reached("azure-chain", AZURE_HOST))}`);
    console.log(`(l) a wildcard reaches:                     ${answer(results.has("azure-wildcard"), reached("azure-wildcard", AZURE_HOST))}`);
    console.log(`(m) the address alone reaches:              ${answer(results.has("azure-ip"), reached("azure-ip", AZURE_HOST))}`);
    console.log(`\nDNS under the allowlist: ${allowlist ? render(allowlist.dns) : "(not run)"}`);
    for (const label of ["azure-head", "azure-chain", "azure-wildcard", "azure-ip"]) {
        const result = results.get(label);
        if (result) {
            console.log(`DNS under ${label.padEnd(15)}: ${render(result.dns)}`);
        }
    }
}

function answer(ran: boolean, condition: boolean): string {
    return !ran ? "(not run)" : condition ? "YES" : "NO";
}

main().catch((error) => {
    console.error("PROBE FAILED:", error);
    process.exit(1);
});
