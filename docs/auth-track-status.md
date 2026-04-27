# Auth Track — Status

Snapshot of where the org/auth/IAM rebuild is, what's working end-to-end, and what's
queued. Living doc — expected to be deleted/merged into the docsite once the track lands.

## Working end-to-end

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

## Architectural decisions in place

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
- Provider-aware validation lives in two helpers in
  `org.kinotic.gateway.internal.auth.OAuth2AuthFactory`:
  - `isIssuerValid(claims, authority)` — direct match for fixed-issuer providers, dynamic
    substitution (using JWT's `tid`) for Microsoft `/common` / `/organizations`.
  - `isEmailVerified(claims, provider)` — explicit `email_verified=true` for providers
    that emit it (Google, Keycloak, Auth0, Okta, generic); presence-trusted for Entra and
    Apple (which don't emit it). Fail-closed on missing email regardless of provider.
- System-level operators **do not authenticate via OIDC**. That path is deferred and out
  of scope for `platformProviders[]`.

## Recently retired (no consumers, removed)

- `OidcSecurityService`, `OidcSecurityServiceProperties`, `OidcProvider`, `JwksService`,
  `DefaultJwksService` — the kinotic-core OIDC stack. Replaced end-to-end by Vert.x's
  `OpenIDConnectAuth.discover` in `OAuth2AuthFactory` + `IamSecurityService` for the
  STOMP-CONNECT terminal validation.
- `TemporarySecurityService` — zombie since the gateway moved to the new flow.
- `frontendConfigurationPath` — endpoint and property; SPA stopped consuming it during
  the OIDC server-side move.
- All `tests.auth/*` test classes + the Keycloak test base — they covered the retired
  flow. **Auth-flow integration tests against the new model are pending.**

## Endpoint reference

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

## What's next

Three tracks in rough priority order. Pick whichever unblocks customers fastest.

### 1. Org-level invitations (recommended next)
Invite-by-email so an org admin can onboard their users. Architecture sketch:

- New `Invitation` entity: `{id, orgId, email, role, verificationToken, status,
  invitedBy, invitedAt, expiresAt, acceptedAt, acceptedAs}`.
- New endpoints under `/api/admin/orgs/:orgId/invitations` (admin) and
  `/api/invite/{lookup,accept/password,accept/start/:provider,accept/callback/:configId}`
  (public). Invitee can accept via password OR via a social/SSO provider whose returned
  email matches the invited address (with a UI fallback for soft mismatch).
- Roles: introduce `ADMIN` / `MEMBER` minimally now (invitation needs *some* role
  concept). Expand later in a dedicated roles track.
- Token: single-use, 7-day default expiry. Resend + revoke endpoints for admins.

### 2. Per-org SSO admin UI
Reach for this once invitations are stable — SSO-only orgs need invitations first to
onboard their first admin via password before configuring their org's SSO.

- New `kinotic-frontend/src/pages/org-admin/OidcConfigurations.vue`.
- Form covers `name`, `provider` (OidcProviderKind dropdown excluding multi-tenant Entra
  variants), `clientId`, `authority`, write-only `clientSecret`, `domains[]`,
  `audience`, `rolesClaimPath`, `additionalScopes`, `enabled`.
- Secret handling mirrors `PlatformOidcBootstrap`: the API stashes via
  `SecretStorageService` and stores `clientSecretRef` on the entity. Reading the entity
  never returns the secret.
- Endpoints under `/api/admin/orgs/:orgId/oidc-configurations`.

### 3. Kind keycloak rewire
Mirrors what we did for compose. `kind/terraform/kinotic.tf:47-51` still uses the dead
`kubernetes-oidc` profile + `oidc.enabled=true` when `enable_keycloak=true`. Becomes
helm `kinotic.oidc.platformProviders[0].*` sets + a k8s Secret terraform creates with
the matching client secret (mounted via the existing `oidcSecrets` mechanism). Smaller
surface than #1 or #2 but worth doing before the next kind workshop / demo.

### 4. Test restructure (incremental)
- Replace `DummySecurityService`'s now-no-op `@ConditionalOnProperty` with a meaningful
  gate (or delete and use the real `IamSecurityService` everywhere with a seeded test
  user).
- Build new auth integration tests against `LoginHandler` / `IamSecurityService` /
  `PlatformOidcBootstrap` shape. Wiremock or a localhost HTTP stub may be lighter than
  bringing back Keycloak Testcontainers.
- Fix the pre-existing `EntityDefinitionCrudTests` drift from commit `1b3e3af2e`
  (`EntityDefinition` lost `@Accessors(chain=true)` — orthogonal to this track but
  unblocking gradle `:check`).

## Pending merge: `c8ab30c37 Refactor CORS configuration to core module (#170)`

One commit on `develop` not yet on our branch. Significant overlap — see the next
section before pulling.

### Files Navid touched (24 total) — categorised against our work

| File | Theirs | Ours | Likely conflict shape |
|---|---|---|---|
| `kinotic-core/api/config/CorsProperties.java` | **new** | (we added our own at `kinotic-gateway/api/config/CorsProperties.java`) | Take theirs (core is the right home); delete ours; update `KinoticApiGatewayProperties` to reference `org.kinotic.core.api.config.CorsProperties`. |
| `kinotic-core/internal/utils/CorsUtil.java` | **new** | (none) | Take as-is. Refactor our inline `buildCorsHandler` in `ApiGatewayVertcleFactory` to call `CorsUtil.createCorsHandler`. |
| `kinotic-core/api/config/KinoticProperties.java` | +5 lines (CORS field) | unchanged | Accept theirs. |
| `kinotic-domain/api/services/iam/IamUserService.java` | +66 lines: comments out `findByScope`, `createUser`, `changePassword`, `resetPassword`; adds `findFirstByEmailInScopeType` | We added `findByEmailPrimary`, `findByEmailAtScopeType`, `findByOidcIdentityAndScope`, `findByOidcIdentity`; renamed `findByEmailDefault` → `findByEmailPrimary` | **Hand-merge.** Their `findFirstByEmailInScopeType` is functionally identical to our `findByEmailAtScopeType` — adopt their name (clearer "first" semantics) and remove ours. Keep all our other additions. Decide whether to delete or restore the methods they commented out. |
| `kinotic-domain/internal/api/services/iam/DefaultIamUserService.java` | +19 lines | We added `findByEmailAtScopeType` impl + renamed `findByEmailDefault` impl + changed `field("isDefault")` → `field("primary")` | Hand-merge — keep our renames, adopt their method name in place of our `findByEmailAtScopeType`. |
| `kinotic-domain/internal/api/services/iam/DefaultSignUpService.java` | +6 lines | We swapped `findByEmailAndScope(…, null)` → our `findByEmailAtScopeType` and added `setPrimary(true)` | Update our caller to use their renamed method (`findFirstByEmailInScopeType`). |
| `kinotic-domain/internal/api/services/iam/IamSecurityService.java` | +3 lines | We did not touch | Accept theirs. |
| `kinotic-domain/internal/api/services/EmailService.java` | +2 lines (likely template path) | unchanged | Accept theirs. |
| `kinotic-domain/.../resources/templates/email/verification-email.html` | **new** | unchanged | Accept theirs. |
| `kinotic-api-gateway/internal/endpoints/rest/SignUpHandler.java` | +5 lines | We did not touch this file in this round | Accept theirs. |
| `kinotic-frontend/src/pages/signup/Signup.vue` | +46 lines | We added `apiUrl()` plumbing + provider buttons | Hand-merge — likely both touch the form structure. Bring in theirs, then re-apply our `apiUrl(...)` for `:action` + `fetch()` calls. |
| `kinotic-frontend/src/pages/signup/VerifyEmail.vue` | +121 lines | We added `apiUrl()`, password-match indicator, `flattenClaims` adapter | **Most painful conflict.** Take theirs as the structural base; re-apply our `apiUrl(...)` calls + the green/red border + `canSubmit` computed. |
| `kinotic-frontend/src/util/helpers.ts` | +7 lines | We added `apiUrl()` helper | Hand-merge — likely both add to the same file; their addition might be a `decodeJwt` helper or similar. |
| `kinotic-frontend/build.gradle` | +4 lines | We changed `webroot` copy target (kinotic-server → kinotic-api-gateway) | Hand-merge. |
| `kinotic-frontend/package.json` | +1 line (probably a dep) | We pulled in their merge already | Likely no conflict. |
| `kinotic-js/.../IIamUserService.ts` | +35 lines (regenerated from `IamUserService.java`) | We did not touch | Will need re-generation if we keep our IamUserService methods. |
| `kinotic-persistence/api/config/PersistenceProperties.java` | -21 lines (CORS removal) | We also removed CORS via the WebServerVerticle move | Probably aligns; verify they removed exactly what we did. |
| `kinotic-persistence/internal/endpoints/PersistenceVerticleFactory.java` | +4 -? lines | We removed `createWebServerNextVerticle` | Hand-merge. |
| `kinotic-persistence/internal/endpoints/WebServerVerticle.java` | -18 lines (modified) | **We deleted this file entirely** (moved to api-gateway) | **Hard conflict.** Git will report "modify/delete". Resolve by accepting our deletion — their edits to a now-moved file are moot. |
| `kinotic-persistence/internal/endpoints/graphql/GqlVerticle.java` | +4 lines | unchanged | Accept theirs. |
| `kinotic-persistence/internal/endpoints/openapi/OpenApiVertxRouterFactory.java` | +6 lines | unchanged | Accept theirs. |
| `kinotic-persistence/internal/utils/VertxWebUtil.java` | -23 lines (CORS removed) | unchanged | Accept theirs. |
| `kinotic-server/src/main/resources/application-development.yml` | +8 lines | We added `appBaseUrl: http://localhost:5173` + email block consolidation + duplicate-key fix | Hand-merge. |
| `kinotic-server/src/main/resources/application-kubernetes.yml` | +4 lines | unchanged | Accept theirs. |

### Recommended merge approach

1. **Commit our work first.** ~50 files staged + unstaged; consolidating into 3-4 logical
   commits would aid review but a single thorough commit is fine for the merge anchor.
2. **`git merge origin/develop`** on a clean tree.
3. **Resolve in this order** (each independently buildable):
   1. Take their `kinotic-core/.../CorsProperties.java` + `CorsUtil.java`. Delete our
      `kinotic-api-gateway/.../CorsProperties.java`. Update `KinoticApiGatewayProperties`
      to import core's `CorsProperties`. Update `ApiGatewayVertcleFactory.buildCorsHandler`
      to delegate to `CorsUtil.createCorsHandler`.
   2. `IamUserService` interface — adopt their `findFirstByEmailInScopeType` name,
      remove our `findByEmailAtScopeType`, keep our other additions (`findByEmailPrimary`,
      `findByOidcIdentity*`). Update callers.
   3. `WebServerVerticle.java` (persistence) — accept our deletion.
   4. Each frontend file — bring in their structural changes, re-apply our `apiUrl(...)`
      and the password-match UI tweaks.
   5. Property files — inspect each; accept theirs unless we deleted something they're
      now editing.
4. **Compile + helm lint** between major resolutions.
5. Single merge commit with a body that lists what we kept of each side.

### Estimated time: 60–90 minutes

Mostly the `VerifyEmail.vue` and `IamUserService` reconciliations. Frontend conflicts
look surface-area-large but are mostly mechanical.
