# Worker overhead benchmark

Measures what running a stateless microservice as a Cloudflare Worker costs its callers in latency and throughput,
compared with running it on a runtime VM as today. Nothing here is secured: the test server admits any credentials,
and the Worker is public. Tear both down after a run.

## What runs where

| Piece | What it is | Where it runs |
|---|---|---|
| Test server | `kinotic-server-test`: the gateway, a security service that admits anyone, `IBenchEntityService` (fake persistence that answers after a requested delay) and `WorkerForwardingConsumer` (forwards `srv://management-api~org.kinotic.testserver.WorkerBench` to the Worker, as the gateway's HTTP consumer would) | A host reachable from Cloudflare under a temporary DNS name |
| Worker | `worker/index.ts`: reads the fake persistence back through the gateway's `POST /api/srv/...` route | Cloudflare |
| Benchmark | `WorkerOverhead.test.ts`: the callers, plus the "runtime" microservice it hosts on its own connection | The same host as the test server |

Each call takes one of three paths:

- **gateway → persistence**: the caller reads the fake persistence directly, the floor every path pays
- **runtime**: caller → gateway → the microservice the benchmark hosts → gateway → persistence (today's model)
- **worker**: caller → gateway → `WorkerForwardingConsumer` → Cloudflare Worker → gateway REST route → persistence

The fake persistence answers each read after `PERSISTENCE_DELAY_MS` (default 3 ms, about an Elasticsearch get).

## Running it

Pick a host close to where Kinotic runs (Azure `centralus`), so the gateway ↔ Cloudflare round trip is the one
production would see. The host needs JDK 25, Bun and Docker if the server runs as an image.

1. **Build the test server** from this branch, at the repository root:

   ```bash
   ./gradlew :kinotic-server-test:bootBuildImage   # image kinoticai/kinotic-server-test:5.0.0-SNAPSHOT
   # or just the jar
   ./gradlew :kinotic-server-test:bootJar          # kinotic-server-test/build/libs/kinotic-server-test-5.0.0-SNAPSHOT.jar
   ```

2. **Deploy the Worker** with a Cloudflare API token, from `kinotic-js/workspace/packages/core`:

   ```bash
   bunx wrangler@4 deploy --config test/worker-overhead/worker/wrangler.jsonc \
        --var KINOTIC_API_URL:https://<test server's temporary DNS name>
   ```

   It answers at `https://kinotic-worker-overhead.<account subdomain>.workers.dev`. The temporary DNS name can be
   chosen before the server is up (a tunnel hostname, for instance), or the Worker redeployed with the final value.

3. **Start the test server** on port 58503 with the Worker's URL, and expose port 58503 under the temporary DNS name
   over HTTPS (for example a Cloudflare Tunnel: `cloudflared tunnel --url http://localhost:58503`):

   ```bash
   docker run -d --name kinotic-bench -p 58503:58503 \
        -e KINOTIC_TESTSERVER_WORKERURL=https://kinotic-worker-overhead.<account subdomain>.workers.dev \
        kinoticai/kinotic-server-test:5.0.0-SNAPSHOT
   # or
   KINOTIC_TESTSERVER_WORKERURL=https://kinotic-worker-overhead.<account subdomain>.workers.dev \
     java -XX:MaxDirectMemorySize=512m --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED \
          --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED \
          -jar kinotic-server-test/build/libs/kinotic-server-test-5.0.0-SNAPSHOT.jar
   ```

   Check the whole path once: this should answer `{"totalMs":...,"readMs":[...]}`

   ```bash
   curl -X POST -H 'content-type: application/json' \
        --data '[{"calls":1,"parallel":false,"id":"a","delayMs":3}]' \
        https://kinotic-worker-overhead.<account subdomain>.workers.dev/srv/run
   ```

4. **Run the benchmark** on the same host, from `kinotic-js/workspace/packages/core`, against the server on
   `127.0.0.1:58503`:

   ```bash
   bun install
   USE_GATEWAY_DOCKER=false WORKER_BENCH=1 bunx vitest run test/worker-overhead
   ```

   It prints a latency table and a throughput table and writes `worker-overhead-results.json`. Tune it with
   `ITERATIONS` (default 200), `WARMUP` (20), `CONCURRENCY` (32), `DURATION_S` (20) and `PERSISTENCE_DELAY_MS` (3).

5. **Tear down**: `bunx wrangler@4 delete --config test/worker-overhead/worker/wrangler.jsonc`, stop the server
   and the tunnel.

## Reading the results

- **worker minus runtime** at the same number of reads is what the Worker design adds per call.
- **in Worker p50** against the caller's p50 splits a worker call into the gateway ↔ Worker hop and the reads.
- **read p50** in a worker row is one Worker → gateway → persistence round trip; in a runtime row, one read over
  the runtime's STOMP connection.
- The runtime path runs in this process, on the same host as the gateway, so it is the best case for today's model.
