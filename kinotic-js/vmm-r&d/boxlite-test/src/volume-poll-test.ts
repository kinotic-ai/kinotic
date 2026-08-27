import { SimpleBox } from "@boxlite-ai/boxlite";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "fs";
import { tmpdir } from "os";
import { join } from "path";

// Counterpart to volume-share-test.ts: same two-box shared-volume setup, but the
// watcher uses chokidar with `usePolling: true` instead of the event-based
// `bun --watch`. Polling re-stats files each tick, reading fresh metadata across
// the virtio-fs share, so it catches box B's edit even though no inotify event
// crosses the VM boundary.
//
// chokidar (over a bare fs.watchFile loop) is the realistic production choice:
// it watches the whole tree recursively. Here box B edits a NESTED file
// (/app/src/app.ts) to prove recursive cross-volume watching works.

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

const sourceFor = (version: string) => `const VERSION = ${JSON.stringify(version)};
console.log(\`[\${new Date().toISOString()}] running VERSION=\${VERSION}\`);
`;

// Poll runner that lives INSIDE the watcher box (kept off the shared volume at
// /root so its own log never trips the watcher). On each chokidar event it
// re-executes the app — the poll equivalent of hot reload. No backticks/${} here
// so the source survives being embedded in a heredoc.
const runnerSrc = `import { watch } from "chokidar";
async function run() {
  const p = Bun.spawn(["bun", "/app/src/app.ts"], { stdout: "inherit", stderr: "inherit" });
  await p.exited;
}
await run(); // initial run
const w = watch("/app", { usePolling: true, interval: 300, ignoreInitial: true });
w.on("all", async (event, path) => {
  console.log("[chokidar] " + event + " " + path + " -> reloading");
  await run();
});
`;

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
  const shared = mkdtempSync(join(tmpdir(), "boxlite-poll-"));
  mkdirSync(join(shared, "src"));
  writeFileSync(join(shared, "src", "app.ts"), sourceFor("v1"));
  console.log(`Shared host volume: ${shared}`);

  const watcher = new SimpleBox({
    image: "oven/bun:latest",
    name: "vol-poll-watcher",
    entrypoint: ["/bin/sh", "-c", "tail -f /dev/null"],
    volumes: [{ hostPath: shared, guestPath: "/app" }],
  });
  const editor = new SimpleBox({
    image: "oven/bun:latest",
    name: "vol-poll-editor",
    entrypoint: ["/bin/sh", "-c", "tail -f /dev/null"],
    volumes: [{ hostPath: shared, guestPath: "/app" }],
  });

  let dataPropagated = false;
  let pollFired = false;

  try {
    // In production chokidar would be baked into the image; here we install it at
    // runtime (the box has outbound npm access).
    console.log("\n[A] installing chokidar in the watcher box ...");
    await watcher.exec("sh", "-c", "cd /root && bun add chokidar > /dev/null 2>&1");

    console.log("[A] starting chokidar usePolling runner ...");
    await watcher.exec("sh", "-c", `cat > /root/runner.ts <<'RUNNER'\n${runnerSrc}\nRUNNER`);
    await watcher.exec("sh", "-c", "cd /root && nohup bun /root/runner.ts > /root/runner.log 2>&1 &");

    const initial = await pollUntil(
      watcher,
      ["cat", "/root/runner.log"],
      (s) => s.includes("VERSION=v1")
    );
    console.log(`[A] runner initial output:\n${initial.stdout.trim() || "(empty)"}`);
    if (!initial.ok) {
      console.log("[A] runner never produced initial v1 output — aborting.");
      return;
    }
    // Let chokidar finish its initial scan before we edit.
    await sleep(1500);

    // Box B rewrites the NESTED source file on the shared volume to v2.
    console.log("\n[B] overwriting /app/src/app.ts with VERSION=v2 via shared volume ...");
    const v2 = sourceFor("v2");
    await editor.exec("sh", "-c", `cat > /app/src/app.ts <<'BUNAPP'\n${v2}\nBUNAPP`);

    // (a) data propagation
    const seen = await pollUntil(
      watcher,
      ["cat", "/app/src/app.ts"],
      (s) => s.includes('VERSION = "v2"'),
      { tries: 10 }
    );
    dataPropagated = seen.ok;

    // (b) did chokidar's poll watcher detect it and reload?
    const reloaded = await pollUntil(
      watcher,
      ["cat", "/root/runner.log"],
      (s) => s.includes("VERSION=v2"),
      { tries: 20 }
    );
    pollFired = reloaded.ok;
    console.log(`\n[A] runner log after edit:\n${reloaded.stdout.trim()}`);
  } finally {
    console.log("\nCleaning up boxes and shared volume ...");
    await watcher.stop().catch(() => {});
    await editor.stop().catch(() => {});
    rmSync(shared, { recursive: true, force: true });
  }

  console.log("\n================ RESULT ================");
  console.log(`(a) data shared across boxes        : ${dataPropagated ? "YES ✅" : "NO ❌"}`);
  console.log(`(b) chokidar usePolling saw the edit: ${pollFired ? "YES ✅" : "NO ❌"}`);
  console.log("=======================================");
}

main();
