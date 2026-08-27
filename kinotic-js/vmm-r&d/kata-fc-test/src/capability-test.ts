import { spawnSync } from "node:child_process";

// Can Kata Containers on Firecracker back the kinotic vm-manager's IVmProvider?
//
// This is the Cloud Hypervisor probe in ../kata-ch-test asked of a different hypervisor, so
// the two reports can be read side by side. One capability is missing by construction rather
// than by result: Firecracker has no filesystem sharing — configuration-fc.toml carries no
// shared_fs setting where configuration-clh.toml sets shared_fs = "virtio-fs" — so a bind
// mount cannot be expressed at all. Phase 2 records that instead of testing volume counts,
// readOnly enforcement, or the host-watcher log path, all of which need a shared directory.
//
//   0. preflight     — is this really a VM per workload, and is Firecracker the hypervisor
//                      that booted it? Every later result is meaningless otherwise, so the
//                      probe refuses to continue rather than reporting a container's traits
//   1. OCI semantics — entrypoint/cmd/env/workdir, the reason boxlite was chosen at all
//   2. sharing       — is a host bind mount available at all?
//   3. stdout        — is the entrypoint's output captured on the host? boxlite discards it,
//                      and with no shared directory this is the only log path left
//   4. lifecycle     — start, exit code of a run-to-completion workload, stop, restart with
//                      rootfs state intact, and whether state survives the manager process
//   5. resources     — are memory and vCPU limits honoured, and where does the writable layer
//                      live on the host, since that decides how it is capped
//   6. network       — egress open by default, a no-egress mode that boots, published ports,
//                      and whether the workload has a host-side address a firewall could
//                      restrict to the api-gateway
//   7. boot latency  — this layer exists for a developer's edit/redeploy cycle
//
// Needs the stack from ./setup-ubuntu.sh, root, and outbound internet. Every container is
// removed in cleanup.

const RUNTIME = "io.containerd.kata-fc.v2";
const NERDCTL = "/usr/local/bin/nerdctl";
/** Firecracker has no virtiofs, so a workload's rootfs must come from a block snapshotter. */
const SNAPSHOTTER = "devmapper";
const IMAGE = "alpine:latest";
/** Reached by address so an egress check never passes on a DNS failure alone. */
const BLOCKED_IP = "1.1.1.1";
const RUN = Date.now().toString(36);

interface Cmd {
    code: number;
    stdout: string;
    stderr: string;
}

function run(command: string, args: string[], timeoutMs = 120_000): Cmd {
    const result = spawnSync(command, args, { encoding: "utf-8", timeout: timeoutMs });
    return {
        code: result.status ?? -1,
        stdout: (result.stdout ?? "").trim(),
        stderr: (result.stderr ?? "").trim(),
    };
}

/** nerdctl against the devmapper snapshotter, which every command here needs. */
function nerdctl(args: string[], timeoutMs?: number): Cmd {
    return run(NERDCTL, ["--snapshotter", SNAPSHOTTER, ...args], timeoutMs);
}

/** nerdctl run on the Kata Firecracker runtime. */
function kata(args: string[], timeoutMs?: number): Cmd {
    return nerdctl(["run", "--runtime", RUNTIME, ...args], timeoutMs);
}

function remove(name: string): void {
    nerdctl(["rm", "-f", name]);
}

const results = new Map<string, boolean>();
function record(key: string, value: boolean): boolean {
    results.set(key, value);
    return value;
}
function verdict(key: string): string {
    return results.has(key) ? (results.get(key) ? "YES" : "NO") : "(not run)";
}

// ---------------------------------------------------------------------------------------

function phasePreflight(): void {
    console.log("=== Phase 0: is a workload actually a VM here, booted by Firecracker? ===");
    const hostKernel = run("uname", ["-r"]).stdout;
    const guest = kata(["--rm", IMAGE, "uname", "-r"]);
    console.log(`  host kernel           : ${hostKernel}`);
    console.log(`  guest kernel          : ${guest.stdout || `(failed: ${guest.stderr.split("\n")[0]})`}`);

    const isVm = guest.code === 0 && guest.stdout.length > 0 && guest.stdout !== hostKernel;
    record("vm", isVm);

    // A long-lived box makes the hypervisor process observable from the host. Read each
    // process's executable from /proc/PID/exe rather than matching a name: comm is truncated
    // to 15 characters, and a pgrep -f pattern matches this probe's own command line, so both
    // can report the wrong answer on a host that quietly resolved a different hypervisor.
    // Compared by basename because kata launches firecracker through the jailer, which
    // chroots it — the link then resolves to /firecracker rather than the bundle path.
    const name = `pre-${RUN}`;
    kata(["-d", "--name", name, IMAGE, "sleep", "120"]);
    const countVmm = (binary: string) => Number(run("sh", ["-c",
        `for p in /proc/[0-9]*; do exe=$(readlink -f "$p/exe" 2>/dev/null); [ "\${exe##*/}" = "${binary}" ] && echo x; done | wc -l`,
    ]).stdout || 0);
    const fcProcs = countVmm("firecracker");
    const qemuProcs = countVmm("qemu-system-x86_64");
    const clhProcs = countVmm("cloud-hypervisor");
    console.log(`  firecracker procs     : ${fcProcs}   (qemu: ${qemuProcs}, cloud-hypervisor: ${clhProcs})`);
    record("fc", fcProcs > 0);
    remove(name);

    if (!isVm) {
        console.log("\n  ABORTING: the guest reported the host kernel, so this ran under runc.");
        console.log("  Every capability below would pass for the wrong reason. Fix the runtime first.");
        process.exit(1);
    }
    if (fcProcs === 0) {
        console.log("\n  ABORTING: a VM booted but Firecracker did not boot it.");
        console.log("  These results would describe some other hypervisor. Check kata-runtime env.");
        process.exit(1);
    }
    console.log();
}

function phaseOciSemantics(): void {
    console.log("=== Phase 1: OCI image semantics ===");
    const entrypoint = kata(["--rm", "--entrypoint", "echo", IMAGE, "from-entrypoint"]);
    console.log(`  entrypoint override   : exit=${entrypoint.code} "${entrypoint.stdout}"`);
    record("entrypoint", entrypoint.stdout === "from-entrypoint");

    const env = kata(["--rm", "-e", "KINOTIC_TEST=abc", IMAGE, "sh", "-c", "echo $KINOTIC_TEST"]);
    console.log(`  environment           : exit=${env.code} "${env.stdout}"`);
    record("env", env.stdout === "abc");

    const workdir = kata(["--rm", "-w", "/tmp", IMAGE, "pwd"]);
    console.log(`  working directory     : exit=${workdir.code} "${workdir.stdout}"`);
    record("workdir", workdir.stdout === "/tmp");
    console.log();
}

function phaseSharing(): void {
    console.log("=== Phase 2: is a host bind mount a live share, or a copy? ===");
    // Accepting a -v flag proves nothing: Firecracker has no shared_fs, and kata satisfies the
    // mount by copying the directory in at boot. That reads exactly like a working bind mount
    // until something writes, so test propagation in both directions rather than visibility.
    const shared = `/tmp/kata-fc-share-${RUN}`;
    const name = `share-${RUN}`;
    run("mkdir", ["-p", shared]);
    run("sh", ["-c", `echo before-start > ${shared}/before.txt`]);

    const started = kata(["-d", "--name", name, "-v", `${shared}:/shared`, IMAGE, "sleep", "180"]);
    if (started.code !== 0) {
        console.log(`  bind mount -v         : REFUSED  ${(started.stderr.split("\n")[0] || "").slice(0, 120)}`);
        record("volumes", false);
        record("volumes-live", false);
        run("rm", ["-rf", shared]);
        console.log();
        return;
    }

    const before = nerdctl(["exec", name, "sh", "-c", "cat /shared/before.txt 2>&1"]);
    console.log(`  content present at boot: ${before.stdout}`);
    record("volumes", before.stdout.includes("before-start"));

    // A live share shows each side the other's later writes; a boot-time copy shows neither
    run("sh", ["-c", `echo after-start > ${shared}/after.txt`]);
    run("sleep", ["2"]);
    const hostToGuest = nerdctl(["exec", name, "sh", "-c", "cat /shared/after.txt 2>/dev/null || echo NOT-VISIBLE"]);
    console.log(`  host write -> guest    : ${hostToGuest.stdout}`);

    nerdctl(["exec", name, "sh", "-c", "echo from-guest > /shared/guest.txt"]);
    run("sleep", ["2"]);
    const guestToHost = run("sh", ["-c", `cat ${shared}/guest.txt 2>/dev/null || echo NOT-VISIBLE`]).stdout;
    console.log(`  guest write -> host    : ${guestToHost}`);

    const live = hostToGuest.stdout.includes("after-start") && guestToHost.includes("from-guest");
    record("volumes-live", live);
    console.log(`  => ${live ? "a live shared filesystem" : "a boot-time copy, not a shared filesystem"}`);
    console.log(`  (configuration-fc.toml has no shared_fs; configuration-clh.toml sets virtio-fs)`);
    remove(name);
    run("rm", ["-rf", shared]);
    console.log();
}

function phaseStdout(): void {
    console.log("=== Phase 3: is the entrypoint's stdout captured? ===");
    const name = `logs-${RUN}`;
    kata(["-d", "--name", name, IMAGE, "sh", "-c", "echo line-one; echo line-two >&2; sleep 30"]);
    const logs = nerdctl(["logs", name]);
    console.log(`  nerdctl logs          : exit=${logs.code} stdout="${logs.stdout}" stderr="${logs.stderr}"`);
    const combined = `${logs.stdout}\n${logs.stderr}`;
    record("stdout", combined.includes("line-one"));
    record("stderr", combined.includes("line-two"));

    // With no shared directory this file is the only way logs reach the host, so confirm it
    // is a real file a host process could tail rather than a stream only nerdctl can render
    const id = nerdctl(["inspect", "-f", "{{.Id}}", name]).stdout;
    const logFile = run("sh", ["-c",
        `find /var/lib/nerdctl -name '${id}-json.log' 2>/dev/null | head -n 1`]).stdout;
    console.log(`  host log file         : ${logFile || "(not found)"}`);
    if (logFile) {
        console.log(`  file type             : ${run("stat", ["-c", "%F size=%s", logFile]).stdout}`);
    }
    record("logfile", logFile.length > 0);
    remove(name);
    console.log();
}

function phaseLifecycle(): void {
    console.log("=== Phase 4: lifecycle and IVmProvider.recover ===");

    const batch = `batch-${RUN}`;
    kata(["--name", batch, IMAGE, "sh", "-c", "echo working; exit 42"]);
    const inspect = nerdctl(["inspect", "-f", "{{.State.Status}} {{.State.ExitCode}}", batch]);
    console.log(`  batch status/exit code: ${inspect.stdout || inspect.stderr.split("\n")[0]}`);
    record("exitcode", inspect.stdout.includes("42"));
    remove(batch);

    const keep = `keep-${RUN}`;
    kata(["-d", "--name", keep, IMAGE, "sh", "-c", "echo boot >> /root/boots.log; sleep 300"]);
    nerdctl(["stop", keep], 60_000);
    const restarted = nerdctl(["start", keep], 60_000);
    const boots = nerdctl(["exec", keep, "cat", "/root/boots.log"]);
    console.log(`  restart in place      : exit=${restarted.code}, /root/boots.log has ${boots.stdout.split("\n").filter(Boolean).length} line(s)`);
    record("restart", restarted.code === 0 && boots.stdout.split("\n").filter(Boolean).length >= 2);

    const listed = nerdctl(["ps", "-a", "--filter", `name=${keep}`, "--format", "{{.Names}} {{.Status}}"]);
    const id = nerdctl(["inspect", "-f", "{{.Id}}", keep]).stdout;
    console.log(`  visible to a new proc : ${listed.stdout}`);
    console.log(`  stable id for logs    : ${id.slice(0, 24)}`);
    record("recover", listed.stdout.includes(keep) && id.length > 0);
    remove(keep);
    console.log();
}

function phaseResources(): void {
    console.log("=== Phase 5: are resource limits honoured? ===");
    // What constrains the workload is its cgroup, not the size of the sandbox VM. free -m and
    // nproc describe the VM, which Kata sizes independently of the limit, so they answer a
    // different question and report a limit as unhonoured when it is being enforced.
    const mem = kata(["--rm", "--memory", "512m", IMAGE, "sh", "-c",
        "cat /sys/fs/cgroup/memory.max 2>/dev/null || cat /sys/fs/cgroup/memory/memory.limit_in_bytes"]);
    const memMib = Number(mem.stdout || 0) / 1024 / 1024;
    console.log(`  cgroup memory.max     : ${mem.stdout} (${memMib} MiB) for --memory 512m`);
    record("memory", memMib === 512);

    const cpus = kata(["--rm", "--cpus", "2", IMAGE, "sh", "-c", "cat /sys/fs/cgroup/cpu.max 2>/dev/null"]);
    const [quota, period] = cpus.stdout.split(/\s+/);
    console.log(`  cgroup cpu.max        : ${cpus.stdout} (${Number(quota) / Number(period)} cpus) for --cpus 2`);
    record("cpus", Number(quota) / Number(period) === 2);

    const seen = kata(["--rm", "--memory", "512m", "--cpus", "2", IMAGE, "sh", "-c",
        "echo \"$(free -m | awk '/Mem:/ {print $2}') MiB / $(nproc) cpus\""]);
    console.log(`  but the guest sees    : ${seen.stdout}`);

    // Unlike the virtiofs stacks, the rootfs here is a thin-pool block device rather than a
    // directory on the host filesystem, which changes what a disk cap would have to act on
    const df = kata(["--rm", IMAGE, "sh", "-c", "df -h / | tail -n 1"]);
    console.log(`  guest df /            : ${df.stdout}`);
    const dev = kata(["--rm", IMAGE, "sh", "-c", "mount | grep ' / ' | head -n 1"]);
    console.log(`  guest rootfs mount    : ${dev.stdout}`);
    console.log(`  host thin-pool        : ${run("sh", ["-c", "dmsetup info kata-fc-pool 2>/dev/null | head -n 3 | tr '\\n' ' '"]).stdout || "(not found)"}`);
    console.log();
}

function phaseNetwork(): void {
    console.log("=== Phase 6: network ===");
    const open = kata(["--rm", IMAGE, "sh", "-c", "wget -T 8 -q -O /dev/null http://example.com; echo exit=$?"]);
    console.log(`  default egress        : ${open.stdout}`);
    record("egress-open", open.stdout.includes("exit=0"));

    // Fetch a raw IP rather than a hostname: a hostname fetch also fails when only DNS is
    // broken, which would report egress as denied on a workload that can still reach the
    // network by address
    const none = kata(["--rm", "--network", "none", IMAGE,
                       "sh", "-c", `wget -T 8 -q -O /dev/null http://${BLOCKED_IP}; echo exit=$?`]);
    console.log(`  --network none        : booted=${none.code === 0 ? "YES" : `NO (${none.stderr.split("\n")[0]})`} ${none.stdout}`);
    record("no-egress-boots", none.code === 0);
    record("no-egress-denies", none.code === 0 && !none.stdout.includes("exit=0"));

    const server = `web-${RUN}`;
    kata(["-d", "--name", server, "-p", "18080:80", "nginx:alpine"]);
    run("sleep", ["3"]);
    const curl = run("curl", ["-s", "-o", "/dev/null", "-w", "%{http_code}", "--max-time", "8", "http://127.0.0.1:18080"]);
    console.log(`  published port 18080  : HTTP ${curl.stdout}`);
    record("ports", curl.stdout === "200");

    const ip = nerdctl(["inspect", "-f", "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}", server]);
    console.log(`  workload address      : ${ip.stdout || "(none reported)"}`);
    console.log(`  (a host-side address is what lets a firewall allow only the api-gateway)`);
    record("addressable", ip.stdout.trim().length > 0);
    remove(server);
    console.log();
}

function phaseBootLatency(): void {
    console.log("=== Phase 7: cold boot latency (the dev edit/redeploy cycle) ===");
    const samples: number[] = [];
    for (let i = 0; i < 3; i++) {
        const started = Date.now();
        const attempt = kata(["--rm", IMAGE, "true"]);
        if (attempt.code === 0) {
            samples.push(Date.now() - started);
        }
    }
    console.log(`  run-to-exit, 3 samples: ${samples.map(ms => `${ms} ms`).join(", ") || "(all failed)"}`);
    console.log();
}

async function main() {
    console.log(`Runtime         : ${RUNTIME}`);
    console.log(`Snapshotter     : ${SNAPSHOTTER}`);
    console.log(`nerdctl         : ${nerdctl(["--version"]).stdout}`);
    console.log(`kata-runtime    : ${run("/opt/kata/bin/kata-runtime", ["--version"]).stdout.split("\n")[0]}`);
    console.log(`firecracker     : ${run("/opt/kata/bin/firecracker", ["--version"]).stdout.split("\n")[0]}`);
    console.log(`Host            : ${run("uname", ["-sr"]).stdout}\n`);

    phasePreflight();
    phaseOciSemantics();
    phaseSharing();
    phaseStdout();
    phaseLifecycle();
    phaseResources();
    phaseNetwork();
    phaseBootLatency();

    console.log("=== REPORT: what the vm-manager needs, and whether this stack provides it ===");
    console.log(`  workloads are real VMs                  : ${verdict("vm")} (firecracker seen: ${verdict("fc")})`);
    console.log(`  OCI entrypoint / env / workdir          : ${verdict("entrypoint")} / ${verdict("env")} / ${verdict("workdir")}`);
    console.log(`  bind mount content present at boot      : ${verdict("volumes")}`);
    console.log(`  ...and it is a LIVE shared filesystem   : ${verdict("volumes-live")}   (clh: YES, via virtio-fs)`);
    console.log(`  entrypoint stdout/stderr captured       : ${verdict("stdout")} / ${verdict("stderr")}`);
    console.log(`  stdout reaches a tailable host file     : ${verdict("logfile")}`);
    console.log(`  run-to-completion exit code             : ${verdict("exitcode")}`);
    console.log(`  restart in place, rootfs intact         : ${verdict("restart")}`);
    console.log(`  state survives the manager process      : ${verdict("recover")}`);
    console.log(`  memory / vcpu limits honoured           : ${verdict("memory")} / ${verdict("cpus")}`);
    console.log(`  egress open by default                  : ${verdict("egress-open")}`);
    console.log(`  no-egress mode boots                    : ${verdict("no-egress-boots")}   and denies: ${verdict("no-egress-denies")}`);
    console.log(`  published ports reachable from the host : ${verdict("ports")}`);
    console.log(`  workload has a host-side address        : ${verdict("addressable")}`);
}

main().catch((error) => {
    console.error("PROBE FAILED:", error);
    process.exit(1);
});
