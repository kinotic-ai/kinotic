# Docker Compose

Containerized stacks for local Kinotic development. Pick the bundle that matches your
workflow — they're built up from small `compose.*.yml` files using docker-compose's
`include:` mechanism, so you can swap pieces in and out without forking a whole stack.

## Pick your scenario

| You want… | Run |
|---|---|
| **Everything in containers, fastest path** | `docker compose up -d` |
| **IntelliJ-running kinotic-server, just ES + Kibana** | `docker compose -f compose.ek-stack.yml up -d` |
| **IntelliJ + ES + run migrations once** | `docker compose -f compose.ek-stack.yml -f compose.kinotic-migration.yml up -d` |
| **Full stack with OIDC via local Keycloak** | `docker compose -f compose.yml -f compose.keycloak.yml up -d` |
| **Test runtime (no observability, ephemeral ES)** | `docker compose -f compose.kinotic-test.yml up -d` |
| **Apple Silicon (M-series) — ES SVE workaround** | append `-f compose.ek-m4.override.yml` to any of the above |

`docker compose down` to stop. `docker compose down -v` to also wipe volumes (ES data).

## What each file does

| File | Purpose | Brings up |
|---|---|---|
| `compose.yml` | Top-level — `include:`s every piece below | Full stack (ES + Kibana + OTEL + load-gen + migration + kinotic-server) |
| `compose.ek-stack.yml` | Elasticsearch + Kibana | `kinotic-elasticsearch:9200`, `kinotic-kibana:5601` |
| `compose.kinotic-migration.yml` | Runs `kinotic-migration` once against ES, then exits | One-shot job — `service_completed_successfully` is what kinotic-server waits on |
| `compose.kinotic-server.yml` | The Kinotic server itself | `kinotic-server:9090/58503` (UI, STOMP) |
| `compose-otel.yml` | OpenTelemetry collector + Grafana + Tempo + Mimir | `grafana:3000`, plus internal otel/tempo/mimir |
| `compose.gen-schemas.yml` | Load-generator container that pre-populates schemas | One-shot when `compose.yml` brings up the full stack |
| `compose.keycloak.yml` | Local Keycloak as a platform OIDC provider (dev-only secret) | `keycloak:8888` — see `KEYCLOAK_HOSTS_SETUP.md` |
| `compose.kinotic-test.yml` | Minimal: ES + migration + server with `SPRING_PROFILES_ACTIVE=test` | Used by integration tests |
| `compose.kinotic-e2e-test.yml` | EK + migration only (server runs externally) | Used by e2e tests in CI |
| `compose.ek-m4.override.yml` | Adds `_JAVA_OPTIONS=-XX:UseSVE=0` for ES on Apple Silicon | Override only — not standalone |

## Common one-liners

```bash
# (1) Backing services for IntelliJ-local kinotic-server dev
#     Runs ES, Kibana, then the migration container which exits when done.
#     Re-run any time you bump kinotic-migration to refresh indices.
docker compose -f compose.ek-stack.yml -f compose.kinotic-migration.yml up -d

# (2) Full self-contained stack (everything in containers, including the server)
docker compose up -d

# (3) Tail the kinotic-server logs (look here for verification URLs in dev — email is off)
docker compose logs -f kinotic-server

# (4) Hot-reload server image after a build
./gradlew :kinotic-server:bootBuildImage
docker compose up -d --force-recreate --no-deps kinotic-server

# (5) Run only the migration container against an already-running ES, then exit
docker compose -f compose.kinotic-migration.yml run --rm kinotic-migration

# (6) Wipe all state and restart clean
docker compose down -v && rm -rf ~/kinotic/elastic-data && docker compose up -d
```

## Service URLs (when the full stack is up)

| Service | URL | Notes |
|---|---|---|
| Kinotic UI | <http://localhost:9090> | TLS off in compose. The `/login`, `/signup`, `/applications` routes are SPA. |
| STOMP | `ws://localhost:58503/v1` | Used by the SPA's `Kinotic.connect(...)` |
| Elasticsearch | <http://localhost:9200> | `xpack.security.enabled=false` — local only |
| Kibana | <http://localhost:5601> | |
| Grafana | <http://localhost:3000> | When `compose-otel.yml` is included (admin/admin) |
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
- A `kinotic-client` confidential client whose secret is hardcoded in
  `keycloak-test-realm.json` (matched by `dev-keycloak-oidc-secret`, mounted into
  kinotic-server as a single file). Both files are committed because the value is
  dev-only — never reuse for anything reachable beyond a developer's laptop.
- A **Continue with Keycloak** button on `/login` and `/signup`, populated by
  `PlatformOidcBootstrap` from the `KINOTIC_OIDC_PLATFORMPROVIDERS_0_*` env vars in
  `compose.keycloak.yml`.

To enable real **Continue with Google / Microsoft** social buttons in compose, follow
[`docs/local-oidc-setup.md`](../../docs/local-oidc-setup.md) — drop the client secret
into the same secrets dir and add another `KINOTIC_OIDC_PLATFORMPROVIDERS_*` block on
the kinotic-server service.

## Dev with kinotic-server in IntelliJ

This is the recommended workflow when iterating on backend code:

```bash
# 1. ES + Kibana + run migrations once
docker compose -f compose.ek-stack.yml -f compose.kinotic-migration.yml up -d

# 2. Wait for migration to finish (it's a one-shot)
docker compose ps kinotic-migration   # State: Exited (0)

# 3. Run KinoticServerApplication in IntelliJ with profile `development`.
#    application-development.yml already points kinotic.persistence at localhost:9200.
```

Email is `enabled: false` in `application-development.yml`; verification links print to
the IntelliJ console. See [`docs/local-oidc-setup.md`](../../docs/local-oidc-setup.md) to
wire a platform OIDC provider.

If you also run the Vite frontend (`pnpm dev` on `:5173`), it proxies `/api/*` to
`http://localhost:9090` automatically — just set `KINOTIC_APPBASEURL=http://localhost:5173`
on the IntelliJ run config so OIDC redirect URIs match what's registered with the IdP.

## Storage paths

- `~/kinotic/elastic-data` — Elasticsearch data dir (host bind mount). Survives
  `docker compose down`; gone with `docker compose down -v` only if you also `rm -rf` it.
- No host volumes for the kinotic-server container — it's stateless.

## Apple Silicon (M-series) note

Recent ES versions hit a SIGILL on M-series CPUs because of SVE auto-detection. Append
`-f compose.ek-m4.override.yml` to any command above; the override sets
`_JAVA_OPTIONS=-XX:UseSVE=0` on the ES container.

## When you outgrow docker-compose

For multi-node clustering (Ignite cluster discovery, replica counts, real workload
identity, etc.) use the KinD setup in `deployment/kind/`. See
`deployment/kind/terraform/` for the Terraform-driven local Kubernetes story.
