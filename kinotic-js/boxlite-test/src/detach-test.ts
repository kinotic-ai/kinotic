import { SimpleBox } from "@boxlite-ai/boxlite";

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function main() {
  console.log("Creating detached box with HTTP server...");

  const box = new SimpleBox({
    image: "python:slim",
    name: "detach-http-test",
    ports: [{ hostPort: 8080, guestPort: 8080, protocol: "tcp" }],
    detach: true,
    autoRemove: false,
  });

  await box.exec(
    "sh", "-c",
    "python -m http.server 8080 --directory /tmp > /dev/null 2>&1 &"
  );

  await sleep(2000);

  // Verify it works from inside the process
  const response = await fetch("http://localhost:8080");
  console.log(`Status from inside process: ${response.status}`);

  console.log(`\nBox ID: ${box.id}`);
  console.log("Process exiting now. Try: curl http://localhost:8080");

  // Do NOT call box.stop()
}

main();
