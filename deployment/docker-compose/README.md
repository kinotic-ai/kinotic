# Docker Compose

Containerized stacks for local Kinotic development. Pick the bundle that matches your
workflow — they're built up from small `compose.*.yml` files using docker-compose's
`include:` mechanism, so you can swap pieces in and out without forking a whole stack.

## Pick your scenario

| You want… | Run |
|---|---|
| **Everything in containers, fastest path** | `docker compose up -d` |
| **IntelliJ-running kinotic-server, just ES + Kibana** | `docker compose -f compose.elasticsearch.yml -f compose.kibana.yml up -d` |
| **IntelliJ + ES + run migrations once** | `docker compose -f compose.elasticsearch.yml -f compose.kinotic-migration.yml up -d` |
| **Full stack with OIDC via local Keycloak** | `docker compose -f compose.yml -f compose.keycloak.yml up -d` |
| **Backing services for the kinotic-test suite** | `docker compose -f compose.kinotic-test.yml up -d` |

`docker compose down` to stop. `docker compose down -v` to also wipe volumes (ES data).

`kinotic-migration` and `kinotic-server` use `pull_policy: missing`, so a locally present image
wins and the stack keeps using it until you ask for a newer one. Run `docker compose pull` to
refresh them from Docker Hub. This is also what lets CI point the stack at the image built from
the commit under test rather than whatever the published tag currently holds.

## What each file does

| File | Purpose | Brings up |
|---|---|---|
| `compose.yml` | Top-level — `include:`s every piece below | Full stack (ES + Kibana + OTEL + load-gen + migration + kinotic-server) |
| `compose.elasticsearch.yml` | Elasticsearch | `kinotic-elasticsearch:9200` |
| `compose.kibana.yml` | Kibana (depends on Elasticsearch) | `kinotic-kibana:5601` |
| `compose.kinotic-migration.yml` | Runs `kinotic-migration` once against ES, then exits | One-shot job — `service_completed_successfully` is what kinotic-server waits on |
| `compose.kinotic-server.yml` | The Kinotic server itself | `kinotic-server:9090/58503` (UI, STOMP) |
| `compose-otel.yml` | OpenTelemetry collector + Grafana + Tempo + Loki + Mimir | `grafana:3000`, `loki:3100`, `tempo:3200`, `mimir:9009` |
| `compose.gen-schemas.yml` | Load-generator container that pre-populates schemas | One-shot when `compose.yml` brings up the full stack |
| `compose.keycloak.yml` | Local Keycloak as a platform OIDC provider (dev-only secret) | `keycloak:8888` — see `KEYCLOAK_HOSTS_SETUP.md` |
| `compose.kinotic-test.yml` | Minimal: ES + migration only, no server | Backing services for the `kinotic-test` suite, which runs the server in-process |
| `compose.kinotic-e2e-test.yml` | Elasticsearch + migration + server, on the `test,e2e-tests,compose` profiles | Used by e2e tests in CI |

## Common one-liners

```bash
# (1) Backing services for IntelliJ-local kinotic-server dev
#     Runs ES, then the migration container which exits when done.
#     Re-run any time you bump kinotic-migration to refresh indices.
docker compose -f compose.elasticsearch.yml -f compose.kinotic-migration.yml up -d

# (2) Full self-contained stack (everything in containers, including the server)
docker compose up -d

# (3) Tail the kinotic-server logs (look here for verification URLs in dev — email is off)
docker compose logs -f kinotic-server

# (4) Hot-reload server image after a build
./gradlew :kinotic-server:bootBuildImage
docker compose up -d --force-recreate --no-deps kinotic-server

# (5) Run only the migration container against an already-running ES, then exit
docker compose -f compose.kinotic-migration.yml run --rm kinotic-migration

# (6) Wipe all state and restart clean (down -v drops the ES named volume)
docker compose down -v && docker compose up -d
```

## Service URLs (when the full stack is up)

| Service | URL | Notes |
|---|---|---|
| Kinotic UI | <http://localhost:9090> | TLS off in compose. The `/login`, `/signup`, `/applications` routes are SPA. |
| STOMP | `ws://localhost:58503/v1` | Used by the SPA's `Kinotic.connect(...)` |
| Elasticsearch | <http://localhost:9200> | `xpack.security.enabled=false` — local only |
| Kibana | <http://localhost:5601> | |
| Grafana | <http://localhost:3000> | When `compose-otel.yml` is included. Anonymous auth with the Admin role — no login |
| Keycloak | <http://keycloak:8888> | When `compose.keycloak.yml` is included; requires `127.0.0.1 keycloak` in `/etc/hosts` per `KEYCLOAK_HOSTS_SETUP.md` |

## Try the auth flow (UI devs)

The full compose stack (`docker compose up -d`) gives you a working signup/login flow out
of the box. Email delivery is off, so verification links land in the kinotic-server log
instead of an inbox.

```bash
# 1. Bring up the stack
docker compose up -d

# 2. Watch the kinotic-server log for the verification URL on signup
docker compose logs -f kinotic-server | grep -i "verification URL"

# 3. Open the SPA
open http://localhost:9090
```

Steps in the SPA:

1. Click **Sign Up**, fill in org name + email + display name, submit.
2. Find the verification URL in the kinotic-server log (printed by `EmailService` when email is disabled). Open it.
3. Set a password → "Account created!" → click **Sign in**.
4. Log in with the email + password you just set.

### Adding "Continue with Keycloak" (local OIDC, no internet)

To exercise the platform-OIDC plumbing without needing a real IdP, layer in
`compose.keycloak.yml`:

```bash
# One-time: add the keycloak hostname to your hosts file (see KEYCLOAK_HOSTS_SETUP.md)
echo '127.0.0.1 keycloak' | sudo tee -a /etc/hosts

# Bring up the stack with Keycloak
docker compose -f compose.yml -f compose.keycloak.yml up -d
```

What this gives you:

- Keycloak at <http://keycloak:8888> with the pre-imported `test` realm.
- A `kinotic-client` confidential client whose secret lives in `keycloak-test-realm.json`
  (committed because the value is dev-only — never reuse beyond a developer's laptop).
  The kinotic-server container picks the secret up from the `KINOTIC_AKV_KEYCLOAK` env
  var via the dev-fallback `EnvVarSecretReferenceResolver`.
- The **Continue with Keycloak** button isn't wired automatically — the social-button
  list is sourced from `kinotic_org_signup_oidc_configuration` rows, and no migration
  ships a Keycloak entry. Seed one manually via the kinotic-migration tool if you want
  the button to appear.

## Dev with kinotic-server in IntelliJ

This is the recommended workflow when iterating on backend code:

```bash
# 1. ES + run migrations once
docker compose -f compose.elasticsearch.yml -f compose.kinotic-migration.yml up -d

# 2. Wait for migration to finish (it's a one-shot)
docker compose ps kinotic-migration   # State: Exited (0)

# 3. Run KinoticServerApplication in IntelliJ with profile `development`.
#    application-development.yml already points the elastic connection at localhost:9200.
```

### Traces, metrics, and logs in Grafana

Add `compose-otel.yml` to bring up the collector and the Grafana stack next to Elasticsearch:

```bash
docker compose -f compose-otel.yml -f compose.elasticsearch.yml -f compose.kibana.yml up -d
```

The `KinoticServerApplication` run configuration exports to the collector on `localhost:4317`.

Grafana is at <http://localhost:3000> (anonymous Admin, no login) and opens on the **Kinotic
Server** dashboard — JVM, HTTP RED metrics, span rates from the traces themselves, and a log
panel, all provisioned from `dashboards/kinotic-server.json`. Beyond the dashboard:

| Signal | Datasource | Where to look |
|---|---|---|
| Traces | Tempo | Explore → Tempo → Search, service name `kinotic-server` |
| Metrics | Mimir | Explore → Mimir, e.g. `jvm_memory_used_bytes{job="kinotic-server"}` |
| Logs | Loki | Explore → Loki, `{service_name="kinotic-server"}` (tenant `kinotic-system`) |

Grafana 12 preinstalls the Drilldown apps (Metrics, Logs, Traces) and downloads them from
grafana.com on first start — the **Drilldown** nav section needs no configuration beyond the
datasources above, and the `grafana-data` volume keeps them across container recreates.

### How the signals name themselves

Each backend renames OTel attributes on ingest, which is what makes a copy-pasted query match
nothing. The rules that matter:

- **Mimir** folds `service.name` into the `job` label and `service.instance.id` into
  `instance`; every other resource attribute lands on the `target_info` metric. Instrument
  attributes keep their names with `.` → `_` (`jvm.memory.type` → `jvm_memory_type`).
- **Metric names carry unit suffixes** (`jvm.memory.used` → `jvm_memory_used_bytes`,
  `http.server.request.duration` → `http_server_request_duration_seconds_bucket`) because
  `mimir.yml` sets `otel_metric_suffixes_enabled: true`. Mimir defaults that off, which stores
  bare `jvm_memory_used` and leaves every stock dashboard empty.
- **Tempo's metrics-generator** writes its own series to Mimir — `traces_spanmetrics_calls_total`
  and `traces_spanmetrics_latency_bucket`, labelled `service`, `span_name`, `span_kind`,
  `status_code`. These only exist because `tempo.yml` enables the processors under `overrides`;
  the `metrics_generator.processor` block alone does nothing.
- **Loki** promotes `service.name` to the `service_name` index label and keeps `trace_id` /
  `span_id` as structured metadata, which is what the Tempo link on each log line matches.
- **Span names come from semconv, not from the code**: `get` / `index` / `search` are
  Elasticsearch endpoint ids, and a bare `GET` / `PUT` / `POST` is an HTTP span with no route
  template. Each Elasticsearch call therefore appears twice — the client span and its transport
  child. `peer.service` and `db.system` are what identify the far end, and Tempo resolves them
  into virtual nodes on the service graph.

`mimir.yml` and `tempo.yml` are bind-mounted, so editing them does not change the container
spec and `docker compose up -d` leaves the old config running. Restart those services
explicitly after a change:

```bash
docker compose -f compose-otel.yml restart mimir tempo
```

To see what's actually stored rather than what should be:

```bash
curl -s 'http://localhost:9009/prometheus/api/v1/label/__name__/values' | jq -r '.data[]' | grep -E 'jvm|http|traces_'
curl -s -H 'X-Scope-OrgID: kinotic-system' 'http://localhost:3100/loki/api/v1/labels' | jq
```

### Correlation between signals

- **Log → trace**: expand a log line in Explore or the dashboard's log panel; the `TraceID`
  derived field links into Tempo. It matches the `trace_id` structured-metadata key, not the
  line text — OTLP log records carry the trace context as metadata, so a body regex finds
  nothing.
- **Metric → trace**: exemplar dots on the p95 latency panel jump to the trace behind the
  sample. Requires `max_global_exemplars_per_user` above 0 in `mimir.yml` — Mimir's default of
  `0` disables exemplar ingestion outright, unlike the series limits where `0` means unlimited.
- **Trace → logs / metrics**: a span in Tempo has *Logs for this span* and the span-metrics
  queries wired through `tracesToLogsV2` / `tracesToMetrics`.
- **Service graph**: Tempo's *Service Graph* tab and the node graph come from the
  `service-graphs` processor writing `traces_service_graph_*` into Mimir.

`application-development.yml` currently has `kinotic.domain.email.enabled: true`, pointed at
the real ACS endpoint. Set it to `false` to have `EmailService` skip the send and log the
verification URL to the IntelliJ console instead — which is what the compose stack does via
`KINOTIC_DOMAIN_EMAIL_ENABLED=false`.

If you also run the Vite frontend (`pnpm dev` on `:5173`), it calls the server directly at
`localhost:58503` via `VITE_KINOTIC_HOST`/`VITE_KINOTIC_PORT` — see
`kinotic-frontend/apps/portal/ENV_SETUP.md`. For flows where the IdP has to call back into
your machine, use `pnpm dev:tunnel` and set `KINOTIC_DOMAIN_APPBASEURL` on the IntelliJ run
config to the tunnel origin so OIDC redirect URIs match what's registered with the IdP.

## Storage paths

- `kinotic-elastic-data` — Elasticsearch data, a Docker named volume. Survives
  `docker compose down`; removed by `docker compose down -v`.
- No host volumes for the kinotic-server container — it's stateless.

## When you outgrow docker-compose

For multi-node clustering (Ignite cluster discovery, replica counts, real workload
identity, etc.) use the KinD setup in `deployment/kind/`. See
`deployment/kind/terraform/` for the Terraform-driven local Kubernetes story.
