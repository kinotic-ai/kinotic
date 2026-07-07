import { SimpleBox, getJsBoxlite } from "@boxlite-ai/boxlite";
import { homedir } from "node:os";
import { join } from "node:path";

// Does boxlite support run-to-completion (batch) workloads — images whose entrypoint does
// its work and exits, like kinotic-migration? The autoremove-restart probe saw an
// instant-exit entrypoint fail create with youki's init-ready handshake error
// (spawn_failed / "waiting for init ready" BrokenChannel). Three competing explanations:
//   1. entrypoints must outlive the boot handshake (hard constraint)
//   2. a race the integration loses only for near-instant exits (bug, flaky)
//   3. create succeeded, the box ran to completion, and the probe's exec("true")
//      auto-REBOOTED it — and the re-boot lost the same race
// Boxes record an exit.previous file, so run-to-completion looks intended. This probe
// separates the explanations:
//
//  A. entrypoint exits after ~3s. Boot via getId() — never exec, so nothing can re-boot
//     it — then poll getInfo only. Does the box transition to stopped on its own, and
//     with what status? This is the kinotic-migration case.
//  B. instant-exit entrypoint, 5 fresh boxes. Count create failures — deterministic
//     constraint (5/5) vs race (flaky) vs fine (0/5, blaming the old probe's exec).
//  C. ~0.5s entrypoint, 3 boxes — the middle ground, sizes the hazard window if B fails.
//
// All boxes autoRemove:false so completed boxes can be inspected; every name is removed
// in cleanup regardless of outcome.

const RUN = Date.now().toString(36);
const POLL_MS = 500;
const COMPLETE_TIMEOUT_MS = 60_000;

const runtime = getJsBoxlite().withDefaultConfig();

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForCompletion(name: string): Promise<string> {
  const start = Date.now();
  while (Date.now() - start < COMPLETE_TIMEOUT_MS) {
    const info = await runtime.getInfo(name);
    if (info && !info.state.running) {
      return info.state.status;
    }
    await sleep(POLL_MS);
  }
  return "(still running after timeout)";
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

// Boots a fresh box with the given entrypoint via getId() (never exec) and reports
// either the natural post-completion status or the create failure.
async function runBatch(name: string, entrypoint: string[]): Promise<{ created: boolean; status?: string; error?: string }> {
  const box = new SimpleBox({ image: "alpine:latest", name, autoRemove: false, runtime, entrypoint });
  try {
    await box.getId();
  } catch (error) {
    return { created: false, error: String(error).split("\n")[0] };
  }
  return { created: true, status: await waitForCompletion(name) };
}

async function main() {
  const boxliteHome = process.env.BOXLITE_HOME ?? join(homedir(), ".boxlite");
  const boxliteVersion = (await import("@boxlite-ai/boxlite/package.json")).default.version as string;
  console.log(`BOXLITE_HOME    : ${boxliteHome}`);
  console.log(`boxlite version : ${boxliteVersion}`);
  console.log(`Platform        : ${process.platform} (${process.arch})  runtime: Bun ${Bun.version}\n`);

  // ---- Phase A: the kinotic-migration shape — work for a few seconds, then exit ----
  console.log(`=== Phase A: entrypoint works ~3s then exits — natural completion ===`);
  const batchName = `batch-${RUN}`;
  let phaseA: { created: boolean; status?: string; error?: string } = { created: false };
  try {
    phaseA = await runBatch(batchName, ["sh", "-c", "echo done > /root/result.txt && sleep 3"]);
    if (!phaseA.created) {
      console.log(`  create FAILED: ${phaseA.error}`);
    } else {
      console.log(`  status after natural completion: ${phaseA.status}`);
      // Reading the result file requires exec, which re-boots the completed box —
      // that behavior is itself part of the report
      const reread = new SimpleBox({ image: "alpine:latest", name: batchName, autoRemove: false, reuseExisting: true, runtime });
      const result = await reread.exec("cat", ["/root/result.txt"]);
      console.log(`  exec after completion re-boots the box; /root/result.txt: ${result.stdout.trim() === "done" ? "PERSISTED" : `(${result.stdout.trim() || result.stderr.trim()})`}`);
      await reread.stop();
    }
  } finally {
    await removeIfPresent(batchName);
  }
  console.log();

  // ---- Phase B: instant exit, 5 attempts — deterministic constraint or race? -------
  console.log(`=== Phase B: instant-exit entrypoint x5 ===`);
  const instantResults: string[] = [];
  for (let i = 0; i < 5; i++) {
    const name = `instant-${RUN}-${i}`;
    try {
      const r = await runBatch(name, ["sh", "-c", "true"]);
      instantResults.push(r.created ? `ok (${r.status})` : `create failed: ${r.error}`);
      console.log(`  attempt ${i + 1}: ${instantResults[i]}`);
    } finally {
      await removeIfPresent(name);
    }
  }
  const instantFailures = instantResults.filter((r) => r.startsWith("create failed")).length;
  console.log();

  // ---- Phase C: ~0.5s exit, 3 attempts — sizing the hazard window ------------------
  console.log(`=== Phase C: ~0.5s entrypoint x3 ===`);
  const halfSecResults: string[] = [];
  for (let i = 0; i < 3; i++) {
    const name = `halfsec-${RUN}-${i}`;
    try {
      const r = await runBatch(name, ["sh", "-c", "sleep 0.5"]);
      halfSecResults.push(r.created ? `ok (${r.status})` : `create failed: ${r.error}`);
      console.log(`  attempt ${i + 1}: ${halfSecResults[i]}`);
    } finally {
      await removeIfPresent(name);
    }
  }
  const halfSecFailures = halfSecResults.filter((r) => r.startsWith("create failed")).length;
  console.log();

  // ---- Report -----------------------------------------------------------------------
  console.log("=== REPORT ===");
  console.log(`(a) ~3s batch workload completes naturally:  ${phaseA.created ? `YES — final status '${phaseA.status}'` : `NO — ${phaseA.error}`}`);
  console.log(`(b) instant-exit create failures:            ${instantFailures}/5 ${instantFailures === 5 ? "(deterministic constraint)" : instantFailures > 0 ? "(RACE — flaky)" : "(fine — old probe's failure was something else)"}`);
  console.log(`(c) ~0.5s-exit create failures:              ${halfSecFailures}/3`);
}

main().catch((error) => {
  console.error("PROBE FAILED:", error);
  process.exit(1);
});
