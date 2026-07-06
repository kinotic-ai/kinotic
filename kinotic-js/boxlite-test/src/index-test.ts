import { SimpleBox } from "@boxlite-ai/boxlite";

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function main() {
  const box = new SimpleBox({ image: "alpine:latest" });
  try {
    const start = performance.now();
    const r1 = await box.exec("echo", "first");
    const elapsed = performance.now() - start;
    console.log(`1: ${r1.stdout.trim()} (VM launch + first exec: ${elapsed.toFixed(0)}ms)`);

    await sleep(2000);

    const r2 = await box.exec("echo", "second");
    console.log("2:", r2.stdout.trim());

    await sleep(2000);

    const r3 = await box.exec("echo", "third");
    console.log("3:", r3.stdout.trim());

    await sleep(2000);

    const r4 = await box.exec("echo", "fourth");
    console.log("4:", r4.stdout.trim());
  } finally {
    await box.stop();
  }
}

main();
