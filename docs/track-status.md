# Track Status

Living doc — running snapshot of in-flight platform tracks, what's working
end-to-end on each, and what's queued. Expected to be deleted/folded into the
docsite once each track lands.

Active tracks:
1. **Auth / IAM** — org/auth/IAM rebuild
2. **Observability stack** — bring kind and Azure to parity with what compose now has (full OTel pipeline into Loki + Tempo + Mimir + Grafana)

---

## 1. Auth / IAM

### Working end-to-end

- **Local password signup → email-verify → org admin user**
  Posts to `/api/signup`, kinotic-server logs the verification URL (email is disabled in
  dev), user clicks, sets password, organization + admin `IamUser` (LOCAL) +
  `IamCredential` are created.
- **Email-first login lookup → password branch**
  `POST /api/login/lookup {email}` returns `{type: "password"}`; frontend collects the
  password, posts `/api/login/token`, gets a Kinotic JWT, opens STOMP CONNECT with
  `Authorization: Bearer …`. The browser never sends raw credentials over the WebSocket.
- **Direct STOMP CONNECT with `login`/`passcode`/`authScopeType`/`authScopeId`** for CLI
  / automation that already knows its scope. Path preserved deliberately.
- **Microsoft Entra (multi-tenant + personal accounts)**
  `kinotic.oidc.platformProviders[0]` wired with audience
  `AzureADandPersonalMicrosoftAccount` and authority `https://login.microsoftonline.com/common/v2.0`.
  Confidential client; secret stored at `~/.kinotic/dev-oidc-secrets/entra-platform`.
  Discovery's literal `{tenantid}` placeholder handled by `setValidateIssuer(false)` +
  `OAuth2AuthFactory.isIssuerValid` substitution from the JWT's `tid` claim.
- **Google**
  `kinotic.oidc.platformProviders[1]`, authority `https://accounts.google.com`.
  No special handling — direct `iss`/authority match, `email_verified` claim required.
  Validated through the same `LoginHandler.handleSocialStart` → callback path.
- **Keycloak (compose)**
  `compose.keycloak.yml` rewired against the new flow (was injecting the retired
  `OIDC_SECURITY_SERVICE_*` env vars). Confidential client + hardcoded dev secret in
  `keycloak-test-realm.json` + `dev-keycloak-oidc-secret` (both committed, dev-only).
  Requires `127.0.0.1 keycloak` in `/etc/hosts` (`KEYCLOAK_HOSTS_SETUP.md`).
- **Branded social buttons**
  `SocialAuthButton.vue` renders the official Google web pill (light/dark × sign-in/sign-up)
  and a composed Microsoft button per Entra brand specs. Falls back to a generic styled
  button for unknown providers (e.g. Keycloak).
- **Split api/app base URLs**
  `kinotic.appBaseUrl` → SPA origin (used for verification email links + post-OIDC SPA
  redirects). `kinotic.apiBaseUrl` (optional, falls back to `appBaseUrl`) → backend origin
  used for OIDC `redirect_uri`. Required for the Azure split-origin deploy
  (`portal.kinotic.ai` SPA + `api.kinotic.ai` AKS).

### Architectural decisions in place

- Two distinct OIDC roles, one entity:
  - **Platform OIDC** — referenced from `KinoticSystem.oidcConfigurationIds`. Bootstrapped
    by `PlatformOidcBootstrap` from `kinotic.oidc.platformProviders[]`. Shows as a button
    on `/login` and `/signup`. Currently social only (Google, Microsoft consumer); shape
    is general enough that system-OIDC could reuse if ever needed.
  - **Per-org SSO** — referenced from `Organization.oidcConfigurationIds`. Reached via
    email-first lookup. Admin UI to manage these is **not built yet**.
- `platformWide` boolean on `OidcConfiguration` was removed — the role is determined by
  *which scope references the config*, not a flag.
- `builtIn` boolean on `OidcConfiguration` was removed — same reason; vestigial.
- `IamUser.isDefault` renamed to `IamUser.primary` to match the codebase's broader
  boolean-naming convention.
- `OidcConfiguration.provider` is now an `OidcProviderKind` enum, not a `String`. Wire
  format preserved via `@JsonValue`/`@JsonCreator` on the enum's `key()`/`fromKey()`.
  Same applies to `PlatformOidcProperties.PlatformProviderEntry.provider`.
- JWT claims `authScopeType` / `authScopeId` (renamed from `scopeType` / `scopeId`) so
  the wire matches the rest of the system's field naming.
- Provider-aware validation lives in two helpers in
  `org.kinotic.gateway.internal.auth.OAuth2AuthFactory`:
  - `isIssuerValid(claims, authority)` — direct match for fixed-issuer providers, dynamic
    substitution (using JWT's `tid`) for Microsoft `/common` / `/organizations`.
  - `isEmailVerified(claims, provider)` — explicit `email_verified=true` for providers
    that emit it (Google, Keycloak, Auth0, Okta, generic); presence-trusted for Entra and
    Apple (which don't emit it). Fail-closed on missing email regardless of provider.
- System-level operators **do not authenticate via OIDC**. That path is deferred and out
  of scope for `platformProviders[]`.

### Recently retired (no consumers, removed)

- `OidcSecurityService`, `OidcSecurityServiceProperties`, `OidcProvider`, `JwksService`,
  `DefaultJwksService` — the kinotic-core OIDC stack. Replaced end-to-end by Vert.x's
  `OpenIDConnectAuth.discover` in `OAuth2AuthFactory` + `IamSecurityService` for the
  STOMP-CONNECT terminal validation.
- `TemporarySecurityService` — zombie since the gateway moved to the new flow.
- `frontendConfigurationPath` — endpoint and property; SPA stopped consuming it during
  the OIDC server-side move.
- All `tests.auth/*` test classes + the Keycloak test base — they covered the retired
  flow. **Auth-flow integration tests against the new model are pending.**

### Endpoint reference

| Method | Path | Owner |
|---|---|---|
| `POST` | `/api/signup` | `SignUpHandler` |
| `POST` | `/api/signup/complete` | `SignUpHandler` |
| `POST` | `/api/signup/start/:provider` | `OidcSignupHandler` |
| `GET`  | `/api/signup/callback/:configId` | `OidcSignupHandler` |
| `POST` | `/api/signup/complete-org` | `OidcSignupHandler` |
| `GET`  | `/api/login/providers` | `LoginHandler` |
| `POST` | `/api/login/lookup` | `LoginHandler` |
| `POST` | `/api/login/token` | `LoginHandler` |
| `POST` | `/api/login/start/:provider` | `LoginHandler` |
| `GET`  | `/api/login/callback/:configId` | `LoginHandler` |
| `POST` | `/api/register/complete` | `LoginHandler` |

---

## 2. Observability stack

### Where each environment is today

| | otel-collector | Loki | Tempo | Mimir | Grafana | Notes |
|---|---|---|---|---|---|---|
| **compose** | ✅ 0.151.0 | ✅ 3.3.2 | ✅ 2.6.1 | ✅ 2.14.3 | ✅ 12.3.1 | Java agent → otel-collector → fan-out. Pinned versions; `loki` exporter migrated to `otlphttp/loki`. |
| **kind** | ❌ | ✅ chart 6.29.0 | ❌ | ❌ | ✅ chart 8.14.0 | Alloy DaemonSet scrapes container logs → Loki push API. No traces or metrics path. |
| **Azure (AKS)** | ❌ | ✅ via helm | ❌ | ❌ | ✅ via helm | Same shape as kind — see `terraform/azure/cluster/observability.tf`. |

### Target architecture (kind + Azure)

One collector binary in two deployment modes, mirroring compose's pipeline:

| Mode | Purpose | Replaces |
|---|---|---|
| `Deployment` (Service-fronted) | Receives OTLP from kinotic-server's Java agent → fans out to Loki/Tempo/Mimir | (the gap — currently nothing) |
| `DaemonSet` running `filelog` receiver | Scrapes container logs node-locally → OTLP → Loki | Alloy |

Same OTel-Collector binary, two configs, identical to compose's gateway config (which means we write the gateway config once and reuse). Vendor-neutral CNCF project; native fit for the Java OTel agent already wired into kinotic-server.

### Phase 1 — gain trace + metric parity (kind + Azure)

Goal: kinotic-server's traces and metrics flow into Tempo and Mimir. Logs continue
through Alloy (unchanged) for now.

- Add helm releases:
  - `grafana/tempo` (matching compose pin: `2.6.1`)
  - `grafana/mimir` (matching compose pin: `2.14.3`)
  - OTel Collector contrib as a `Deployment` with a Service named `otel-collector` exposing OTLP gRPC `:4317` and HTTP `:4318`
- Move existing `otel-collector-config.yaml` content to a configmap shared with the OTel-Collector helm release. Endpoints become the in-cluster Service DNS names (`tempo.observability.svc:4317`, `loki.observability.svc:3100/otlp`, `mimir.observability.svc:9009/otlp`).
- Set kinotic-server's `OTEL_EXPORTER_OTLP_ENDPOINT` to `http://otel-collector.observability.svc:4317` via helm values (currently this env var is only set in compose).
- Add Tempo + Mimir datasources to `values-grafana.yaml` (and `values-grafana-azure.yaml`).
- Reuse `helm/observability/` value files where possible — kind and Azure already share them for Loki + Grafana.

### Phase 2 — collapse to one collector product

Goal: drop Alloy; OTel Collector handles both OTLP gateway and node-local log scraping.

- Add a second OTel Collector helm release as `DaemonSet` mode with `filelog` receiver
  pointed at `/var/log/containers/*.log`, k8s metadata enrichment via the `k8sattributes`
  processor, and OTLP export to the gateway-mode collector (or directly to Loki — TBD;
  going through the gateway is simpler if we end up wanting central processing).
- Remove `helm_release.alloy` + the alloy configmap from `terraform/azure/cluster/observability.tf` and `terraform/kind/observability.tf`.
- Delete `helm/observability/values-alloy.yaml` and `alloy-config.alloy`.

Defer Phase 2 until Phase 1 is stable in both kind and Azure — Alloy doing logs is fine in the meantime.

### Open questions before starting Phase 1

- **Loki version on kind/Azure.** kind currently uses Loki helm chart `6.29.0` whose `appVersion` should support `/otlp`. Worth confirming on first deploy that OTLP ingest works with whatever Loki version that chart resolves to; if not, pin to a chart version aligned with Loki 3.3.x.
- **Service name in helm.** The compose collector is reachable as `otel-collector`; in k8s it'll be `otel-collector.observability.svc.cluster.local` (or whatever short form the resolver picks up). Helm values for kinotic-server need to set `OTEL_EXPORTER_OTLP_ENDPOINT` accordingly per env.
- **Resource sizing.** Compose collector runs in a single shared container; the k8s `Deployment` should start at `requests: 128Mi/100m`, scale by HPA on CPU. Real numbers come from Phase 1 telemetry.

---

## What's next (cross-track backlog)

Rough priority order — pick based on what unblocks customers fastest.

### A. Observability Phase 1 (recommended next)
Bring kind to OTel parity per Section 2 above. Lower risk than the auth-track items
(no API surface change), unblocks better debug feedback for everything else.

### B. Org-level invitations
Invite-by-email so an org admin can onboard their users.

- New `Invitation` entity: `{id, orgId, email, role, verificationToken, status,
  invitedBy, invitedAt, expiresAt, acceptedAt, acceptedAs}`.
- New endpoints under `/api/admin/orgs/:orgId/invitations` (admin) and
  `/api/invite/{lookup,accept/password,accept/start/:provider,accept/callback/:configId}`
  (public). Invitee can accept via password OR via a social/SSO provider whose returned
  email matches the invited address (with a UI fallback for soft mismatch).
- Roles: introduce `ADMIN` / `MEMBER` minimally now (invitation needs *some* role
  concept). Expand later in a dedicated roles track.
- Token: single-use, 7-day default expiry. Resend + revoke endpoints for admins.

### C. Per-org SSO admin UI
Reach for this once invitations are stable — SSO-only orgs need invitations first to
onboard their first admin via password before configuring their org's SSO.

- New `kinotic-frontend/src/pages/org-admin/OidcConfigurations.vue`.
- Form covers `name`, `provider` (`OidcProviderKind` dropdown excluding multi-tenant Entra
  variants), `clientId`, `authority`, write-only `clientSecret`, `domains[]`,
  `audience`, `rolesClaimPath`, `additionalScopes`, `enabled`.
- Secret handling mirrors `PlatformOidcBootstrap`: the API stashes via
  `SecretStorageService` and stores `clientSecretRef` on the entity. Reading the entity
  never returns the secret.
- Endpoints under `/api/admin/orgs/:orgId/oidc-configurations`.

### D. Kind keycloak rewire
Mirrors what we did for compose. `kind/terraform/kinotic.tf:47-51` still uses the dead
`kubernetes-oidc` profile + `oidc.enabled=true` when `enable_keycloak=true`. Becomes
helm `kinotic.oidc.platformProviders[0].*` sets + a k8s Secret terraform creates with
the matching client secret (mounted via the existing `oidcSecrets` mechanism). Smaller
surface than B/C but worth doing before the next kind workshop / demo.

### E. Test restructure (incremental)
- Replace `DummySecurityService`'s now-no-op `@ConditionalOnProperty` with a meaningful
  gate (or delete and use the real `IamSecurityService` everywhere with a seeded test
  user).
- Build new auth integration tests against `LoginHandler` / `IamSecurityService` /
  `PlatformOidcBootstrap` shape. Wiremock or a localhost HTTP stub may be lighter than
  bringing back Keycloak Testcontainers.
- Fix the pre-existing `EntityDefinitionCrudTests` drift from commit `1b3e3af2e`
  (`EntityDefinition` lost `@Accessors(chain=true)` — orthogonal to this track but
  unblocking gradle `:check`).
