# Prompt: Organization (and Application) Members page — phased implementation

## Goal
Add a real **Members** page to `kinotic-frontend` (replacing the `/members` placeholder) that lists members of the signed-in admin's organization and lets them invite (by email), enable/disable, and remove members, plus manage pending invitations. A "member" is an `IamUser` at a scope within the admin's org (`organizationId` = admin's org; `applicationId == null` for org members, set for application members). Make the service/page **scope-parameterized by `applicationId`** so a future Application-members page reuses everything. Passwords never cross the Continuum RPC bus, so adding a member is a **tokenized email invite** (LOCAL password *or* the scope's OIDC provider), accepted over REST like signup/login.

## Overriding principle: one logic path, composed — do not duplicate
The signup/login stack already exposes scope-aware, reusable seams. The invite flow must **compose** them, not add parallel copies. Confirm each by reading it before use (per `CLAUDE.md` "Don't guess from names"), then reuse:

- `DefaultIamUserService.createUser(IamUser, password)` — already sets id/dates/`enabled`, auto-detects `LOCAL`/`OIDC` from password presence, enforces unique-email-per-scope, and creates the `IamCredential` only when a password is present. Use it for **all** member creation. `deleteById` already cascades the credential.
- `OidcFlowOrchestrator.startFlow/handleCallback` (gateway `…/rest/support/`) — config-agnostic state/nonce/PKCE + code exchange + issuer/audience validation. It already stashes `orgId` on the flow session and returns it in `CallbackResult`.
- `AuthEndpointSupport.completeOidcLogin(ctx, config, claims, userLookup)` — the shared post-callback path (validates `email_verified`, rejects disabled, establishes the session). Reuse it for invite-OIDC by passing a `userLookup` that asserts email-match and **creates** the member; reuse `respondSuccess`/`redirectCallbackFailure` for the LOCAL/error paths.
- `EmailService.sendVerificationEmail` body (domain) — ACS client + dual HTML/text Thymeleaf + `enabled=false` dev-logging. Extract a private `sendTemplatedEmail(...)` and make both verification and the new invite email thin callers.
- `PendingSignUpRepository.findValidByToken` — the find→delete-if-expired→throw logic. Extract it to a shared base so the new invite repo inherits it. *(Done in Phase 1 as `AbstractTokenVerificationRepository`.)*

## Design invariants settled in review (apply across all phases)
These were confirmed against the code and decided during review; later phases assume them.

1. **One org-scope user per email, globally.** Sign-up enforces this today via `findFirstOrgUserByEmail`, and the login stack *depends* on it: `POST /api/login/lookup` and password auth both resolve the user via the **unscoped** `findByEmail(email)` first-match, and social login via `findAllByOidcIdentity → pickFirst`. Two org-scope rows for one email would make login nondeterministic (whichever doc ES returns first wins; the other org is unreachable). Therefore **invites must reject any email that already has an org-scope user anywhere** — not just within the target scope. Multi-org-per-email (with a login-time org picker) is real product work and explicitly out of scope.
2. **Email normalization.** Nothing in the stack lowercases emails and ES term filters are exact-match. Invites are the first flow where two parties type the same address independently (admin at invite time; IdP `email` claim at accept time), so `Alice@X.com` vs `alice@x.com` would break the OIDC email-match assert or strand a LOCAL user. Centralize trim+lowercase in `DefaultIamUserService` at storage (`createUser`) and at the email lookups (`findByEmail*`/`findFirstOrgUserByEmail`), normalize at invite creation, and compare case-insensitively in the accept paths. **Caveat:** existing rows are not migrated; acceptable for the current pre-production stage.
3. **Member-management authorization == org participant, with no roles.** The roles system doesn't exist. Today each org has exactly one user (the signup admin), so "is an org participant" trivially equals "is the admin." The moment the first invite is accepted, **every** invited member gains full member-management power (invite/disable/remove anyone but themselves, cancel invites). This is the chosen interim semantic, not an oversight. The only structural guards are participant-derived org scoping, `ApplicationParticipant` rejection, and the self-lockout guard.
4. **Org scope comes from the bound participant, never the client.** Every `MemberService` call derives `organizationId` from `securityContext.requireParticipant(OrganizationParticipant.class)`. `applicationId` is the only scope value a caller supplies. The invite repo inherits the **unscoped** `deleteById`/`findById` from `AbstractRepository`, so `cancelInvite`/enable/disable/remove must **load-and-assert org match** before mutating — that load-then-check is the security boundary.
5. **Disable/remove gate at login only.** `completeOidcLogin`/`authenticateLocal` check `enabled` at login time, but an already-established browser session (Participant in the Vert.x session) and any issued refresh tokens keep working until they expire. Acknowledged; live-session revocation is out of scope for this run.

## Verified facts to rely on (don't re-derive incorrectly)
- `ApplicationParticipant` and `OrganizationParticipant` are **siblings** (both `extends Participant`), so `securityContext.requireParticipant(OrganizationParticipant.class)` correctly rejects an app participant with `AuthorizationException`. That is the org-only guard.
- `SecurityContext.requireParticipant` throws `AuthorizationException` when the bound participant isn't an instance of the requested type.
- `ApplicationService.getOidcConfigurations(appId)` already returns only **enabled** configs (a **list** — see the `oidcProviderName` rule in Phase 3); `OidcConfigurationService.findOrgLoginConfig(orgId)` returns the org's single SSO config or null. Use these to resolve a scope's OIDC config server-side.
- `OidcConfigurationRepository.findById(id, orgId)` is the org-scoped config lookup the gateway SSO callback already uses; reuse it for invite-accept.
- The org SSO callback `GET /api/login/callback/sso/:configId` resolves its config by `(configId, orgId-from-flow-session)`. Threading an `inviteToken` through the flow session lets the **existing** callback serve invite-accept too — no new IdP redirect URI per customer (see Phase 4).
- There is **no org-switcher** in the codebase; `findAllByOidcIdentity`'s "org switcher" Javadoc references a consumer that doesn't exist. Under invariant #1 the method returns ≤1 row, so it (and `pickFirst`) collapse to a single-result lookup (Phase 1b).
- `CrudTable` (frontend) takes `dataSource: IDataSource<DescriptiveIdentifiable>` (`DescriptiveIdentifiable` has an `[key:string]: any` index signature, so `IDataSource<IamUser>` is assignable), `headers: CrudHeader[]`, emits `@add-item` and `@update:search`, and exposes an `#additional-actions` per-row slot. A read-only data source (no `deleteById`) shows no built-in edit/delete column.
- The frontend uses **pnpm**; `kinotic-js/workspace` uses **bun**. Lombok everywhere in Java; `@Publish` on a plain interface auto-derives the CRI from package+name (e.g. `org.kinotic.os.api.services.iam.MemberService`).

## Build / verify commands
Backend (per repo `CLAUDE.md`): download JDK 25 if absent, then
```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
CLAUDE_CLOUD_COMPILE=true ./gradlew :<module>:compileJava -Porg.gradle.java.installations.paths=/tmp/jdk-25.x.y
```
os-api TS: `cd kinotic-js/workspace && bun install && bun run build && bun run --filter '@kinotic-ai/os-api' type-check`.
Frontend: `pnpm install && npx vue-tsc -b && npx vite build`.

## Constraints for this run
- **Each phase changes at most 10 files** (counting new, edited, deleted, and generated files such as `pnpm-lock.yaml` / `components.d.ts`).
- Finish a phase by **compiling/type-checking the affected module(s) and committing** with a descriptive message before starting the next. Don't open a PR unless asked.
- Phases are ordered by dependency; later phases assume earlier ones are present.

---

### Phase 1 — Domain refactor: shared verification base + scoped user queries + signup unification ✅ DONE (PR #225)
Pure refactor + additive queries; no new feature, signup behavior unchanged. Implemented and pushed:
1. `kinotic-domain/api/model/iam/PendingVerification.java` (new) — `extends Identifiable<String>` with `getVerificationToken()`, `getExpiresAt()`.
2. `internal/api/repositories/AbstractTokenVerificationRepository.java` (new) — `extends AbstractRepository<T extends PendingVerification>`; provides `findByVerificationToken` + `findValidByToken` (find → delete-if-expired+throw → return), moved from `PendingSignUpRepository`.
3. `api/model/iam/PendingSignUp.java` (edit) — `implements PendingVerification`.
4. `internal/api/repositories/PendingSignUpRepository.java` (edit) — extends the new base; keeps only `findByEmail`.
5. `api/services/iam/IamUserService.java` (edit) — added `findByScope`/`searchByScope`.
6. `internal/api/services/iam/DefaultIamUserService.java` (edit) — implemented them.
7. `internal/api/repositories/IamUserRepository.java` (edit) — added `searchByScope` (bool `must` query_string over `email`/`displayName` + `filter` scopeFilter; blank text → `findByScope`).
8. `internal/api/services/iam/DefaultSignUpService.java` (edit) — routes admin creation through `IamUserService.createUser`; dropped hand-rolled credential hashing and the now-unused repo injections.

### Phase 1b — Domain invariant hardening: single-org OIDC lookup + email normalization (≤5 files)
Pure refactor/hardening that makes invariants #1 and #2 explicit in the domain before the invite feature is built on them. No new feature.
1. `api/services/iam/IamUserService.java` (edit) — replace `findAllByOidcIdentity(sub, configId): List<IamUser>` with `findByOidcIdentity(sub, configId): IamUser` (unscoped single match). Update its Javadoc to drop the non-existent "org switcher" rationale and state the one-org-per-identity invariant.
2. `internal/api/services/iam/DefaultIamUserService.java` (edit) — implement the single lookup; **normalize email** (trim + `toLowerCase(Locale.ROOT)`) in `createUser` and in every email lookup (`findByEmail(email)`, `findByEmail(email, org, app)`, `findFirstOrgUserByEmail`). Add one private `normalizeEmail(...)` helper.
3. `internal/api/repositories/IamUserRepository.java` (edit) — change `findAllByOidcIdentity` to a `findFirst`-based single-result `findByOidcIdentity(sub, configId)`.
4. `…/gateway/internal/endpoints/rest/OrganizationLoginHandler.java` (edit) — `completeSocialLogin` uses the single lookup directly; delete `pickFirst`.
5. `…/gateway/internal/endpoints/rest/OrganizationSignupHandler.java` (edit) — `createPendingSignUp` uses the single lookup and checks `!= null` instead of `!isEmpty()`.
Verify: `:kinotic-domain:compileJava` and `:kinotic-api-gateway:compileJava`. Commit. Note in the commit that existing mixed-case email rows are not migrated.

### Phase 2 — Domain invite lifecycle (≤9 files)
Additive feature on top of Phase 1/1b.
1. `api/model/iam/PendingInvite.java` (new) — Lombok; `implements PendingVerification`; fields `id, verificationToken, expiresAt, created, email, displayName, authType, organizationId, applicationId, oidcConfigId, invitedBy`.
2. `internal/api/repositories/PendingInviteRepository.java` (new) — `extends AbstractTokenVerificationRepository<PendingInvite>`, index `kinotic_iam_pending_invite`; add `findByEmailAndScope`, `findByScope`.
3. `api/services/iam/InviteService.java` (new) — `createInvite`, `getValidInvite`, `acceptLocalInvite`, `acceptOidcInvite`, `findPendingInvites`, `cancelInvite(inviteId, orgId)`.
4. `internal/api/services/iam/DefaultInviteService.java` (new) — composes `IamUserService.createUser`/`findByEmail`/`findFirstOrgUserByEmail`, `PendingInviteRepository`, `EmailService`, `OrganizationService`. Rules:
   - **Invite TTL = 7 days** (invites sit in inboxes, unlike the 24h-LOCAL / 10min-OIDC sign-up tokens). Define as a named constant.
   - `createInvite` normalizes the email, then rejects it if (a) it already has an org-scope user anywhere (`findFirstOrgUserByEmail`, invariant #1 — message e.g. *"This email already belongs to an organization."*), or (b) a pending invite already exists in this scope (`findByEmailAndScope`).
   - Both accept paths funnel through one private `acceptInvite(...)` that calls `createUser` then deletes the invite. The OIDC path additionally asserts the invite's `oidcConfigId` matches the callback's config and the IdP `email` claim equals the invited email (case-insensitive).
   - `cancelInvite(inviteId, orgId)` **loads the invite, asserts its `organizationId` equals `orgId`, then deletes** (the repo's `deleteById` is unscoped — invariant #4).
5. `internal/api/services/EmailService.java` (edit) — extract private `sendTemplatedEmail(...)`; `sendVerificationEmail` becomes a thin caller (unchanged behavior); add `sendInviteEmail(email, displayName, token, orgName)`.
6. `resources/templates/email/invite-email.html` (new), 7. `…/invite-email.txt` (new) — mirror the verification templates.
8. `src/test/.../DefaultInviteServiceTest.java` (new, recommended) — cover the uniqueness rejections, expiry, the OIDC email-match assert, and both accept paths. If skipped due to the file budget, verify manually via the `kinotic.email.enabled=false` dev-logging path (the invite link is logged instead of sent, so the full accept flow is exercisable locally without ACS) and say so in the commit.
Verify: `:kinotic-domain:compileJava` (+ tests if added). Commit.

### Phase 3 — os-api published facade (3 files)
1. `kinotic-os-api/api/services/iam/InviteOptions.java` (new) — `{ localEnabled, oidcEnabled, oidcProviderName }`. Semantics: `localEnabled` is true whenever LOCAL invites are permitted for the scope (today: always — there's no setting that disables it; document it as the seam for a future toggle). `oidcEnabled`/`oidcProviderName` come from `resolveScopeOidcConfig`: for **org** scope, `findOrgLoginConfig(orgId)` (single config → its name, or `oidcEnabled=false` if null); for **application** scope, `getOidcConfigurations(appId)` returns a **list**, so the rule is *exactly one enabled config → enable OIDC with that name; zero or more-than-one → `oidcEnabled=false`* (ambiguous multi-config app invites are not offered until the page lets the admin pick).
2. `api/services/iam/MemberService.java` (new) — `@Publish`; `findMembers`/`searchMembers`/`inviteOptions`/`inviteMember`/`setMemberEnabled`/`removeMember`/`findPendingInvites`/`cancelInvite`, each taking `applicationId` where scoped.
3. `internal/api/services/iam/DefaultMemberService.java` (new) — inject `SecurityContext`, domain `IamUserService` + `InviteService`, `OidcConfigurationService`, `ApplicationService`. Private helpers: `requireOrganizationId()` (rejects `ApplicationParticipant` via `requireParticipant(OrganizationParticipant.class)`), one `resolveScopeOidcConfig(applicationId)` (used by `inviteOptions` + `inviteMember`), one `loadOwnedMember(id)` (org-match assert + self-lockout guard — an admin can't disable/remove their own account — used by enable/disable + remove). Org id always comes from the participant; `cancelInvite`/`removeMember`/`setMemberEnabled` pass that org to the domain layer so the load-and-assert boundary (invariant #4) holds.
Verify: `:kinotic-os-api:compileJava`. Commit.

### Phase 4 — Gateway invite-accept REST, reusing the SSO callback (≤6 files)
Invite-OIDC reuses the **existing** `/api/login/callback/sso/:configId` rather than adding a new callback route. Org SSO configs point at customer-managed IdPs; a new `…/api/invite/callback/<id>` redirect URI would have to be registered in every customer's IdP app or invites fail with `redirect_uri_mismatch`. Threading `inviteToken` through the flow session avoids that and keeps to the "one logic path, composed" principle.
1. `…/rest/support/OidcFlowSession.java` (edit) — add nullable `inviteToken` (+ serialize) alongside `orgId`.
2. `…/support/CallbackResult.java` (edit) — add `inviteToken`.
3. `…/support/OidcFlowOrchestrator.java` (edit) — `startFlow(…, orgId, inviteToken)` overload (existing 4-arg delegates with null); copy `inviteToken` into `CallbackResult`.
4. `…/rest/InviteHandler.java` (new) — owns three routes:
   - `GET /api/invite?token` → invite details (email, displayName, authType, orgName) for the accept page.
   - `POST /api/invite/accept` (LOCAL) → `inviteService.acceptLocalInvite(token, password)` then `respondSuccess`.
   - `POST /api/invite/start` (OIDC) → resolve the invite's org SSO config, `startFlow(ctx, config, ssoCallbackUrl(configId), orgId, inviteToken)` so the IdP returns to the existing SSO callback; respond `{redirect}`.
5. `…/rest/OrganizationLoginHandler.java` (edit) — in `completeSsoLogin`, when `result.inviteToken() != null`, run a create-on-accept lookup (`sub -> inviteService.acceptOidcInvite(result.inviteToken(), sub, emailClaim, result.config().getId(), result.orgId())`) through the same `completeOidcLogin` path instead of the plain login lookup. Inject `InviteService`.
6. `…/endpoints/ApiGatewayVertcleFactory.java` (edit) — inject + `mountRoutes` `InviteHandler` next to `organizationSignupHandler`.
Verify: `:kinotic-api-gateway:compileJava`. Commit.

### Phase 5 — os-api TypeScript client (≤7 files)
1. `src/api/model/iam/PendingInvite.ts` (new), 2. `src/api/model/iam/InviteOptions.ts` (new), 3. `src/api/services/IMemberService.ts` (new — non-CRUD proxy like `DeviceApprovalService`; `memberDataSource(applicationId)` returns `IDataSource<IamUser>` whose `findAll`/`search` call `findMembers`/`searchMembers`; `import type` for the `InviteOptions` interface to satisfy `verbatimModuleSyntax`).
4. `src/api/OsApiPlugin.ts` (edit — add `members`, remove `iamUsers`), 5. `src/index.ts` (edit — export new, drop `IIamUserService`), 6. `src/api/services/IIamUserService.ts` (delete — it's already dead: it targets a domain service that is no longer `@Publish`ed, confirmed by its own JSDoc, and after Phase 1b its `findAllByOidcIdentity` shape is stale anyway), 7. `package.json` (bump `1.8.0`→`1.9.0`).
**Semver note:** removing a public export is strictly major, but the removed export is non-functional, so `1.9.0` is defensible. The out-of-workspace consumers `kinotic-js/load-generator` (registry `^1.4.0`) and `kinotic-js/e2e-tests` (registry `^1.7.0`) call `Kinotic.iamUsers.findByEmailAndScope/createUser`; they're unaffected by the workspace `1.9.0` today but will need migration on their next os-api bump (and may already be non-functional against the current backend). Flag this in the commit.
Verify: `bun run build` + `type-check`. Commit.

### Phase 6 — Frontend (≤6 files)
1. `src/pages/Members.vue` (new — optional `applicationId: string|null = null` prop; `CrudTable` bound to `Kinotic.members.memberDataSource(applicationId)`; columns Name/Email/Auth type/Status/Joined; `@add-item` opens an invite `Dialog` driven by `inviteOptions`; `#additional-actions` enable-disable + remove behind a confirm — the current admin's own row hides those actions, matching the server-side self-lockout guard; a pending-invites section via `findPendingInvites`/`cancelInvite`).
2. `src/pages/signup/InviteAccept.vue` (new — unauthenticated, modeled on `VerifyEmail.vue`: `GET /api/invite?token`; LOCAL→password form `POST /api/invite/accept`; OIDC→button `POST /api/invite/start` then `window.location = redirect`).
3. `src/pages/routes.ts` (edit — `/members` renders `Members.vue` keeping `sidebarItems: organizationSidebarItems`; add public `/invite/accept`).
4. `package.json`, 5. `pnpm-lock.yaml`, 6. `components.d.ts` (from install/build).
**Known gotcha:** linking only os-api creates a dual `@kinotic-ai/core` instance (workspace vs registry) that breaks the `KinoticSingleton` augmentation and the runtime singleton. Unify on the in-repo core — link `@kinotic-ai/core`, `persistence`, `idl`, and `os-api` to `link:../kinotic-js/workspace/packages/*` (they're version-compatible) — so this counts as edits to the same `package.json`. Verify `vue-tsc -b` and `vite build`. Note in the commit that these links revert to caret ranges once os-api 1.9.0 / core 1.6.2 are published.

## Out of scope
- **Roles / RBAC** — the roles system doesn't exist; authorization is participant-derived org scoping + `ApplicationParticipant` rejection + the self-lockout guard. Consequence (chosen, not accidental — invariant #3): every accepted member has full member-management power.
- **Multi-org-per-email** — invariant #1 forbids it; a login-time org picker is future work.
- **Live-session / refresh-token revocation on disable/remove** — gating is at login only (invariant #5).
- **`resendInvite`.**
- **The Application-members page itself** — everything is scope-parameterized for it, but the page is future work.
