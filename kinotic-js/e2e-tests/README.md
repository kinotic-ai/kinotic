# End-to-end tests for Kinotic.

Vitest suites under `test/` that drive a real kinotic-server over STOMP and REST. Every
push to `develop` and `main` runs them in CI and publishes the results to the Allure report.

```bash
pnpm install

# Against an already-running server on 127.0.0.1:58503
pnpm test

# Let the suite start its own stack via testcontainers
VITE_USE_KINOTIC_DOCKER=true pnpm test

pnpm ui-test       # vitest --ui
```

`test/setup.ts` is the vitest `globalSetup`. With `VITE_USE_KINOTIC_DOCKER=true` it brings up
`deployment/docker-compose/compose.kinotic-e2e-test.yml` (Elasticsearch + migration +
kinotic-server on the `test,e2e-tests,compose` profiles) and hands the mapped ports to the
suites; otherwise it points them at `127.0.0.1:58503`.

## Node-failure suite

`test/node-failure/` proves that a service call fails instead of hanging when the node serving
it, or the node the caller is connected to, dies mid-call, and that a stream's producer is
cancelled when its caller unsubscribes, disconnects, or loses its node. It runs on its own
config against a two-node cluster (`kinotic-server` and `kinotic-server-2` from the same compose
file) and kills and restarts `kinotic-server-2` through the `docker` CLI, so it needs Docker
either way:

```bash
# Let the suite start its own two-node stack
VITE_USE_KINOTIC_DOCKER=true pnpm test:node-failure

# Against a stack started by hand on 127.0.0.1:58503 and 127.0.0.1:58504
docker compose --env-file ../../gradle.properties \
  -f ../../deployment/docker-compose/compose.kinotic-e2e-test.yml up kinotic-server kinotic-server-2
pnpm test:node-failure
```

A run takes several minutes: every lost call waits on Ignite's failure detection, and the killed
node is started again so the suite can prove the host reconnects and serves.

`test/k8s/` holds a cluster cache-eviction suite that is currently disabled — see
[test/k8s/README.md](test/k8s/README.md).
