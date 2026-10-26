# Docker Compose

Containerized stacks for local Kinotic development. Pick the bundle that matches your
workflow — they're built up from small `compose.*.yml` files using docker-compose's
`include:` mechanism, so you can swap pieces in and out without forking a whole stack.

## Pick your scenario

| You want… | Run |
|---|---|
| **Everything in containers, fastest path** | `docker compose up -d` |
| **Everything, with the server images you built from your working tree** | `./gradlew bootBuildImage` for the modules you change, then `docker compose up -d` |
| **Full stack with OIDC via local Keycloak** | `docker compose -f compose.yml -f compose.keycloak.yml up -d` |
| **Backing services for the kinotic-test suite** | `docker compose -f compose.kinotic-test.yml up -d` |

`docker compose down` to stop. `docker compose down -v` to also wipe volumes (ES data and the published UIs).

`kinotic-migration` and the three servers use `pull_policy: missing`, so a locally present image
wins and the stack keeps using it until you ask for a newer one. Run `docker compose pull` to
refresh them from Docker Hub. This is also what lets CI point the stack at the image built from
the commit under test rather than whatever the published tag currently holds.

## What each file does

| File | Purpose | Brings up |
|---|---|---|
| `compose.yml` | Top-level — `include:`s every piece below, as the compose project `kinotic` | Full stack (ES + Kibana + OTEL + load-gen + migration + Azurite and the sites edge + Gitea + the three servers + the UIs) |
| `compose.elasticsearch.yml` | Elasticsearch | `kinotic-elasticsearch:9200` |
| `compose.kibana.yml` | Kibana (depends on Elasticsearch) | `kinotic-kibana:5601` |
| `compose.kinotic-migration.yml` | Runs `kinotic-migration` once against ES, then exits | One-shot job — `service_completed_successfully` is what the servers wait on |
| `compose.kinotic-servers.yml` | The three Kinotic servers, one Ignite cluster | `kinotic-server-management:58503`, `kinotic-server-system:58504`, `kinotic-server-app:58505` (REST and STOMP) |
| `compose.ui.yml` | The portal and the system console, the `kinotic-ui` image built from `kinotic-ui.Dockerfile`, which builds `kinotic-frontend` and serves both through `kinotic-ui.ts`. `docker compose up --build` builds it from the working tree | `kinotic-ui:9090` (portal), `kinotic-ui:9091` (system console) |
| `compose-otel.yml` | OpenTelemetry collector + Grafana + Tempo + Loki + Mimir | `grafana:3000`, `loki:3100`, `tempo:3200`, `mimir:9009` |
| `compose.gen-schemas.yml` | Load-generator container that pre-populates schemas | One-shot when `compose.yml` brings up the full stack |
| `compose.sites.yml` | Where published UIs are stored and served: the Azurite emulator, and the local edge, the `kinotic-sites-edge` image built from `sites-edge.Dockerfile`, which runs the Cloudflare sites Worker (`../cloudflare/sites-worker/worker.js`) over it through `sites-edge.ts`. `docker compose up --build` builds it from the working tree | `kinotic-azurite:10000`, `kinotic-sites-edge:8088` |
| `compose.gitea.yml` | The Gitea server the demonstration server hosts projects' repositories on, in place of GitHub: it creates the `kinotic_admin` administrator the servers act as on first start | `kinotic-gitea:3000` |
| `compose.keycloak.yml` | Local Keycloak as a social sign-in OIDC provider (dev-only secret) | `keycloak:8888` — see `KEYCLOAK_HOSTS_SETUP.md` |
| `compose.kinotic-test.yml` | Minimal: ES + migration only, no server | Backing services for the `kinotic-test` suite, which runs the server in-process |
| `compose.kinotic-e2e-test.yml` | Elasticsearch + migration + the servers, on the `e2e` profile | Used by e2e tests in CI |

## Common one-liners

```bash
# (1) ES and the migration container, which exits when done.
#     Re-run any time you bump kinotic-migration to refresh indices.
docker compose -f compose.elasticsearch.yml -f compose.kinotic-migration.yml up -d

# (2) Full self-contained stack (everything in containers, including the server)
docker compose up -d

# (3) Tail the management server's logs
docker compose logs -f kinotic-server-management

# (4) Hot-reload a server image after a build (the same for kinotic-server-system and kinotic-server-app)
./gradlew :kinotic-server-management:bootBuildImage
docker compose up -d --force-recreate --no-deps kinotic-server-management

# (5) Run only the migration container against an already-running ES, then exit
docker compose -f compose.kinotic-migration.yml run --rm kinotic-migration

# (6) Wipe all state and restart clean (down -v drops the ES named volume)
docker compose down -v && docker compose up -d

# (7) Remove a stack another compose project started, such as `docker-compose`, the project named after
#     this directory: every service has a fixed container_name, so its containers block `up` until they
#     are gone (add -v to drop its volumes too)
docker compose -p docker-compose down
```

## Service URLs (when the full stack is up)

| Service | URL | Notes |
|---|---|---|
| Portal | <http://localhost:9090> | TLS off in compose. Served by `kinotic-ui`; every path that names no file answers with the SPA's `index.html` |
| System console | <http://localhost:9091> | Served by `kinotic-ui`, as the portal is; system users sign in here |
| Management server | <http://localhost:58503> | REST, and STOMP at `ws://localhost:58503/v1` — what the SPA's `Kinotic.connect(...)` opens |
| System server | <http://localhost:58504> | REST, and STOMP at `ws://localhost:58504/v1`, for the system console |
| App server | <http://localhost:58505> | REST, and STOMP at `ws://localhost:58505/v1`; each application's API host is `<organizationId>--<applicationId>.localhost:58505` |
| Elasticsearch | <http://localhost:9200> | `xpack.security.enabled=false` — local only |
| Kibana | <http://localhost:5601> | |
| A published UI | `http://<app>--<ui>.<org>.sites.localhost:8088` | Served by `kinotic-sites-edge` from Azurite; the project's deployment page links it. Chrome, Edge and Firefox resolve `*.localhost` to this machine |
| Azurite | <http://localhost:10000/devstoreaccount1> | Azurite's `devstoreaccount1` development account, which the publish and removal workloads upload to and delete from |
| Gitea | <http://localhost:3001> | Every project's repository, in the Gitea organization named by its Kinotic organization's id. Sign in as `kinotic_admin` / `kinotic-gitea-admin` to browse them |
| Grafana | <http://localhost:3000> | When `compose-otel.yml` is included. Anonymous auth with the Admin role — no login |
| Keycloak | <http://keycloak:8888> | When `compose.keycloak.yml` is included; requires `127.0.0.1 keycloak` in `/etc/hosts` per `KEYCLOAK_HOSTS_SETUP.md` |

## Sample data

The full stack seeds the `test-org` organization for evaluators: `compose.yml` runs the
migration with the `fixtures` profile (set in `evaluation.env`), and `kinotic-gen-schemas`
adds the `ecommerce` and `healthcare` applications with entity data. Sign in at
<http://localhost:9090> as `kinotic@kinotic.local` / `kinotic` to see it. The other stacks
include `compose.kinotic-migration.yml` without `evaluation.env` and seed none of it.

## Try the auth flow (UI devs)

The full compose stack (`docker compose up -d`) is the demonstration server: every server runs the
`demonstration` profile, which sends no email and offers sign-up with an email and a password.

```bash
docker compose up -d
open http://localhost:9090
```

1. Click **Sign Up**, fill in your name and email, submit. The page goes straight to the verification
   step, since no email will arrive.
2. Name your organization and set a password; you are signed in.
3. On **Members**, add someone with an invitation: a dialog shows the invitation's link to send them,
   or to open yourself.
4. Create an application and a project in it, or have a coding agent do both: the project's repository
   is created on the stack's Gitea from the project template, which the management server downloads
   from GitHub, so creating a project needs an internet connection. A push to its default branch deploys it,
   on a vm-manager node running beside the stack.

To connect a coding agent, install the Kinotic plugin from `kinotic-ai/agent-plugin` (see the
[quick start](../../website/content/01.getting-started/02.quick-start.md)), whose own MCP server is Kinotic Cloud's,
and add this server beside it:

```bash
# Claude Code, then /mcp → kinotic-local → authenticate
claude mcp add -s user --transport http kinotic-local http://localhost:58503/mcp
# Codex
codex mcp add kinotic-local --url http://localhost:58503/mcp && codex mcp login kinotic-local
```

Grok Build takes it in `~/.grok/config.toml`, then `/mcps` → `kinotic-local` → `i`:

```toml
[mcp_servers.kinotic-local]
url = "http://localhost:58503/mcp"
oauth_client_id = "https://kinotic.ai/oauth/grok-build-client.json"
```

Signing in opens the portal's consent page on 9090; sign in, or sign up, and approve. Only a coding
agent on this machine can reach the server: ChatGPT, Grok and the Claude apps' connectors cannot reach
`localhost`.

### Adding "Continue with Keycloak" (local OIDC, no internet)

To exercise the social sign-in OIDC plumbing without needing a real IdP, layer in
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
  The management server's container picks the secret up from the `KINOTIC_AKV_KEYCLOAK` env
  var via the dev-fallback `EnvVarSecretReferenceResolver`.
- The **Continue with Keycloak** button isn't wired automatically — the social-button
  list is sourced from the `type=ORG_SIGNUP` rows of `kinotic_oidc_configuration`, and no migration
  ships a Keycloak entry. Seed one manually via the kinotic-migration tool if you want
  the button to appear.

## Developing with images built from your working tree

The servers run in compose while you develop them too. Build the images of what you change, then bring
the stack up; compose runs `kinoticai/<module>:<version>` from your machine rather than pulling it:

```bash
# from the repository root
./gradlew :kinotic-server-management:bootBuildImage :kinotic-server-system:bootBuildImage \
          :kinotic-server-app:bootBuildImage :kinotic-migration:bootBuildImage
cd deployment/docker-compose
docker compose up -d
```

Rebuild an image and run `docker compose up -d` again to replace its container.

### Traces, metrics, and logs in Grafana

Add `compose-otel.yml` to bring up the collector and the Grafana stack next to Elasticsearch:

```bash
docker compose -f compose-otel.yml -f compose.elasticsearch.yml -f compose.kibana.yml up -d
```

The compose servers export to the collector, `otel-collector:4317` on the compose network.

Grafana is at <http://localhost:3000> (anonymous Admin, no login) and opens on the **Kinotic
Server** dashboard — JVM, HTTP RED metrics, span rates from the traces themselves, and a log
panel, all provisioned from `dashboards/kinotic-server.json`. Beyond the dashboard:

| Signal | Datasource | Where to look |
|---|---|---|
| Traces | Tempo | Explore → Tempo → Search, service name `kinotic-server-management`, `kinotic-server-system` or `kinotic-server-app` (tenant `kinotic-system`) |
| Metrics | Mimir | Explore → Mimir, e.g. `jvm_memory_used_bytes{job="kinotic-server-management"}` (tenant `kinotic-system`) |
| Logs | Loki | Explore → Loki, `{service_name="kinotic-server-management"}`, one node by adding `service_instance_id` (tenant `kinotic-system`) |

All three run multi-tenant. The server's own telemetry lands in the `kinotic-system` tenant —
the collector stamps it on pushes that name none — and each organization's workload telemetry
in a tenant named by the organization id, pushed by the vm-manager on every node. The Grafana
datasources browse `kinotic-system`; change the `X-Scope-OrgID` header on a datasource to browse
an organization, or query it in the portal, which reads the tenant the signed-in user may see.

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
- **Vert.x metrics** arrive through Micrometer's global registry, which the agent bridges onto the
  OTLP exporter when `OTEL_INSTRUMENTATION_MICROMETER_ENABLED` is `true` — so they are only exported
  with the agent attached and that flag set. What reaches Mimir is the gauges and the failure counters:
  `vertx_{http,net}_{server,client}_active_connections`, `vertx_http_{server,client}_active_requests`,
  `vertx_http_{server,client}_active_ws_connections`, `vertx_eventbus_pending`,
  `vertx_eventbus_handlers`, `vertx_pool_in_use`, `vertx_pool_queue_pending`, `vertx_pool_ratio`,
  `vertx_eventbus_reply_failures_total`, `vertx_{http,net}_{server,client}_errors_total` and
  `vertx_http_server_request_resets_total`. `vertx_http_server_active_ws_connections` counts
  the STOMP connections on `/api/v1`, the servers' only WebSocket endpoint. `KinoticVertxConfig`
  denies Vert.x's request counters and response timers, whose requests the agent records as
  `http_server_request_duration_seconds_*` and `http_client_request_duration_seconds_*`, and the
  meters that record on every socket read and write, event bus message, request or worker task: the
  byte counters and size histograms, the per-message event bus counters, and the pool timers and
  completed-task counter.
- **Tempo's metrics-generator** writes its own series to Mimir — `traces_spanmetrics_calls_total`
  and `traces_spanmetrics_latency_bucket`, labelled `service`, `span_name`, `span_kind`,
  `status_code`, and `peer_service` on HTTP client spans to a named backend. These only exist
  because `tempo.yml` enables the processors under `overrides`; the `metrics_generator.processor`
  block alone does nothing.
- **Loki** promotes `service.name` to the `service_name` index label and keeps `trace_id` /
  `span_id` as structured metadata, which is what the Tempo link on each log line matches.
- **Span names come from semconv, not from the code**: `get` / `index` / `search` are
  Elasticsearch endpoint ids, and a bare `GET` / `PUT` / `POST` is an HTTP span with no route
  template. Each Elasticsearch call therefore appears twice — the client span and its transport
  child, and each Loki, Tempo or Mimir query the servers make is a bare `GET` / `POST` too.
  `peer.service` and `db.system` are what identify the far end, and Tempo resolves them
  into virtual nodes on the service graph. `OTEL_INSTRUMENTATION_COMMON_PEER_SERVICE_MAPPING`
  sets `peer.service` on each HTTP client span from the host and port it called (`elasticsearch`,
  `loki`, `tempo`, `mimir`), so `{peer_service!="elasticsearch"}` leaves the Elasticsearch
  transport spans out of a query; the endpoint spans keep their `get` / `index` / `search` names.

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

The servers send no email: each link a person must open is logged and shown in the portal
([Demonstration server](../../website/content/04.platform/03.configuration.md)).

If you also run the Vite frontend (`pnpm dev` on `:5173`), it calls the management server directly at
`localhost:58503` via `VITE_KINOTIC_HOST`/`VITE_KINOTIC_PORT` — see
`kinotic-frontend/apps/portal/ENV_SETUP.md`.

## Storage paths

- `kinotic-elastic-data` — Elasticsearch data, a Docker named volume. Survives
  `docker compose down`; removed by `docker compose down -v`.
- `kinotic-azurite-data` — every published UI's files, a Docker named volume, kept and removed
  the same way.

## Published UIs

A deployment of a project with UIs uploads each built UI to Azurite, as it uploads to the sites
storage account in Azure: the system server's `demonstration` profile sets
`kinotic.systemApi.uiDeployment.siteStorage: AZURITE` and points the publish workload at
`http://192.168.127.254:10000/devstoreaccount1`, Azurite where a BOXLITE workload reaches this machine's
loopback. `kinotic-sites-edge` serves each UI at `http://<app>--<ui>.<org>.sites.localhost:8088`
by running the same `worker.js` the Cloudflare edge runs, over Azurite. On start it creates the
private `sites` container and signs the Worker's read SAS with Azurite's well-known development key,
which terraform does for the real account.

Each upload and removal URL names its site's directory and carries a SAS for the whole `sites`
container, signed with Azurite's well-known development key: Azurite honors no directory SAS, and the
key is public, so a workload on this machine is not confined to its own site as it is in Azure.
