import { SimpleBox, getJsBoxlite } from "@boxlite-ai/boxlite";
import { existsSync, readdirSync, statSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

// What does autoRemove actually do in practice? The SDK doc says "Remove box when
// stopped (default: true)" and JsBox exposes start() ("Start or restart a stopped box")
// and stop() ("Stop the box (preserves state for restart)"). If a stopped box with
// autoRemove:false keeps its rootfs disk AND can be restarted with that state intact,
// the vm-manager gains a stop/restart lifecycle option — a workload could be restarted
// in place instead of destroyed and recreated, which would change the workload contract.
//
// Phases:
//  1. autoRemove:false — write a marker into the VM rootfs (/root, non-tmpfs) and one
//     into /tmp (tmpfs, expected to vanish), stop, then inspect what remains: the
//     registry record (runtime.getInfo) and the on-disk box dir ($BOXLITE_HOME/boxes/<id>).
//  2. restart — exec on a reused SimpleBox handle against the STOPPED box (does exec
//     auto-start?); if that fails, runtime.get(name).start() then exec. Report whether
//     /root marker survived, /tmp marker vanished, and whether the box id changed.
//  3. autoRemove:true — same stop, then check record and files are actually removed.
//  4. entrypoint — a box whose entrypoint appends a marker to /root/boots.log; after a
//     stop/restart the file should hold a second line, proving the recorded entrypoint
//     re-runs on every boot (restart really restarts the workload, not just the VM).
//
// Memory/process state is expected to be lost either way — stop() is not a snapshot;
// the question here is purely rootfs-disk persistence and restartability.

const RUN = Date.now().toString(36);
const KEEP_NAME = `keep-${RUN}`;
const DROP_NAME = `drop-${RUN}`;
const MARKER = `MARKER_${RUN}`;

function dirSummary(dir: string): string {
  if (!existsSync(dir)) {
    return "(gone)";
  }
  const entries: string[] = [];
  let total = 0;
  const walk = (d: string, prefix: string) => {
    for (const name of readdirSync(d)) {
      const p = join(d, name);
      const st = statSync(p);
      if (st.isDirectory()) {
        walk(p, `${prefix}${name}/`);
      } else {
        total += st.size;
        entries.push(`    ${prefix}${name}  (${(st.size / 1024 / 1024).toFixed(1)} MiB)`);
      }
    }
  };
  walk(dir, "");
  return `${(total / 1024 / 1024).toFixed(1)} MiB total\n${entries.join("\n")}`;
}

async function main() {
  const boxliteHome = process.env.BOXLITE_HOME ?? join(homedir(), ".boxlite");
  const boxliteVersion = (await import("@boxlite-ai/boxlite/package.json")).default.version as string;
  console.log(`BOXLITE_HOME    : ${boxliteHome}`);
  console.log(`boxlite version : ${boxliteVersion}`);
  console.log(`Platform        : ${process.platform} (${process.arch})  runtime: Bun ${Bun.version}\n`);

  const runtime = getJsBoxlite().withDefaultConfig();

  // ---- Phase 1: autoRemove:false — what survives stop()? -------------------------
  console.log(`=== Phase 1: create '${KEEP_NAME}' with autoRemove:false, write state, stop ===`);
  const keep = new SimpleBox({ image: "alpine:latest", name: KEEP_NAME, autoRemove: false, runtime });
  await keep.exec("sh", "-c", `echo ${MARKER} > /root/state.txt && echo ${MARKER} > /tmp/state.txt && cat /root/state.txt`);
  const keepId = keep.id;
  const keepDir = join(boxliteHome, "boxes", keepId);
  console.log(`box id          : ${keepId}`);
  console.log(`box dir before stop: ${dirSummary(keepDir)}\n`);

  await keep.stop();

  const stoppedInfo = await runtime.getInfo(KEEP_NAME);
  console.log(`after stop:`);
  console.log(`  getInfo status : ${stoppedInfo ? `${stoppedInfo.state.status} (running=${stoppedInfo.state.running})` : "(no record)"}`);
  console.log(`  box dir        : ${dirSummary(keepDir)}\n`);

  // ---- Phase 2: restart the stopped box ------------------------------------------
  console.log(`=== Phase 2: restart '${KEEP_NAME}' and check state ===`);
  let restartPath = "";
  let restarted: SimpleBox | null = null;

  // 2a: does a reused SimpleBox handle auto-start a stopped box on exec?
  try {
    const reused = new SimpleBox({ image: "alpine:latest", name: KEEP_NAME, autoRemove: false, reuseExisting: true, runtime });
    await reused.exec("true");
    restarted = reused;
    restartPath = "SimpleBox(reuseExisting).exec auto-started the stopped box";
  } catch (error) {
    console.log(`  exec on stopped box via reused SimpleBox failed: ${error}`);
    // 2b: explicit restart through the runtime handle
    const jsbox = await runtime.get(KEEP_NAME);
    if (!jsbox) {
      throw new Error(`box ${KEEP_NAME} not found`);
    }
    console.log(`  runtime.get -> JsBox methods: ${Object.getOwnPropertyNames(Object.getPrototypeOf(jsbox)).join(" ")}`);
    await jsbox.start();
    const reused = new SimpleBox({ image: "alpine:latest", name: KEEP_NAME, autoRemove: false, reuseExisting: true, runtime });
    restarted = reused;
    restartPath = "runtime.get(name).start() restarted it, exec via reused SimpleBox";
  }

  const rootMarker = await restarted.exec("cat", "/root/state.txt");
  const tmpMarker = await restarted.exec("sh", "-c", "cat /tmp/state.txt 2>&1 || true");
  const restartedId = await restarted.getId();
  console.log(`  restart path   : ${restartPath}`);
  console.log(`  /root/state.txt: ${rootMarker.stdout.trim() === MARKER ? "SURVIVED" : `LOST (got: ${rootMarker.stdout.trim() || rootMarker.stderr.trim()})`}`);
  console.log(`  /tmp/state.txt : ${tmpMarker.stdout.trim() === MARKER ? "survived (tmpfs persisted?!)" : "gone (expected — tmpfs)"}`);
  console.log(`  box id         : ${restartedId} ${restartedId === keepId ? "(unchanged)" : `(CHANGED from ${keepId})`}\n`);

  // ---- Phase 2c: the explicit restart API — runtime.get(name).start() ------------
  console.log(`=== Phase 2c: stop again, restart via runtime.get('${KEEP_NAME}').start() ===`);
  await restarted.stop();
  const jsbox = await runtime.get(KEEP_NAME);
  if (!jsbox) {
    throw new Error(`box ${KEEP_NAME} not found`);
  }
  await jsbox.start();
  // Checked via getInfo BEFORE any exec — exec would auto-boot and mask whether start()
  // did the work
  const afterStart = await runtime.getInfo(KEEP_NAME);
  console.log(`  running after start(), before any exec: ${afterStart?.state.running}`);
  const viaStart = new SimpleBox({ image: "alpine:latest", name: KEEP_NAME, autoRemove: false, reuseExisting: true, runtime });
  const marker2 = await viaStart.exec("cat", "/root/state.txt");
  console.log(`  /root/state.txt: ${marker2.stdout.trim() === MARKER ? "SURVIVED" : `LOST (got: ${marker2.stdout.trim() || marker2.stderr.trim()})`}\n`);
  await viaStart.stop();
  await runtime.remove(KEEP_NAME, true);

  // ---- Phase 3: autoRemove:true — is everything actually removed? ----------------
  console.log(`=== Phase 3: create '${DROP_NAME}' with autoRemove:true, stop ===`);
  const drop = new SimpleBox({ image: "alpine:latest", name: DROP_NAME, autoRemove: true, runtime });
  await drop.exec("true");
  const dropId = drop.id;
  const dropDir = join(boxliteHome, "boxes", dropId);
  console.log(`box id          : ${dropId}`);
  await drop.stop();
  const dropInfo = await runtime.getInfo(DROP_NAME);
  console.log(`after stop:`);
  console.log(`  getInfo        : ${dropInfo ? `record still present (${dropInfo.state.status})` : "(no record — removed)"}`);
  console.log(`  box dir        : ${dirSummary(dropDir)}\n`);

  // ---- Phase 4: does the entrypoint re-run on restart? ----------------------------
  console.log(`=== Phase 4: entrypoint re-run on restart ===`);
  const EP_NAME = `entry-${RUN}`;

  // The entrypoint runs asynchronously after boot, so exec can race it — poll until the
  // expected boot count (or timeout) before judging
  async function bootCount(box: SimpleBox, expected: number): Promise<number> {
    let count = 0;
    for (let i = 0; i < 50 && count < expected; i++) {
      const result = await box.exec("sh", "-c", "wc -l < /root/boots.log 2>/dev/null || echo 0");
      count = Number(result.stdout.trim());
      if (count < expected) {
        await new Promise((resolve) => setTimeout(resolve, 200));
      }
    }
    return count;
  }

  // The entrypoint must outlive the boot handshake: one that exits immediately fails the
  // whole boot with spawn_failed / "waiting for init ready" BrokenChannel — so append the
  // marker, then stay alive like a real workload
  const entrypoint = ["sh", "-c", "echo boot >> /root/boots.log && sleep 300"];
  let firstBoot = 0;
  let secondBoot = 0;
  try {
    const entry = new SimpleBox({ image: "alpine:latest", name: EP_NAME, autoRemove: false, runtime, entrypoint });
    await entry.exec("true");
    firstBoot = await bootCount(entry, 1);
    console.log(`  boots.log lines after first boot: ${firstBoot}`);
    await entry.stop();

    // Rebooted through the explicit handle rather than an implicit exec-boot: boxlite
    // PR 988 refuses exec against a box whose own command has run, and start() is the
    // call BoxliteProvider.restart() makes anyway
    const stopped = await runtime.get(EP_NAME);
    if (!stopped) {
      throw new Error(`box ${EP_NAME} not found`);
    }
    await stopped.start();
    const entryAgain = new SimpleBox({ image: "alpine:latest", name: EP_NAME, autoRemove: false, reuseExisting: true, runtime });
    secondBoot = await bootCount(entryAgain, 2);
    console.log(`  boots.log lines after restart   : ${secondBoot}\n`);
    await entryAgain.stop();
  } finally {
    if (await runtime.getInfo(EP_NAME)) {
      await runtime.remove(EP_NAME, true);
    }
  }

  // ---- Report ---------------------------------------------------------------------
  console.log("=== REPORT ===");
  console.log(`(a) autoRemove:false + stop keeps registry record: ${stoppedInfo ? "YES" : "NO"}`);
  console.log(`(b) autoRemove:false + stop keeps disk files:      ${existsSync(keepDir) ? "checked above" : "see Phase 1 output"}`);
  console.log(`(c) stopped box restartable:                       see Phase 2 restart path`);
  console.log(`(d) rootfs state survives restart:                 ${rootMarker.stdout.trim() === MARKER ? "YES" : "NO"}`);
  console.log(`(e) autoRemove:true + stop removes record+files:   ${!dropInfo && !existsSync(dropDir) ? "YES" : "NO / partial"}`);
  console.log(`(f) runtime.get(name).start() boots a stopped box: ${afterStart?.state.running ? "YES" : "NO"}`);
  console.log(`(g) entrypoint re-runs on restart:                 ${secondBoot >= 2 ? "YES" : firstBoot === 0 ? "INCONCLUSIVE (never ran on first boot)" : "NO"}`);
}

main().catch((error) => {
  console.error("PROBE FAILED:", error);
  process.exit(1);
});
