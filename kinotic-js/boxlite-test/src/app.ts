// This is the Bun app that runs INSIDE the BoxLite container.
// It gets injected into the container and executed by the host script (index.ts).

const server = Bun.serve({
  port: 3000,
  fetch(req) {
    const url = new URL(req.url);

    if (url.pathname === "/health") {
      return Response.json({ status: "ok", runtime: "bun", container: "boxlite" });
    }

    return new Response("Hello from Bun inside BoxLite!");
  },
});

console.log(`Bun server running at http://localhost:${server.port}`);
