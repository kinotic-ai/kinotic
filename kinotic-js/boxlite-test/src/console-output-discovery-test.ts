import { SimpleBox } from "@boxlite-ai/boxlite";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { join } from "node:path";

// Follow-up to console-log-test.ts, which showed boxes/<id>/console.log is NOT created for
// a plain SimpleBox (while shim.stderr in the same dir IS). So krun_set_console_output is
// wired conditionally — plausibly only for detach: true, where the VM has no parent stdio
// to inherit (the "/tmp/boxlite-console-" string in the native binary hints at the
// non-detached case). This probe stops guessing and FINDS where entrypoint output goes:
//
//   1. Boot a normal box whose entrypoint prints lines containing a unique run marker
//      (awk exec'd directly — no shell), then scan $BOXLITE_HOME and /tmp/boxlite-console-*
//      for the marker while the VM runs and again after stop.
//   2. Dump the box dir listing, box.info(), and the FULL shim.stderr — boxlite's tracing
//      writes there and may log the console output path outright ("Setting console output
//      path" is a known event in the binary). BOXLITE_DEBUG=1 when launching this script
//      may make that log more verbose.
//   3. Repeat with detach: true and check boxes/<id>/console.log specifically.
//
// Outcome decides the log-shipping design: if detached boxes get a live console.log,
// vm-manager runs workloads detached and tails that file — no shell wrapper, no log volume.

const RUN_MARKER = `CONSOLE_MARKER_${Date.now().toString(36)}`;
const LINES = 30;

// Directories/files that cannot contain console text but are huge or unreadable.
const SKIP_DIRS = new Set(["images", "layers", "disks", "disk-images", "bases", "manifests", "blobs", "db"]);
const SKIP_EXT = [".qcow2", ".img", ".sock", ".db", ".pid"];
const MAX_SCAN_BYTES = 5 * 1024 * 1024;

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function* walk(dir: string): Generator<string> {
  let entries;
  try {
    entries = readdirSync(dir, { withFileTypes: true });
  } catch {
    return;
  }
  for (const e of entries) {
    const p = join(dir, e.name);
    if (e.isDirectory()) {
      if (!SKIP_DIRS.has(e.name)) yield* walk(p);
    } else if (e.isFile()) {
      yield p;
    }
  }
}

function scanForMarker(roots: string[], marker: string): string[] {
  const hits: string[] = [];
  for (const root of roots) {
    for (const file of walk(root)) {
      if (SKIP_EXT.some((ext) => file.endsWith(ext))) continue;
      try {
        if (statSync(file).size > MAX_SCAN_BYTES) continue;
        if (readFileSync(file, "utf-8").includes(marker)) hits.push(file);
      } catch {
        // unreadable (socket, permission) — not a text log we could tail anyway
      }
    }
  }
  return hits;
}

function tmpConsoleFiles(): string[] {
  try {
    return readdirSync(tmpdir())
      .filter((n) => n.startsWith("boxlite-console-"))
      .map((n) => join(tmpdir(), n));
  } catch {
    return [];
  }
}

function listDir(dir: string): string {
  try {
    return readdirSync(dir)
      .map((n) => {
        try {
          const s = statSync(join(dir, n));
          return `${n} (${s.isDirectory() ? "dir" : s.size + "B"})`;
        } catch {
          return `${n} (?)`;
        }
      })
      .join(", ");
  } catch {
    return "<unreadable>";
  }
}

function awkEmitter(marker: string): string[] {
  return [
    "/usr/bin/awk",
    `BEGIN { for (i = 0; i < ${LINES}; i++) { ` +
      `print "${marker} stdout " i; fflush(); ` +
      `print "${marker} stderr " i > "/dev/stderr"; fflush(); ` +
      `system("sleep 1") } }`,
  ];
}

async function probeBox(label: string, detach: boolean, boxliteHome: string) {
  console.log(`\n──────── ${label} (detach: ${detach}) ────────`);
  const marker = `${RUN_MARKER}_${detach ? "D" : "N"}`;

  const box = new SimpleBox({
    image: "alpine:latest",
    // Unique per run: autoRemove: false keeps the box record after stop()
    name: `console-discovery-${detach ? "detached" : "normal"}-${Date.now().toString(36)}`,
    entrypoint: awkEmitter(marker),
    autoRemove: false,
    detach,
  });

  try {
    await box.exec("true");
    const boxDir = join(boxliteHome, "boxes", box.id);
    const consolePath = join(boxDir, "console.log");

    console.log(`box id      : ${box.id}`);
    console.log(`box.info()  : ${JSON.stringify(box.info())}`);
    console.log(`box dir     : ${listDir(boxDir)}`);
    console.log(`console.log : ${existsSync(consolePath) ? `EXISTS (${statSync(consolePath).size}B)` : "absent"}`);
    console.log(`/tmp/boxlite-console-*: ${tmpConsoleFiles().join(", ") || "none"}`);

    // Give the entrypoint time to emit, then hunt for the marker while the VM is LIVE.
    await sleep(8000);
    const liveHits = scanForMarker([boxliteHome, ...tmpConsoleFiles()], marker);
    console.log(`marker found while running : ${liveHits.length ? liveHits.join(", ") : "NOWHERE ❌"}`);
    if (existsSync(consolePath)) {
      const content = readFileSync(consolePath, "utf-8");
      const seen = content.split("\n").filter((l) => l.includes(marker)).length;
      console.log(`console.log live content   : ${content.length}B, ${seen} marker lines`);
    }

    const shimPath = join(boxDir, "shim.stderr");
    if (existsSync(shimPath)) {
      console.log(`shim.stderr (full):`);
      for (const l of readFileSync(shimPath, "utf-8").split("\n")) console.log(`  | ${l}`);
    }

    await box.stop();

    console.log(`after stop — box dir       : ${listDir(boxDir)}`);
    const postHits = scanForMarker([boxliteHome, ...tmpConsoleFiles()], marker);
    console.log(`marker found after stop    : ${postHits.length ? postHits.join(", ") : "NOWHERE ❌"}`);
  } finally {
    await box.stop().catch(() => {});
  }
}

async function main() {
  const boxliteHome = process.env.BOXLITE_HOME ?? join(homedir(), ".boxlite");
  console.log(`BOXLITE_HOME : ${boxliteHome}`);
  console.log(`Platform     : ${process.platform} (${process.arch})  runtime: Bun ${Bun.version}`);
  console.log(`Run marker   : ${RUN_MARKER}`);
  console.log(`(tip: BOXLITE_DEBUG=1 bun run src/console-output-discovery-test.ts for verbose shim logs)`);

  await probeBox("Probe 1: normal box", false, boxliteHome);
  await probeBox("Probe 2: detached box", true, boxliteHome);

  console.log("\n================ INTERPRETATION ================");
  console.log("marker in boxes/<id>/console.log (live) -> tail console.log; detached mode if only there.");
  console.log("marker only in /tmp/boxlite-console-*   -> tail that path instead (check lifetime).");
  console.log("marker NOWHERE in both probes           -> boxlite discards entrypoint stdout; the");
  console.log("                                           log volume mount + guest contract stays.");
  console.log("================================================");
}

main();
