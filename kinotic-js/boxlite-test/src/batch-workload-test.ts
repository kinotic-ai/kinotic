import { SimpleBox, getJsBoxlite } from "@boxlite-ai/boxlite";
import { mkdtempSync, readFileSync, readdirSync, statSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { join } from "node:path";

// How do run-to-completion (batch) workloads — images whose entrypoint does its work and
// exits, like kinotic-migration — behave in boxlite?
//
// Before boxlite PR 988 the answer was "invisibly": the entrypoint exited, the VM stayed
// up, getInfo kept reporting running=true forever, and exec against that zombie failed
// with "Container init process exited — cannot exec ... container status: `Stopped`" —
// the only external completion signal. Filed as boxlite#933.
//
// PR 988 makes a box's main command its init (PID 1) and hands the lifecycle to it:
//   - a box stops when its main command exits; a host-side watcher marks it Stopped and
//     records the exit code in containers/<cid>/exit.json (ExitRecord)
//   - exec / cp / metrics against a *finished job* are refused rather than silently
//     restarting it, and a handle whose VM has died refuses instead of serving the corpse
//   - the exit code is surfaced on CLI `inspect .State.ExitCode` and on the REST wire;
//     the SDK surface (box.wait(), an exit code on box info) is a designed follow-up
//
// That last point is why this probe still matters to vm-manager: BoxliteProvider observes
// workloads through the Node SDK's getInfo(), so the open questions are what the SDK can
// see of the transition and whether the exit code is reachable at all from this side.
//
// Phases:
//  A. does a ~3s entrypoint transition the box on its own, to which status, how fast?
//  C. exec / metrics / copyOut / stop against that finished job — refused, and how?
//  D. explicit start() on the finished job re-runs the command with the rootfs intact
//     (PR 988 refuses the implicit exec-boot phase A used to re-boot through)
//  B. an entrypoint exiting 42 — is that code reachable from getInfo() or from disk?
//  E. instant-exit entrypoint x3 — does start() itself ever fail, or land clean?
//
// All boxes autoRemove:false; every name is removed in cleanup regardless of outcome.

const RUN = Date.now().toString(36);
const POLL_MS = 250;
const IMAGE = "alpine:latest";
const BOXLITE_HOME = process.env.BOXLITE_HOME ?? join(homedir(), ".boxlite");

const runtime = getJsBoxlite().withDefaultConfig();

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
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

// boxlite errors carry multi-line Rust context; the first line is the message
function firstLine(error: unknown): string {
  return String(error).split("\n")[0]!;
}

interface JobObservation {
  /** Distinct state.status values seen, in order. */
  statuses: string[];
  /** Whether the box left the running state without anyone stopping it. */
  stopped: boolean;
  /** Milliseconds from the first poll to the transition, or -1 if it never came. */
  transitionMs: number;
  /** getInfo() when the window closed, as JSON so a field this probe predates still shows. */
  terminal: string;
}

// Polls getInfo for windowMs, stopping early once a box that was running no longer is
async function observe(name: string, windowMs: number): Promise<JobObservation> {
  const statuses: string[] = [];
  const start = Date.now();
  let sawRunning = false;
  let stopped = false;
  let transitionMs = -1;
  while (Date.now() - start < windowMs) {
    const info = await runtime.getInfo(name);
    const status = info?.state.status ?? "(no record)";
    if (statuses[statuses.length - 1] !== status) {
      statuses.push(status);
    }
    sawRunning ||= info?.state.running === true;
    if (sawRunning && !info?.state.running) {
      stopped = true;
      transitionMs = Date.now() - start;
      break;
    }
    await sleep(POLL_MS);
  }
  const terminal = await runtime.getInfo(name);
  return { statuses, stopped, transitionMs, terminal: JSON.stringify(terminal) };
}

// create/getId() only writes the registry record (status 'configured'); start() is what
// boots the VM and runs the command
async function bootJob(name: string, command: { entrypoint?: string[]; cmd?: string[] }): Promise<SimpleBox> {
  const box = new SimpleBox({ image: IMAGE, name, autoRemove: false, runtime, ...command });
  await box.getId();
  const jsbox = await runtime.get(name);
  if (!jsbox) {
    throw new Error(`box ${name} not found`);
  }
  await jsbox.start();
  return box;
}

// Sweeps the box's on-disk state for whatever records its exit. PR 988 writes
// containers/<cid>/exit.json (ExitRecord); 0.9.7 wrote exit.previous in the box dir.
// Both are internal layout, so the roots are swept rather than addressed directly.
function findExitRecords(boxId: string): Array<{ path: string; content: string }> {
  const found: Array<{ path: string; content: string }> = [];
  const walk = (dir: string, depth: number) => {
    if (depth > 4) {
      return;
    }
    let entries: string[];
    try {
      entries = readdirSync(dir);
    } catch {
      return;
    }
    for (const name of entries) {
      const path = join(dir, name);
      if (statSync(path).isDirectory()) {
        walk(path, depth + 1);
      } else if (/^exit\b/.test(name)) {
        found.push({ path, content: readFileSync(path, "utf-8").trim().slice(0, 200) });
      }
    }
  };
  walk(join(BOXLITE_HOME, "boxes", boxId), 0);
  walk(join(BOXLITE_HOME, "containers"), 0);
  return found;
}

async function main() {
  const boxliteVersion = (await import("@boxlite-ai/boxlite/package.json")).default.version as string;
  console.log(`BOXLITE_HOME    : ${BOXLITE_HOME}`);
  console.log(`boxlite version : ${boxliteVersion}`);
  console.log(`Platform        : ${process.platform} (${process.arch})  runtime: Bun ${Bun.version}\n`);

  // ---- Phase A: does the box transition when its entrypoint exits? -----------------
  console.log(`=== Phase A: ~3s entrypoint — does the box stop on its own? ===`);
  const batchName = `batch-${RUN}`;
  let phaseA: JobObservation = { statuses: [], stopped: false, transitionMs: -1, terminal: "null" };
  let execError = "(not reached)";
  let metricsError = "(not reached)";
  let copyOutError = "(not reached)";
  let stopError = "(not reached)";
  let runsAfterRestart = "";
  try {
    const box = await bootJob(batchName, {
      entrypoint: ["sh", "-c", "echo run >> /root/runs.log && sleep 3"],
    });

    // 30s window for a 3s entrypoint: generous enough that a slow watcher still reads as
    // a transition rather than as the old zombie
    phaseA = await observe(batchName, 30_000);
    console.log(`  statuses         : [${phaseA.statuses.join(" -> ")}]`);
    console.log(`  stopped by itself: ${phaseA.stopped ? `YES after ${phaseA.transitionMs}ms` : "NO (zombie — boxlite#933 behavior)"}`);
    console.log(`  terminal getInfo : ${phaseA.terminal}`);

    // ---- Phase C: what does a finished job still serve? ---------------------------
    console.log(`\n=== Phase C: exec / metrics / copyOut / stop against the finished job ===`);
    try {
      await box.exec("true");
      execError = "(succeeded — it restarted the job or joined a zombie)";
    } catch (error) {
      execError = firstLine(error);
    }
    console.log(`  exec    : ${execError}`);

    const jsbox = await runtime.get(batchName);
    if (jsbox) {
      try {
        await jsbox.metrics();
        metricsError = "(succeeded)";
      } catch (error) {
        metricsError = firstLine(error);
      }
      console.log(`  metrics : ${metricsError}`);

      try {
        await jsbox.copyOut("/root/runs.log", join(mkdtempSync(join(tmpdir(), "boxlite-probe-")), "runs.log"));
        copyOutError = "(succeeded)";
      } catch (error) {
        copyOutError = firstLine(error);
      }
      console.log(`  copyOut : ${copyOutError}`);

      // BoxliteProvider.stop() calls stop() on a handle it cached at start, so whether a
      // dead VM refuses here decides whether that call needs a guard
      try {
        await jsbox.stop();
        stopError = "(succeeded — idempotent)";
      } catch (error) {
        stopError = firstLine(error);
      }
      console.log(`  stop    : ${stopError}`);
    }

    // ---- Phase D: explicit restart re-runs the command over the same rootfs -------
    console.log(`\n=== Phase D: runtime.get(name).start() on the finished job ===`);
    const again = await runtime.get(batchName);
    if (!again) {
      throw new Error(`box ${batchName} not found`);
    }
    await again.start();
    const reader = new SimpleBox({ image: IMAGE, name: batchName, autoRemove: false, reuseExisting: true, runtime });
    const runs = await reader.exec("sh", "-c", "wc -l < /root/runs.log");
    runsAfterRestart = runs.stdout.trim();
    console.log(`  runs.log lines after restart: ${runsAfterRestart} (expect 2 — run 1 persisted, restart ran again)`);
    await again.stop();
  } finally {
    await removeIfPresent(batchName);
  }
  console.log();

  // ---- Phase B: is the exit code reachable from this side? -------------------------
  console.log(`=== Phase B: entrypoint exits 42 — where does the code surface? ===`);
  const exitName = `exit-${RUN}`;
  let phaseB: JobObservation = { statuses: [], stopped: false, transitionMs: -1, terminal: "null" };
  let exitRecords: Array<{ path: string; content: string }> = [];
  try {
    const box = await bootJob(exitName, { entrypoint: ["sh", "-c", "sleep 1; exit 42"] });
    const boxId = await box.getId();
    phaseB = await observe(exitName, 30_000);
    console.log(`  statuses         : [${phaseB.statuses.join(" -> ")}]`);
    console.log(`  terminal getInfo : ${phaseB.terminal}`);
    exitRecords = findExitRecords(boxId);
    for (const record of exitRecords) {
      console.log(`  on disk ${record.path}: ${record.content}`);
    }
    if (exitRecords.length === 0) {
      console.log(`  on disk          : no exit record under boxes/${boxId} or containers/`);
    }
  } finally {
    await removeIfPresent(exitName);
  }
  console.log();

  // ---- Phase E: instant exit — does start() itself ever fail? ----------------------
  console.log(`=== Phase E: instant-exit entrypoint x3, booted via start() ===`);
  const bootFailures: string[] = [];
  for (let i = 0; i < 3; i++) {
    const name = `instant-${RUN}-${i}`;
    try {
      await bootJob(name, { entrypoint: ["sh", "-c", "true"] });
      const window = await observe(name, 10_000);
      console.log(`  attempt ${i + 1}: start() ok — statuses [${window.statuses.join(" -> ")}], stopped: ${window.stopped}`);
    } catch (error) {
      bootFailures.push(firstLine(error));
      console.log(`  attempt ${i + 1}: start() FAILED — ${bootFailures[bootFailures.length - 1]}`);
    } finally {
      await removeIfPresent(name);
    }
  }
  console.log();

  // ---- Report -----------------------------------------------------------------------
  console.log("=== REPORT ===");
  console.log(`(a) box stops when its entrypoint exits:    ${phaseA.stopped ? `YES after ${phaseA.transitionMs}ms — [${phaseA.statuses.join(" -> ")}]` : "NO (zombie — boxlite#933 behavior)"}`);
  console.log(`(b) exit code 42 present in getInfo():      ${phaseB.terminal.includes("42") ? "YES" : "NO — the SDK cannot read it"}`);
  console.log(`(c) exit code on disk:                      ${exitRecords.length ? exitRecords.map((r) => r.path).join(", ") : "no record found"}`);
  console.log(`(d) finished job refuses exec:              ${execError}`);
  console.log(`(e) finished job refuses metrics / copyOut: ${metricsError} / ${copyOutError}`);
  console.log(`(f) stop() on a dead VM:                    ${stopError}`);
  console.log(`(g) start() re-runs it, rootfs intact:      ${runsAfterRestart === "2" ? "YES (2 runs recorded)" : `runs.log lines: ${runsAfterRestart || "(unread)"}`}`);
  console.log(`(h) start() failures with instant-exit:     ${bootFailures.length}/3${bootFailures.length ? ` — ${bootFailures[0]}` : ""}`);
}

main().catch((error) => {
  console.error("PROBE FAILED:", error);
  process.exit(1);
});
