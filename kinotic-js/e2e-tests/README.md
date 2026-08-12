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

`test/k8s/` holds a cluster cache-eviction suite that is currently disabled — see
[test/k8s/README.md](test/k8s/README.md).
