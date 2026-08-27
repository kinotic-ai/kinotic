# Bug: `listInfo()` reports detached boxes as "stopped" while VM is still running

## Summary

When a box is created with `detach: true`, the VM continues running after the parent process exits (confirmed via active port forwarding), but `listInfo()` incorrectly reports `"status": "stopped"` and `"running": false`.

## Environment

- `@boxlite-ai/boxlite`: latest
- Runtime: Bun v1.3.6 (also reproducible with Node.js)
- OS: Linux x86_64 (WSL2 6.6.87.2-microsoft-standard-WSL2)

## Steps to Reproduce

### 1. Create a detached box with an HTTP server

Save as `start-detached.ts`:

```typescript
import { SimpleBox } from "@boxlite-ai/boxlite";

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function main() {
  const box = new SimpleBox({
    image: "python:slim",
    name: "detach-bug-repro",
    ports: [{ hostPort: 8080, guestPort: 8080, protocol: "tcp" }],
    detach: true,
    autoRemove: false,
  });

  await box.exec(
    "sh", "-c",
    "python -m http.server 8080 --directory /tmp > /dev/null 2>&1 &"
  );
  await sleep(2000);

  const response = await fetch("http://localhost:8080");
  console.log(`Server responding inside process: ${response.status}`);
  console.log(`Box ID: ${box.id}`);
  console.log("Exiting without calling box.stop()...");
}

main();
```

```bash
bun run start-detached.ts
```

### 2. Verify the VM is still running after process exit

```bash
curl http://localhost:8080
# Returns directory listing — the VM is alive and serving requests
```

### 3. Check what listInfo() reports

```bash
bun -e "import { JsBoxlite } from '@boxlite-ai/boxlite'; const r = JsBoxlite.withDefaultConfig(); console.log(JSON.stringify(await r.listInfo(), null, 2))"
```

## Expected Result

```json
{
  "id": "...",
  "state": {
    "status": "running",
    "running": true
  },
  "name": "detach-bug-repro"
}
```

## Actual Result

```json
{
  "id": "...",
  "state": {
    "status": "stopped",
    "running": false
  },
  "name": "detach-bug-repro"
}
```

The box reports `"stopped"` even though the VM is actively serving HTTP requests on port 8080.
