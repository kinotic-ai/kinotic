import { SimpleBox, getJsBoxlite } from "@boxlite-ai/boxlite";
import { homedir } from "node:os";
import { join } from "node:path";

// How do run-to-completion (batch) workloads — images whose entrypoint does its work and
// exits, like kinotic-migration — behave in boxlite? Established so far:
//   - create/getId() only writes the registry record (status 'configured'); the VM boots
//     on first exec or an explicit runtime.get(name).start()
//   - after the entrypoint exits the box does NOT transition: the VM stays up and
//     getInfo reports running=true, while the container inside is dead — exec then fails
//     with "Container init process exited — cannot exec ... container status: `Stopped`"
//
// This probe characterizes that zombie state and what it takes to work around it:
//
//  A. entrypoint appends to /root/runs.log then exits after ~3s. Boot via start(), then:
//     confirm the box stays 'running' past the entrypoint's exit (zombie), confirm exec
//     in that state fails (capture the error), then stop() and boot again via exec —
//     runs.log should then hold 2 lines: run 1 persisted, run 2 from the re-boot.
//  B. instant-exit entrypoint x3 booted via start(): does start() itself ever fail
//     (the original BrokenChannel), or does it just land in the zombie immediately?
//
// All boxes autoRemove:false; every name is removed in cleanup regardless of outcome.

const RUN = Date.now().toString(36);
const POLL_MS = 250;

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

// Polls getInfo for windowMs and returns the distinct status values seen plus whether
// running ever flipped to false
async function observe(name: string, windowMs: number): Promise<{ statuses: string[]; leftRunning: boolean }> {
  const statuses: string[] = [];
  let leftRunning = false;
  const start = Date.now();
  let sawRunning = false;
  while (Date.now() - start < windowMs) {
    const info = await runtime.getInfo(name);
    const status = info?.state.status ?? "(no record)";
    if (statuses[statuses.length - 1] !== status) {
      statuses.push(status);
    }
    sawRunning ||= info?.state.running === true;
    if (sawRunning && !info?.state.running) {
      leftRunning = true;
      break;
    }
    await sleep(POLL_MS);
  }
  return { statuses, leftRunning };
}

async function main() {
  const boxliteHome = process.env.BOXLITE_HOME ?? join(homedir(), ".boxlite");
  const boxliteVersion = (await import("@boxlite-ai/boxlite/package.json")).default.version as string;
  console.log(`BOXLITE_HOME    : ${boxliteHome}`);
  console.log(`boxlite version : ${boxliteVersion}`);
  console.log(`Platform        : ${process.platform} (${process.arch})  runtime: Bun ${Bun.version}\n`);

  // ---- Phase A: the zombie window and the stop/re-boot workaround ------------------
  console.log(`=== Phase A: ~3s entrypoint — zombie after exit, then stop + re-boot ===`);
  const batchName = `batch-${RUN}`;
  let stillRunningAfterExit = false;
  let zombieExecError = "(exec unexpectedly succeeded)";
  let runsAfterReboot = "";
  try {
    const box = new SimpleBox({
      image: "alpine:latest",
      name: batchName,
      autoRemove: false,
      runtime,
      entrypoint: ["sh", "-c", "echo run >> /root/runs.log && sleep 3"],
    });
    await box.getId();
    const jsbox = await runtime.get(batchName);
    await jsbox.start();

    // 12s window for a 3s entrypoint: if running never flips, the zombie is confirmed
    const window = await observe(batchName, 12_000);
    stillRunningAfterExit = !window.leftRunning;
    console.log(`  observed statuses over 12s: [${window.statuses.join(" -> ")}]`);
    console.log(`  still 'running' ~9s after entrypoint exit: ${stillRunningAfterExit}`);

    try {
      await box.exec("true");
      zombieExecError = "(exec unexpectedly succeeded)";
    } catch (error) {
      zombieExecError = String(error).split("\n")[0];
    }
    console.log(`  exec in that state: ${zombieExecError}`);

    // Full stop, then exec boots a fresh VM which re-runs the entrypoint; two lines in
    // runs.log = the first run persisted and the re-boot ran again
    await jsbox.stop();
    const reboot = new SimpleBox({ image: "alpine:latest", name: batchName, autoRemove: false, reuseExisting: true, runtime });
    const runs = await reboot.exec("sh", ["-c", "wc -l < /root/runs.log"]);
    runsAfterReboot = runs.stdout.trim();
    console.log(`  runs.log lines after stop + exec re-boot: ${runsAfterReboot} (expect 2)`);
    await reboot.stop();
  } finally {
    await removeIfPresent(batchName);
  }
  console.log();

  // ---- Phase B: instant exit — does start() itself ever fail? ----------------------
  console.log(`=== Phase B: instant-exit entrypoint x3, booted via start() ===`);
  const bootFailures: string[] = [];
  for (let i = 0; i < 3; i++) {
    const name = `instant-${RUN}-${i}`;
    try {
      const box = new SimpleBox({ image: "alpine:latest", name, autoRemove: false, runtime, entrypoint: ["sh", "-c", "true"] });
      await box.getId();
      try {
        const jsbox = await runtime.get(name);
        await jsbox.start();
        const window = await observe(name, 3_000);
        console.log(`  attempt ${i + 1}: start() ok — statuses [${window.statuses.join(" -> ")}], left running: ${window.leftRunning}`);
        await jsbox.stop();
      } catch (error) {
        const message = String(error).split("\n")[0];
        bootFailures.push(message);
        console.log(`  attempt ${i + 1}: start() FAILED — ${message}`);
      }
    } finally {
      await removeIfPresent(name);
    }
  }
  console.log();

  // ---- Report -----------------------------------------------------------------------
  console.log("=== REPORT ===");
  console.log(`(a) box stays 'running' after entrypoint exit:  ${stillRunningAfterExit ? "YES (zombie — completion is invisible in getInfo)" : "NO (it transitioned on its own)"}`);
  console.log(`(b) exec in the zombie state:                   ${zombieExecError}`);
  console.log(`(c) rootfs persists across stop + re-boot:      ${runsAfterReboot === "2" ? "YES (2 runs recorded)" : `runs.log lines: ${runsAfterReboot || "(unread)"}`}`);
  console.log(`(d) start() failures with instant-exit:         ${bootFailures.length}/3${bootFailures.length ? ` — ${bootFailures[0]}` : ""}`);
}

main().catch((error) => {
  console.error("PROBE FAILED:", error);
  process.exit(1);
});
