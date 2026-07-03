import { SimpleBox } from "@boxlite-ai/boxlite";
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

// Demonstrates which process output IS and IS NOT obtainable on the host with boxlite.
// Written to accompany a feature request for host-side log capture of container output.
//
// Cases:
//   1. Image-default ENTRYPOINT (nginx:alpine — Docker convention: logs go to stdout/stderr,
//      access.log/error.log are symlinks to /dev/stdout & /dev/stderr)
//   2. Declared entrypoint, attached box (direct-binary awk emitter, no shell involved)
//   3. Declared entrypoint, detached box (same emitter, detach: true)
//   4. exec()'d process on a running box (the one case with an output API)
//
// For each case the script generates output containing a unique marker, then looks for that
// marker everywhere it could plausibly land on the host: the box's logs/console.log and every
// readable file under the box dir. The final table shows the gap.

const RUN = Date.now().toString(36);
const BOXLITE_HOME = process.env.BOXLITE_HOME ?? join(homedir(), ".boxlite");
const NGINX_PORT = 18080;

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
    if (e.isDirectory()) yield* walk(p);
    else if (e.isFile()) yield p;
  }
}

/** Returns the files under the box dir containing the marker. */
function findInBoxDir(boxId: string, marker: string): string[] {
  const hits: string[] = [];
  for (const file of walk(join(BOXLITE_HOME, "boxes", boxId))) {
    if (SKIP_EXT.some((ext) => file.endsWith(ext))) continue;
    try {
      if (statSync(file).size > MAX_SCAN_BYTES) continue;
      if (readFileSync(file, "utf-8").includes(marker)) hits.push(file);
    } catch {
      // unreadable — not a log a host agent could tail anyway
    }
  }
  return hits;
}

function consoleLogInfo(boxId: string): string {
  const p = join(BOXLITE_HOME, "boxes", boxId, "logs", "console.log");
  return existsSync(p) ? `exists (${statSync(p).size}B)` : "absent";
}

interface CaseResult {
  name: string;
  outputGenerated: boolean;
  foundAt: string[];
}

const results: CaseResult[] = [];

// One line per second to both streams, exec'd directly (no shell required in the chain).
function awkEmitter(marker: string): string[] {
  return [
    "/usr/bin/awk",
    `BEGIN { for (i = 0; i < 60; i++) { ` +
      `print "${marker} stdout " i; fflush(); ` +
      `print "${marker} stderr " i > "/dev/stderr"; fflush(); ` +
      `system("sleep 1") } }`,
  ];
}

async function case1_imageDefaultEntrypoint() {
  console.log("\n─── Case 1: image-default ENTRYPOINT (nginx:alpine) ───");
  const box = new SimpleBox({
    image: "nginx:alpine",
    name: `gaps-nginx-${RUN}`,
    ports: [{ hostPort: NGINX_PORT, guestPort: 80, protocol: "tcp" }],
    autoRemove: false,
  });
  try {
    await box.exec("true"); // boots the VM; nginx (image ENTRYPOINT) starts as container init
    await sleep(2000);

    // Each request makes nginx write an access-log line — to stdout, per the image's
    // /var/log/nginx/access.log -> /dev/stdout symlink.
    const marker = `/marker-${RUN}`;
    let ok = false;
    for (let i = 0; i < 5 && !ok; i++) {
      try {
        const res = await fetch(`http://localhost:${NGINX_PORT}${marker}`);
        ok = res.status > 0;
      } catch {
        await sleep(1000);
      }
    }
    console.log(`request reached nginx : ${ok ? "YES (access-log line was generated)" : "NO — case inconclusive"}`);
    await sleep(2000);

    const found = findInBoxDir(box.id, marker);
    console.log(`console.log           : ${consoleLogInfo(box.id)}`);
    console.log(`access-log line found : ${found.length ? found.join(", ") : "NOWHERE on host"}`);
    results.push({ name: "1. image-default ENTRYPOINT (nginx)", outputGenerated: ok, foundAt: found });
  } finally {
    await box.stop().catch(() => {});
  }
}

async function entrypointCase(name: string, detach: boolean) {
  console.log(`\n─── ${name} ───`);
  const marker = `MARKER_${RUN}_${detach ? "D" : "A"}`;
  const box = new SimpleBox({
    image: "alpine:latest",
    name: `gaps-entrypoint-${detach ? "detached" : "attached"}-${RUN}`,
    entrypoint: awkEmitter(marker),
    autoRemove: false,
    detach,
  });
  try {
    await box.exec("true");
    await sleep(5000); // emitter has produced ~5 lines on each stream by now

    const found = findInBoxDir(box.id, marker);
    console.log(`console.log           : ${consoleLogInfo(box.id)}`);
    console.log(`entrypoint output found: ${found.length ? found.join(", ") : "NOWHERE on host"}`);
    results.push({ name, outputGenerated: true, foundAt: found });
  } finally {
    await box.stop().catch(() => {});
  }
}

async function case4_execStreaming() {
  console.log("\n─── Case 4: exec()'d process (the contrast) ───");
  const marker = `MARKER_${RUN}_EXEC`;
  const box = new SimpleBox({
    image: "alpine:latest",
    name: `gaps-exec-${RUN}`,
    autoRemove: false,
  });
  try {
    const result = await box.exec("echo", marker);
    const obtained = result.stdout.includes(marker);
    console.log(`exec stdout obtained  : ${obtained ? "YES — exec() returns/streams output" : "NO"}`);
    results.push({
      name: "4. exec()'d process",
      outputGenerated: true,
      foundAt: obtained ? ["<returned by exec() API>"] : [],
    });
  } finally {
    await box.stop().catch(() => {});
  }
}

async function main() {
  const version = (await import("@boxlite-ai/boxlite/package.json")).default.version;
  console.log(`boxlite ${version}  ${process.platform}/${process.arch}  BOXLITE_HOME=${BOXLITE_HOME}`);

  await case1_imageDefaultEntrypoint();
  await entrypointCase("Case 2: declared entrypoint, attached box", false);
  await entrypointCase("Case 3: declared entrypoint, detached box", true);
  await case4_execStreaming();

  console.log("\n================== SUMMARY: is process output obtainable on the host? ==================");
  for (const r of results) {
    const status = !r.outputGenerated ? "INCONCLUSIVE" : r.foundAt.length ? `YES — ${r.foundAt.join(", ")}` : "NO ❌";
    console.log(`  ${r.name.padEnd(45)} ${status}`);
  }
  console.log("=========================================================================================");
  console.log("Cases 1–3 are the gap: container stdout/stderr is not captured anywhere host-visible,");
  console.log("regardless of how the entrypoint is defined or whether the box is detached.");
}

main();
