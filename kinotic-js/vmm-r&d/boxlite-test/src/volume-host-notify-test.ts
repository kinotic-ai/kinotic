import { SimpleBox } from "@boxlite-ai/boxlite";
import { mkdtempSync, rmSync, watch, watchFile, unwatchFile, type FSWatcher } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

// Does a watcher ON THE HOST get notified when a boxlite GUEST writes to a file on a
// host-mounted volume? This is the guest->HOST direction the log-shipping design relies
// on (one Alloy on the host tailing per-VM log files), and is distinct from the
// guest->guest case already covered by volume-share-test.ts / volume-poll-test.ts.
//
// The guest->guest finding ("inotify does not cross") does NOT necessarily predict this:
// a guest write reaches the host file via virtiofsd performing a real write() on the HOST
// kernel, and that host-side write normally DOES emit an inotify event. So we measure it.
//
// Two host-side watchers, mirroring the existing pair of tests:
//   - fs.watch     -> event-based (inotify on Linux, FSEvents on macOS): low latency IF it fires
//   - fs.watchFile -> poll-based  (stat loop): should fire across the share, at poll-interval latency
//
// PLATFORM NOTE: production Alloy runs on a LINUX host (fsnotify -> inotify). fs.watch on
// macOS uses FSEvents, which can behave differently across a virtio-fs share, so the
// authoritative result is the one from running this on Linux. Run on both if you can.

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function main() {
  const shared = mkdtempSync(join(tmpdir(), "boxlite-host-notify-"));
  const logFile = join(shared, "out.log");
  console.log(`Host volume dir : ${shared}`);
  console.log(`Platform        : ${process.platform} (${process.arch})  runtime: Bun ${Bun.version}`);
  const backend =
    process.platform === "linux" ? "inotify" : process.platform === "darwin" ? "FSEvents" : "?";
  console.log(`fs.watch backend: ${backend}\n`);

  const box = new SimpleBox({
    image: "alpine:latest",
    name: "vol-host-notify",
    entrypoint: ["/bin/sh", "-c", "tail -f /dev/null"],
    volumes: [{ hostPath: shared, guestPath: "/app" }],
  });

  // Set by the host-side watcher callbacks; reset each round so latency is per-write.
  let eventFiredAt = 0;
  let pollFiredAt = 0;

  // Watch the directory (non-recursive is fine: out.log sits directly in it).
  const eventWatcher: FSWatcher = watch(shared, () => {
    if (!eventFiredAt) eventFiredAt = Date.now();
  });
  watchFile(logFile, { interval: 300 }, (curr, prev) => {
    if (curr.mtimeMs !== prev.mtimeMs && !pollFiredAt) pollFiredAt = Date.now();
  });

  const results: Array<{ round: number; event: number | null; poll: number | null }> = [];

  try {
    // Create the box and warm the mount before measuring (first exec lazily boots the VM).
    await box.exec("sh", "-c", "echo warmup > /app/.warmup");
    await sleep(1000);

    for (let round = 1; round <= 3; round++) {
      eventFiredAt = 0;
      pollFiredAt = 0;
      const line = `log line round ${round} @ ${new Date().toISOString()}`;
      const tStart = Date.now();

      // The "workload" appends a line to its log file on the shared volume.
      await box.exec("sh", "-c", `echo ${JSON.stringify(line)} >> /app/out.log`);

      // Wait for either watcher to fire; cap the window well above the 300ms poll interval.
      const deadline = Date.now() + 4000;
      while (Date.now() < deadline && (!eventFiredAt || !pollFiredAt)) await sleep(50);

      const event = eventFiredAt ? eventFiredAt - tStart : null;
      const poll = pollFiredAt ? pollFiredAt - tStart : null;
      results.push({ round, event, poll });
      console.log(
        `round ${round}: event=${event === null ? "MISS" : event + "ms"}  ` +
          `poll=${poll === null ? "MISS" : poll + "ms"}`,
      );
    }
  } finally {
    eventWatcher.close();
    unwatchFile(logFile);
    await box.stop().catch(() => {});
    rmSync(shared, { recursive: true, force: true });
  }

  const anyEvent = results.some((r) => r.event !== null);
  const allPoll = results.every((r) => r.poll !== null);
  console.log("\n================ RESULT (guest -> host) ================");
  console.log(`event-based (fs.watch)  saw guest writes : ${anyEvent ? "YES ✅" : "NO ❌"}`);
  console.log(`poll-based  (watchFile) saw guest writes : ${allPoll ? "YES ✅" : "PARTIAL/NO ⚠️"}`);
  console.log("-------------------------------------------------------");
  console.log("event=YES on LINUX  -> host Alloy can use inotify (sub-second).");
  console.log("event=NO            -> configure Alloy to POLL; latency ~= poll interval.");
  console.log("=======================================================");
}

main();
