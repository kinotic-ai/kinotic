import { SimpleBox, getJsBoxlite } from "@boxlite-ai/boxlite";
import { homedir } from "node:os";
import { join } from "node:path";

// Does boxlite support run-to-completion (batch) workloads — images whose entrypoint does
// its work and exits, like kinotic-migration? The autoremove-restart probe saw an
// instant-exit entrypoint fail a boot with youki's init-ready handshake error
// (spawn_failed / "waiting for init ready" BrokenChannel).
//
// A previous version of this probe established that box creation is lazy at the engine
// level too: create/getId() only writes the registry record (status 'configured') and the
// VM does not boot until first exec. So this probe boots explicitly via
// runtime.get(name).start() — no exec anywhere near the boot — and then only observes
// getInfo, so nothing can accidentally (re)boot a completed box.
//
//  A. entrypoint works ~3s then exits. Boot via start(), poll getInfo: does the box
//     leave 'running' on its own, and what is the final status? The kinotic-migration case.
//  B. instant-exit entrypoint, 5 fresh boxes: does start() fail deterministically (hard
//     constraint), sometimes (race), or never?
//  C. ~0.5s entrypoint, 3 boxes — sizes the hazard window if B fails.
//
// All boxes autoRemove:false so completed boxes can be inspected; every name is removed
// in cleanup regardless of outcome.

const RUN = Date.now().toString(36);
const POLL_MS = 250;
const COMPLETE_TIMEOUT_MS = 60_000;

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

interface BatchResult {
  createdStatus?: string;
  bootError?: string;
  observed: string[];      // distinct state.status values seen while polling, in order
  finalStatus?: string;
}

// Creates a fresh box, boots it via runtime.get(name).start() (never exec), then polls
// getInfo until it leaves 'running' or times out.
async function runBatch(name: string, entrypoint: string[]): Promise<BatchResult> {
  const result: BatchResult = { observed: [] };

  const box = new SimpleBox({ image: "alpine:latest", name, autoRemove: false, runtime, entrypoint });
  await box.getId();
  result.createdStatus = (await runtime.getInfo(name))?.state.status;

  try {
    const jsbox = await runtime.get(name);
    await jsbox.start();
  } catch (error) {
    result.bootError = String(error).split("\n")[0];
    return result;
  }

  const start = Date.now();
  let sawRunning = false;
  while (Date.now() - start < COMPLETE_TIMEOUT_MS) {
    const info = await runtime.getInfo(name);
    const status = info?.state.status ?? "(no record)";
    if (result.observed[result.observed.length - 1] !== status) {
      result.observed.push(status);
    }
    sawRunning ||= info?.state.running === true;
    if (sawRunning && !info?.state.running) {
      result.finalStatus = status;
      return result;
    }
    await sleep(POLL_MS);
  }
  result.finalStatus = sawRunning ? "(still running after timeout)" : "(never seen running)";
  return result;
}

function describe(r: BatchResult): string {
  if (r.bootError) {
    return `boot FAILED: ${r.bootError}`;
  }
  return `created '${r.createdStatus}' -> observed [${r.observed.join(" -> ")}] -> final '${r.finalStatus}'`;
}

async function main() {
  const boxliteHome = process.env.BOXLITE_HOME ?? join(homedir(), ".boxlite");
  const boxliteVersion = (await import("@boxlite-ai/boxlite/package.json")).default.version as string;
  console.log(`BOXLITE_HOME    : ${boxliteHome}`);
  console.log(`boxlite version : ${boxliteVersion}`);
  console.log(`Platform        : ${process.platform} (${process.arch})  runtime: Bun ${Bun.version}\n`);

  // ---- Phase A: the kinotic-migration shape — work for a few seconds, then exit ----
  console.log(`=== Phase A: boot via start(), entrypoint works ~3s then exits ===`);
  const batchName = `batch-${RUN}`;
  let phaseA: BatchResult = { observed: [] };
  try {
    phaseA = await runBatch(batchName, ["sh", "-c", "echo done > /root/result.txt && sleep 3"]);
    console.log(`  ${describe(phaseA)}`);
    if (!phaseA.bootError) {
      // Reading the result file requires exec, which boots the box again — that
      // behavior is itself part of the report
      const reread = new SimpleBox({ image: "alpine:latest", name: batchName, autoRemove: false, reuseExisting: true, runtime });
      const fileCheck = await reread.exec("cat", ["/root/result.txt"]);
      console.log(`  exec after completion boots again; /root/result.txt: ${fileCheck.stdout.trim() === "done" ? "PERSISTED" : `(${fileCheck.stdout.trim() || fileCheck.stderr.trim()})`}`);
      await reread.stop();
    }
  } finally {
    await removeIfPresent(batchName);
  }
  console.log();

  // ---- Phase B: instant exit, 5 attempts — deterministic constraint or race? -------
  console.log(`=== Phase B: instant-exit entrypoint x5, booted via start() ===`);
  const instant: BatchResult[] = [];
  for (let i = 0; i < 5; i++) {
    const name = `instant-${RUN}-${i}`;
    try {
      const r = await runBatch(name, ["sh", "-c", "true"]);
      instant.push(r);
      console.log(`  attempt ${i + 1}: ${describe(r)}`);
    } finally {
      await removeIfPresent(name);
    }
  }
  const instantFailures = instant.filter((r) => r.bootError).length;
  console.log();

  // ---- Phase C: ~0.5s exit, 3 attempts — sizing the hazard window ------------------
  console.log(`=== Phase C: ~0.5s entrypoint x3, booted via start() ===`);
  const halfSec: BatchResult[] = [];
  for (let i = 0; i < 3; i++) {
    const name = `halfsec-${RUN}-${i}`;
    try {
      const r = await runBatch(name, ["sh", "-c", "sleep 0.5"]);
      halfSec.push(r);
      console.log(`  attempt ${i + 1}: ${describe(r)}`);
    } finally {
      await removeIfPresent(name);
    }
  }
  const halfSecFailures = halfSec.filter((r) => r.bootError).length;
  console.log();

  // ---- Report -----------------------------------------------------------------------
  console.log("=== REPORT ===");
  console.log(`(a) create/getId boots the VM:               ${phaseA.createdStatus === "configured" ? "NO — record only, status 'configured'" : `status after create: '${phaseA.createdStatus}'`}`);
  console.log(`(b) ~3s batch workload completes naturally:  ${phaseA.bootError ? `boot failed: ${phaseA.bootError}` : `final status '${phaseA.finalStatus}'`}`);
  console.log(`(c) instant-exit boot failures:              ${instantFailures}/5 ${instantFailures === 5 ? "(deterministic constraint)" : instantFailures > 0 ? "(RACE — flaky)" : "(no failures)"}`);
  console.log(`(d) ~0.5s-exit boot failures:                ${halfSecFailures}/3`);
}

main().catch((error) => {
  console.error("PROBE FAILED:", error);
  process.exit(1);
});
