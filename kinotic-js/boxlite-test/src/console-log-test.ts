import { SimpleBox } from "@boxlite-ai/boxlite";
import { existsSync, readFileSync, statSync, watch, type FSWatcher } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

// Does boxlite persist the VM console — including the container ENTRYPOINT's stdout and
// stderr — to a host-side file we can tail? The native binding calls libkrun's
// krun_set_console_output with BoxFilesystemLayout::console_output_path, and the layout
// strings contain boxes/<box_id>/{console.log, shim.stderr}. If entrypoint output lands
// there LIVE, the log-shipping design needs no shell wrapper and no log volume mount:
// Alloy scrapes $BOXLITE_HOME/boxes/<box_id>/console.log directly, for ANY image —
// including distroless/scratch images that ship no /bin/sh.
//
// The entrypoint below execs awk DIRECTLY (no shell anywhere in the exec chain) to prove
// capture does not depend on a shell inside the image. It prints one line per second to
// stdout AND stderr for 30s, so live growth and latency are observable while the VM runs.
//
// The console-capture code path is identical on macOS (Hypervisor.framework) and Linux
// (KVM) — only the fs.watch backend differs (FSEvents vs inotify), so treat the watcher
// result as informational on macOS.
//
// Reports: (a) console.log path pattern, (b) entrypoint stdout captured, (c) stderr
// captured, (d) live growth + latency, (e) fs.watch fires on appends, (f) boot-noise
// volume/format, (g) box.exec() output included or not, (h) file survives stop().

const LINES = 30;
const EXEC_MARKER = "EXEC_MARKER_e7c1a4";
const POLL_MS = 200;
const WATCH_WINDOW_MS = 15_000;

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function readConsole(path: string): string {
  try {
    return readFileSync(path, "utf-8");
  } catch {
    return "";
  }
}

async function main() {
  const boxliteHome = process.env.BOXLITE_HOME ?? join(homedir(), ".boxlite");
  console.log(`BOXLITE_HOME    : ${boxliteHome}`);
  console.log(`Platform        : ${process.platform} (${process.arch})  runtime: Bun ${Bun.version}\n`);

  // awk is exec'd directly — no /bin/sh in the chain. fflush() defeats stdio buffering so
  // per-second latency is measurable rather than one flush at exit.
  const awkProgram =
    `BEGIN { for (i = 0; i < ${LINES}; i++) { ` +
    `print "stdout line " i; fflush(); ` +
    `print "stderr line " i > "/dev/stderr"; fflush(); ` +
    `system("sleep 1") } }`;

  // Unique per run: autoRemove: false keeps the box record after stop(), so a fixed
  // name collides with the previous run's stopped box.
  const box = new SimpleBox({
    image: "alpine:latest",
    name: `console-log-test-${Date.now().toString(36)}`,
    entrypoint: ["/usr/bin/awk", awkProgram],
    autoRemove: false,
  });

  let watcher: FSWatcher | null = null;
  let watchEvents = 0;

  try {
    // First exec lazily boots the VM (and starts the entrypoint); box.id is valid after it.
    const bootStart = Date.now();
    await box.exec("true");
    const boxId = box.id;
    const boxDir = join(boxliteHome, "boxes", boxId);
    const consolePath = join(boxDir, "console.log");
    const shimStderrPath = join(boxDir, "shim.stderr");

    console.log(`Box id          : ${boxId}`);
    console.log(`(a) console.log : ${consolePath}`);
    console.log(`    exists      : ${existsSync(consolePath) ? "YES" : "NO"}\n`);

    watcher = watch(boxDir, (_event, filename) => {
      if (filename === "console.log") watchEvents++;
    });

    // Poll while the entrypoint is still emitting: record when each "stdout line N" first
    // becomes visible, and how the file size moves, to judge live growth vs flush-on-stop.
    const firstSeen = new Map<number, number>();
    const sizeSamples: Array<{ t: number; size: number }> = [];
    const pollDeadline = Date.now() + WATCH_WINDOW_MS;
    let execIssued = false;

    while (Date.now() < pollDeadline) {
      const content = readConsole(consolePath);
      sizeSamples.push({ t: Date.now() - bootStart, size: content.length });

      for (let i = 0; i < LINES; i++) {
        if (!firstSeen.has(i) && content.includes(`stdout line ${i}`)) {
          firstSeen.set(i, Date.now());
        }
      }

      // Once the entrypoint is visibly running, check whether exec() output also lands
      // on the console (expected: no — exec streams go to the API, not the VM console).
      if (!execIssued && firstSeen.size >= 2) {
        execIssued = true;
        await box.exec("echo", EXEC_MARKER);
      }

      await sleep(POLL_MS);
    }

    const content = readConsole(consolePath);
    const lines = content.split("\n");

    const stdoutSeen = firstSeen.size;
    const stderrSeen = Array.from({ length: LINES }, (_, i) => i)
      .filter((i) => content.includes(`stderr line ${i}`)).length;

    // Live growth: multiple lines became visible at distinct times ≈ 1s apart while the
    // VM was still running. Flush-on-stop would show 0 or 1 distinct arrival times.
    const arrivals = Array.from(firstSeen.entries()).sort(([a], [b]) => a - b);
    const gaps = arrivals.slice(1).map(([, t], idx) => t - arrivals[idx][1]);
    const medianGap = gaps.length
      ? gaps.slice().sort((a, b) => a - b)[Math.floor(gaps.length / 2)]
      : null;

    const firstAppLine = lines.findIndex((l) => l.includes("stdout line 0"));
    const bootNoise = firstAppLine >= 0 ? lines.slice(0, Math.min(firstAppLine, 20)) : lines.slice(0, 20);

    console.log("================ RESULT (console.log capture) ================");
    console.log(`(b) entrypoint stdout captured : ${stdoutSeen > 0 ? "YES ✅" : "NO ❌"} (${stdoutSeen}/${LINES} lines seen in window)`);
    console.log(`(c) entrypoint stderr captured : ${stderrSeen > 0 ? "YES ✅" : "NO ❌"} (${stderrSeen}/${LINES} lines seen in window)`);
    console.log(`(d) live growth                : ${arrivals.length >= 5 ? "YES ✅" : "NO/INCONCLUSIVE ❌"} ` +
                `(${arrivals.length} distinct arrivals, median inter-arrival ${medianGap === null ? "n/a" : medianGap + "ms"} — expect ~1000ms)`);
    console.log(`(e) fs.watch fired on appends  : ${watchEvents > 0 ? `YES ✅ (${watchEvents} events)` : "NO ⚠️ (informational on macOS)"}`);
    console.log(`(f) boot noise before app line : ${firstAppLine >= 0 ? firstAppLine : "app line not found; total " + lines.length} lines`);
    console.log(`(g) box.exec() output on console: ${content.includes(EXEC_MARKER) ? "YES (exec IS on console)" : "NO (exec output separate) ✅"}`);
    console.log("---------------------------------------------------------------");
    console.log("First lines of console.log (boot-noise format sample):");
    for (const l of bootNoise.slice(0, 20)) console.log(`  | ${l}`);
    if (existsSync(shimStderrPath)) {
      const shim = readFileSync(shimStderrPath, "utf-8");
      console.log(`shim.stderr: ${shim.length} bytes${shim.length ? ` — first line: ${shim.split("\n")[0]}` : ""}`);
    } else {
      console.log("shim.stderr: not present");
    }

    watcher.close();
    watcher = null;
    await box.stop();

    const survives = existsSync(consolePath);
    console.log(`(h) console.log survives stop(): ${survives ? "YES ✅" : "NO ❌"}` +
                (survives ? ` (${statSync(consolePath).size} bytes)` : ""));
    console.log("===============================================================");
    console.log("(b)+(c)+(d) all YES  -> ship logs by tailing console.log; no shell wrapper,");
    console.log("                        no log volume mount needed in vm-manager.");
    console.log("(d) NO               -> console flushes on stop; live tail needs another path.");
  } finally {
    watcher?.close();
    await box.stop().catch(() => {});
  }
}

main();
