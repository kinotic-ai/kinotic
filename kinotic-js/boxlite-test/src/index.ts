import { SimpleBox } from "@boxlite-ai/boxlite";
import { readFileSync } from "fs";
import { resolve } from "path";

const APP_SOURCE = readFileSync(resolve(import.meta.dir, "app.ts"), "utf-8");

async function main() {
  console.log("Creating BoxLite container with oven/bun image...");

  const box = new SimpleBox({
    image: "oven/bun:latest",
    entrypoint: ["/bin/sh", "-c", "tail -f /dev/null"],
    ports: [{ hostPort: 3000, guestPort: 3000 }],
  });

  try {
    // Create workspace and inject the Bun app into the container
    console.log("Injecting Bun app into container...");
    await box.exec("mkdir", "-p", "/app");
    await box.exec(
      "sh",
      "-c",
      `cat > /app/server.ts << 'BUNAPP'\n${APP_SOURCE}\nBUNAPP`
    );

    // Verify the file was written
    const check = await box.exec("cat", "/app/server.ts");
    console.log("Injected app source:\n", check.stdout);

    // Verify bun is available in the container
    const bunVersion = await box.exec("bun", "--version");
    console.log(`Bun version in container: ${bunVersion.stdout.trim()}`);

    // Start the Bun server in the background
    console.log("Starting Bun server inside container...");
    await box.exec(
      "sh",
      "-c",
      "nohup bun run /app/server.ts > /app/server.log 2>&1 &"
    );

    // Give the server a moment to start
    await box.exec("sleep", "2");

    // Test the server from inside the container (using bun since curl may not be installed)
    console.log("Testing server from inside the container...");
    const response = await box.exec(
      "bun",
      "-e",
      "const r = await fetch('http://localhost:3000'); console.log(await r.text())"
    );
    console.log(`Response: ${response.stdout}`);

    const healthResponse = await box.exec(
      "bun",
      "-e",
      "const r = await fetch('http://localhost:3000/health'); console.log(await r.text())"
    );
    console.log(`Health check: ${healthResponse.stdout}`);

    // Show server logs
    const logs = await box.exec("cat", "/app/server.log");
    console.log(`Server logs:\n${logs.stdout}`);

    console.log("\nBun app is running inside BoxLite container!");
    console.log("Port 3000 is forwarded — try: curl http://localhost:3000");
    console.log("Press Ctrl+C to stop...");

    // Keep alive until interrupted
    await new Promise((_, reject) => {
      process.on("SIGINT", () => reject(new Error("interrupted")));
      process.on("SIGTERM", () => reject(new Error("interrupted")));
    });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : String(err);
    if (message !== "interrupted") {
      console.error("Error:", message);
    }
  } finally {
    console.log("\nStopping BoxLite container...");
    await box.stop();
    console.log("Done.");
  }
}

main();
