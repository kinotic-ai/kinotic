import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync, watch } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

// Can Kata Containers on Cloud Hypervisor back the kinotic vm-manager's IVmProvider?
//
// Every phase below maps to something the provider must do, or to a place where boxlite
// 0.9.7 could not do it (findings #6, #7, #9, #12, #13 in ../boxlite-test/README.md). The
// point is a like-for-like answer to the same questions, not a tour of Kata's features.
//
//   0. preflight     — is this really a VM per workload, or did the runtime fall back to
//                      runc? Every later result is meaningless if this is not a VM, so the
//                      probe refuses to continue rather than reporting capabilities that
//                      belong to a container.
//   1. OCI semantics — entrypoint/cmd/env/workdir, the reason boxlite was chosen at all
//   2. mounts        — three at once, which boxlite cannot boot (#9), and whether readOnly
//                      is enforced, which the customer-code design depends on
//   3. host view     — do guest writes reach a host watcher via inotify? The Alloy log
//                      shipping design is built on this
//   4. stdout        — is the entrypoint's output captured on the host? boxlite discards it
//                      (#7), which is why workloads must write files to /var/log/kinotic
//   5. lifecycle     — start, exit code of a run-to-completion workload (invisible in
//                      boxlite, #6), stop, restart with rootfs state intact, and whether
//                      state survives the manager process, which is IVmProvider.recover
//   6. resources     — are memory, vCPU and disk limits honoured, and where does the
//                      writable layer live on the host, since that decides how it is capped
//   7. network       — egress open by default, a no-egress mode that actually boots (#12),
//                      published ports, and whether the workload has a host-side address
//                      that a firewall could restrict to the api-gateway
//   8. boot latency  — this layer exists for a developer's edit/redeploy cycle
//
// Needs the stack from ./setup-ubuntu.sh, root, and outbound internet. Every container is
// removed in cleanup.

const RUNTIME = "io.containerd.kata-clh.v2";
const NERDCTL = "/usr/local/bin/nerdctl";
const IMAGE = "alpine:latest";
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

/** nerdctl on the Kata Cloud Hypervisor runtime. */
function kata(args: string[], timeoutMs?: number): Cmd {
    return run(NERDCTL, ["run", "--runtime", RUNTIME, ...args], timeoutMs);
}

function nerdctl(args: string[], timeoutMs?: number): Cmd {
    return run(NERDCTL, args, timeoutMs);
}

function remove(name: string): void {
    run(NERDCTL, ["rm", "-f", name]);
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
    console.log("=== Phase 0: is a workload actually a VM here? ===");
    const hostKernel = run("uname", ["-r"]).stdout;
    const guest = kata(["--rm", IMAGE, "uname", "-r"]);
    console.log(`  host kernel           : ${hostKernel}`);
    console.log(`  guest kernel          : ${guest.stdout || `(failed: ${guest.stderr.split("\n")[0]})`}`);

    const isVm = guest.code === 0 && guest.stdout.length > 0 && guest.stdout !== hostKernel;
    record("vm", isVm);

    // A long-lived box makes the hypervisor process observable from the host. Match on each
    // process's executable rather than its name: comm is truncated to 15 characters so it
    // never equals "cloud-hypervisor", and a pgrep -f pattern matches this probe's own
    // command line, so both report the wrong answer on a host running QEMU.
    const name = `pre-${RUN}`;
    kata(["-d", "--name", name, IMAGE, "sleep", "120"]);
    const countVmm = (binary: string) => Number(run("sh", ["-c",
        `for p in /proc/[0-9]*; do [ "$(readlink -f "$p/exe" 2>/dev/null)" = "${binary}" ] && echo x; done | wc -l`,
    ]).stdout || 0);
    const clhProcs = countVmm("/opt/kata/bin/cloud-hypervisor");
    const qemuProcs = countVmm("/opt/kata/bin/qemu-system-x86_64");
    console.log(`  cloud-hypervisor procs: ${clhProcs}${qemuProcs > 0 ? `   qemu-system procs: ${qemuProcs}` : ""}`);
    record("clh", clhProcs > 0);
    remove(name);

    if (!isVm) {
        console.log("\n  ABORTING: the guest reported the host kernel, so this ran under runc.");
        console.log("  Every capability below would pass for the wrong reason. Fix the runtime first.");
        process.exit(1);
    }
    console.log();
}

function phaseOciSemantics(): void {
    console.log("=== Phase 1: OCI image semantics ===");
    const entrypoint = kata(["--rm", "--entrypoint", "echo", IMAGE, "from-entrypoint"]);
    console.log(`  entrypoint override   : exit=${entrypoint.code} "${entrypoint.stdout}"`);
    record("entrypoint", entrypoint.code === 0 && entrypoint.stdout === "from-entrypoint");

    const env = kata(["--rm", "-e", "KINOTIC_TEST=abc", IMAGE, "sh", "-c", "echo $KINOTIC_TEST"]);
    console.log(`  environment           : exit=${env.code} "${env.stdout}"`);
    record("env", env.stdout === "abc");

    const workdir = kata(["--rm", "-w", "/tmp", IMAGE, "pwd"]);
    console.log(`  working directory     : exit=${workdir.code} "${workdir.stdout}"`);
    record("workdir", workdir.stdout === "/tmp");
    console.log();
}

function phaseMounts(hostDirs: string[]): void {
    console.log("=== Phase 2: mounts (boxlite cannot boot three) ===");
    for (const count of [1, 2, 3, 4]) {
        const args = hostDirs.slice(0, count).flatMap((dir, index) => ["-v", `${dir}:/v${index}`]);
        const attempt = kata(["--rm", ...args, IMAGE, "sh", "-c", "ls -d /v* | tr '\\n' ' '"]);
        console.log(`  ${count} volume(s)           : ${attempt.code === 0 ? `STARTED  guest sees: ${attempt.stdout}` : `FAILED   ${attempt.stderr.split("\n")[0]}`}`);
        record(`volumes-${count}`, attempt.code === 0);
    }

    const readOnly = kata(["--rm", "-v", `${hostDirs[0]}:/ro:ro`, IMAGE,
                           "sh", "-c", "echo denied > /ro/should-not-exist 2>&1; echo exit=$?"]);
    console.log(`  readOnly enforced     : ${readOnly.stdout}`);
    record("readonly", readOnly.stdout.includes("exit=1") || readOnly.stdout.includes("exit=2"));
    console.log();
}

async function phaseHostView(shared: string): Promise<void> {
    console.log("=== Phase 3: does a host watcher see guest writes (the Alloy design)? ===");
    const name = `watch-${RUN}`;
    kata(["-d", "--name", name, "-v", `${shared}:/out`, IMAGE, "sleep", "120"]);

    const seen = await new Promise<number | null>(resolve => {
        const started = Date.now();
        const watcher = watch(shared, () => {
            watcher.close();
            resolve(Date.now() - started);
        });
        setTimeout(() => {
            watcher.close();
            resolve(null);
        }, 10_000);
        nerdctl(["exec", name, "sh", "-c", "echo hello > /out/guest.log"]);
    });

    console.log(`  inotify saw the write : ${seen === null ? "NO (timed out after 10s)" : `YES after ${seen} ms`}`);
    const readBack = run("sh", ["-c", `cat ${join(shared, "guest.log")} 2>/dev/null`]).stdout;
    console.log(`  host reads the content: "${readBack}"`);
    record("inotify", seen !== null);
    record("hostread", readBack === "hello");
    remove(name);
    console.log();
}

function phaseStdout(): void {
    console.log("=== Phase 4: is the entrypoint's stdout captured? (boxlite discards it) ===");
    const name = `logs-${RUN}`;
    kata(["-d", "--name", name, IMAGE, "sh", "-c", "echo line-one; echo line-two >&2; sleep 30"]);
    const logs = nerdctl(["logs", name]);
    console.log(`  nerdctl logs          : exit=${logs.code} stdout="${logs.stdout}" stderr="${logs.stderr}"`);
    const combined = `${logs.stdout}\n${logs.stderr}`;
    record("stdout", combined.includes("line-one"));
    record("stderr", combined.includes("line-two"));
    remove(name);
    console.log();
}

function phaseLifecycle(): void {
    console.log("=== Phase 5: lifecycle and IVmProvider.recover ===");

    // Run-to-completion with a non-zero status — invisible in boxlite (finding #6)
    const batch = `batch-${RUN}`;
    kata(["--name", batch, IMAGE, "sh", "-c", "echo working; exit 42"]);
    const inspect = nerdctl(["inspect", "-f", "{{.State.Status}} {{.State.ExitCode}}", batch]);
    console.log(`  batch status/exit code: ${inspect.stdout || inspect.stderr.split("\n")[0]}`);
    record("exitcode", inspect.stdout.includes("42"));
    remove(batch);

    // Stop and start again: does the writable layer survive, and does the entrypoint re-run?
    const keep = `keep-${RUN}`;
    kata(["-d", "--name", keep, IMAGE, "sh", "-c", "echo boot >> /root/boots.log; sleep 300"]);
    nerdctl(["stop", keep], 60_000);
    const restarted = nerdctl(["start", keep], 60_000);
    const boots = nerdctl(["exec", keep, "cat", "/root/boots.log"]);
    console.log(`  restart in place      : exit=${restarted.code}, /root/boots.log has ${boots.stdout.split("\n").filter(Boolean).length} line(s)`);
    record("restart", restarted.code === 0 && boots.stdout.split("\n").filter(Boolean).length >= 2);

    // The vm-manager crashing must not lose the workload: containerd owns this state, so a
    // brand-new process can still find and address it by label
    const listed = nerdctl(["ps", "-a", "--filter", `name=${keep}`, "--format", "{{.Names}} {{.Status}}"]);
    const id = nerdctl(["inspect", "-f", "{{.Id}}", keep]).stdout;
    console.log(`  visible to a new proc : ${listed.stdout}`);
    console.log(`  stable id for logs    : ${id.slice(0, 24)}`);
    record("recover", listed.stdout.includes(keep) && id.length > 0);
    remove(keep);
    console.log();
}

function phaseResources(): void {
    console.log("=== Phase 6: are resource limits honoured? ===");
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

    // The VM is sized independently of the limit, so a guest misreports its own size to
    // anything reading free/nproc — the reason a workload must size itself from its cgroup
    const seen = kata(["--rm", "--memory", "512m", "--cpus", "2", IMAGE, "sh", "-c",
        "echo \"$(free -m | awk '/Mem:/ {print $2}') MiB / $(nproc) cpus\""]);
    console.log(`  but the guest sees    : ${seen.stdout}`);

    const df = kata(["--rm", IMAGE, "sh", "-c", "df -h / | tail -n 1"]);
    console.log(`  guest df /            : ${df.stdout}`);

    // Where the writable layer lives decides how disk gets capped: a host directory can take
    // an XFS project quota, the same mechanism already designed for volume mounts
    const snapshotter = run("sh", ["-c", "ls -d /var/lib/containerd/io.containerd.snapshotter.v1.* 2>/dev/null | tr '\\n' ' '"]);
    console.log(`  host snapshotter dirs : ${snapshotter.stdout || "(none found)"}`);
    console.log(`  host fs for that dir  : ${run("sh", ["-c", "df -hT /var/lib/containerd | tail -n 1"]).stdout}`);
    console.log();
}

function phaseNetwork(): void {
    console.log("=== Phase 7: network ===");
    const open = kata(["--rm", IMAGE, "sh", "-c", "wget -T 8 -q -O /dev/null http://example.com; echo exit=$?"]);
    console.log(`  default egress        : ${open.stdout}`);
    record("egress-open", open.stdout.includes("exit=0"));

    // The equivalent of NetworkMode.DISABLED, which boxlite cannot boot at all (finding #12)
    const none = kata(["--rm", "--network", "none", IMAGE,
                       "sh", "-c", "wget -T 8 -q -O /dev/null http://example.com; echo exit=$?"]);
    console.log(`  --network none        : booted=${none.code === 0 ? "YES" : `NO (${none.stderr.split("\n")[0]})`} ${none.stdout}`);
    record("no-egress-boots", none.code === 0);
    record("no-egress-denies", none.code === 0 && !none.stdout.includes("exit=0"));

    const server = `web-${RUN}`;
    kata(["-d", "--name", server, "-p", "18080:80", "nginx:alpine"]);
    // nginx needs a moment to bind before the host probe means anything
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
    console.log("=== Phase 8: cold boot latency (the dev edit/redeploy cycle) ===");
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
    console.log(`nerdctl         : ${nerdctl(["--version"]).stdout}`);
    console.log(`kata-runtime    : ${run("/opt/kata/bin/kata-runtime", ["--version"]).stdout.split("\n")[0]}`);
    console.log(`Host            : ${run("uname", ["-sr"]).stdout}\n`);

    const hostDirs = Array.from({ length: 4 }, () => mkdtempSync(join(tmpdir(), `kata-vol-${RUN}-`)));
    const shared = hostDirs[0]!;

    try {
        phasePreflight();
        phaseOciSemantics();
        phaseMounts(hostDirs);
        await phaseHostView(shared);
        phaseStdout();
        phaseLifecycle();
        phaseResources();
        phaseNetwork();
        phaseBootLatency();
    } finally {
        for (const dir of hostDirs) {
            rmSync(dir, { recursive: true, force: true });
        }
    }

    console.log("=== REPORT: what the vm-manager needs, and whether this stack provides it ===");
    console.log(`  workloads are real VMs                  : ${verdict("vm")} (cloud-hypervisor seen: ${verdict("clh")})`);
    console.log(`  OCI entrypoint / env / workdir          : ${verdict("entrypoint")} / ${verdict("env")} / ${verdict("workdir")}`);
    console.log(`  three volume mounts (boxlite: NO)       : ${verdict("volumes-3")}   four: ${verdict("volumes-4")}`);
    console.log(`  readOnly mount enforced                 : ${verdict("readonly")}`);
    console.log(`  host inotify sees guest writes          : ${verdict("inotify")}   host reads content: ${verdict("hostread")}`);
    console.log(`  entrypoint stdout/stderr captured       : ${verdict("stdout")} / ${verdict("stderr")}   (boxlite: NO)`);
    console.log(`  run-to-completion exit code (boxlite: NO): ${verdict("exitcode")}`);
    console.log(`  restart in place, rootfs intact         : ${verdict("restart")}`);
    console.log(`  state survives the manager process      : ${verdict("recover")}`);
    console.log(`  memory / vcpu limits honoured           : ${verdict("memory")} / ${verdict("cpus")}`);
    console.log(`  egress open by default                  : ${verdict("egress-open")}`);
    console.log(`  no-egress mode boots (boxlite: NO)      : ${verdict("no-egress-boots")}   and denies: ${verdict("no-egress-denies")}`);
    console.log(`  published ports reachable from the host : ${verdict("ports")}`);
    console.log(`  workload has a host-side address        : ${verdict("addressable")}`);
}

main().catch((error) => {
    console.error("PROBE FAILED:", error);
    process.exit(1);
});
