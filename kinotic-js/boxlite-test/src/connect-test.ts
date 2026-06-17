import { SimpleBox, JsBoxlite } from "@boxlite-ai/boxlite";

const BOX_NAME = "alpine-persist-test";

async function main() {
  // List all running boxes
  const runtime = JsBoxlite.withDefaultConfig();
  console.log("Listing all running boxes...");
  const boxes = await runtime.listInfo();
  console.log(`Found ${boxes.length} running box(es):`);
  for (const info of boxes) {
    console.log(JSON.stringify(info, null, 2));
  }

  // Create or reuse the box via SimpleBox
  const box = new SimpleBox({
    image: "alpine:latest",
    name: BOX_NAME,
    reuseExisting: true,
    autoRemove: false,
    detach: true,
    // entrypoint: ["/bin/sh", "-c", "tail -f /dev/null"],
  });

  // First exec triggers lazy create-or-reuse
  const result = await box.exec("echo", "hello from box");
  const reused = box.created === false;
  console.log(`\nBox "${BOX_NAME}" ${reused ? "reused (existing)" : "newly created"}`);
  console.log(`stdout: ${result.stdout.trim()}`);
  console.log(`exitCode: ${result.exitCode}`);

  // Show uptime
  const uptime = await box.exec("cat", "/proc/uptime");
  console.log(`Box uptime (seconds): ${uptime.stdout.trim().split(" ")[0]}`);

  // Append a timestamp marker to prove reconnection
  const ts = new Date().toISOString();
  await box.exec("sh", "-c", `echo "${ts}" >> /root/visits.log`);
  const visits = await box.exec("cat", "/root/visits.log");
  console.log(`\nVisit log (/root/visits.log):`);
  console.log(visits.stdout);

  console.log(
    `Done. Box "${BOX_NAME}" is left running. Run this script again to test reconnecting.`
  );

  // Do NOT call box.stop() — leave it running
}

main();
