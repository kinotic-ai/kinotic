import { SimpleBox } from "@boxlite-ai/boxlite";
import { mkdtempSync, writeFileSync, rmSync } from "fs";
import { tmpdir } from "os";
import { join } from "path";

// Verifies that a host directory mounted as a volume into two boxes is shared:
// box B edits a source file on the shared volume, and box A — running
// `bun --watch` against that same file — both sees the new content (data
// propagation) and hot-restarts on it (watch-event propagation).

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

const sourceFor = (version: string) => `const VERSION = ${JSON.stringify(version)};
console.log(\`[\${new Date().toISOString()}] running VERSION=\${VERSION}\`);
setInterval(() => {}, 1 << 30); // keep alive so --watch hot-restarts on change
`;

// Poll an exec'd command until its stdout satisfies `predicate` or we time out.
async function pollUntil(
  box: SimpleBox,
  cmd: string[],
  predicate: (stdout: string) => boolean,
  { tries = 15, intervalMs = 1000 }: { tries?: number; intervalMs?: number } = {}
): Promise<{ ok: boolean; stdout: string }> {
  let stdout = "";
  for (let i = 0; i < tries; i++) {
    const r = await box.exec(cmd[0], ...cmd.slice(1));
    stdout = r.stdout;
    if (predicate(stdout)) return { ok: true, stdout };
    await sleep(intervalMs);
  }
  return { ok: false, stdout };
}

async function main() {
  // Shared host directory mounted into BOTH boxes.
  const shared = mkdtempSync(join(tmpdir(), "boxlite-vol-"));
  writeFileSync(join(shared, "app.ts"), sourceFor("v1"));
  console.log(`Shared host volume: ${shared}`);

  // Box A: the watcher. Box B: the editor. Same hostPath -> guest /app.
  const watcher = new SimpleBox({
    image: "oven/bun:latest",
    name: "vol-watcher",
    entrypoint: ["/bin/sh", "-c", "tail -f /dev/null"],
    volumes: [{ hostPath: shared, guestPath: "/app" }],
  });
  const editor = new SimpleBox({
    image: "oven/bun:latest",
    name: "vol-editor",
    entrypoint: ["/bin/sh", "-c", "tail -f /dev/null"],
    volumes: [{ hostPath: shared, guestPath: "/app" }],
  });

  let dataPropagated = false;
  let watchFired = false;

  try {
    // Start `bun --watch` in the watcher; log OUTSIDE the shared volume so the
    // log file itself can never be mistaken for a watched source change.
    console.log("\n[A] starting `bun --watch /app/app.ts` ...");
    await watcher.exec(
      "sh",
      "-c",
      "nohup bun --watch /app/app.ts > /root/watch.log 2>&1 &"
    );

    const initial = await pollUntil(
      watcher,
      ["cat", "/root/watch.log"],
      (s) => s.includes("VERSION=v1")
    );
    console.log(`[A] watcher initial output:\n${initial.stdout.trim() || "(empty)"}`);
    if (!initial.ok) {
      console.log("[A] watcher never produced initial v1 output — aborting.");
      return;
    }

    // Box B rewrites the source on the shared volume to v2.
    console.log("\n[B] overwriting /app/app.ts with VERSION=v2 via shared volume ...");
    const v2 = sourceFor("v2");
    await editor.exec("sh", "-c", `cat > /app/app.ts <<'BUNAPP'\n${v2}\nBUNAPP`);

    // (a) Data propagation: does box A now SEE the new bytes?
    const seen = await pollUntil(
      watcher,
      ["cat", "/app/app.ts"],
      (s) => s.includes('VERSION = "v2"'),
      { tries: 10 }
    );
    dataPropagated = seen.ok;
    console.log(`\n[A] cat /app/app.ts ->\n${seen.stdout.trim()}`);

    // (b) Watch-event propagation: did `bun --watch` detect it and re-run?
    const restarted = await pollUntil(
      watcher,
      ["cat", "/root/watch.log"],
      (s) => s.includes("VERSION=v2"),
      { tries: 15 }
    );
    watchFired = restarted.ok;
    console.log(`\n[A] watcher log after edit:\n${restarted.stdout.trim()}`);
  } finally {
    console.log("\nCleaning up boxes and shared volume ...");
    await watcher.stop().catch(() => {});
    await editor.stop().catch(() => {});
    rmSync(shared, { recursive: true, force: true });
  }

  console.log("\n================ RESULT ================");
  console.log(`(a) data shared across boxes   : ${dataPropagated ? "YES ✅" : "NO ❌"}`);
  console.log(`(b) bun --watch saw the change : ${watchFired ? "YES ✅" : "NO ❌"}`);
  console.log("=======================================");
}

main();
