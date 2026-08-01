# Trusted Device Verification — Plan-Building Prompt

Build a phased implementation plan (do not implement until the plan is approved) for
gating the email-first login lookup behind Keeper-style trusted-device verification.
Read the repository `CLAUDE.md` first — every convention in it binds. File paths and
line numbers below were accurate when this prompt was written; re-verify before
planning against them — when this prompt and the code disagree, the code wins.

## The problem

`POST /api/auth/org/login/lookup` is an unauthenticated account-existence oracle for
the highest-value targets. The password branch is already deliberately ambiguous
(unknown email, local user, and dead-SSO user all get `{type:"password"}`), but the
SSO branch is not — `OrganizationLoginHandler.resolveSsoOrPassword` (~`:165`) answers
`{type:"sso", redirect:<IdP authorize URL>}`, confirming the account exists **and**
naming the org's IdP. `ApplicationLoginHandler` mounts the same shape at app scope.
Both must be covered.

Current state: the SPA does not call lookup at all — `LoginPage.vue` posts email +
password straight to `POST /api/auth/org/login` (generic 401) and renders social
buttons per enabled provider. Lookup stays mounted only for the future email-first
SSO-discovery UX. This feature is the prerequisite for routing that UX back into the UI.

## The goal

Only the owner of an email address can reach the "let me use this email to log in"
point. Entering an email on an unrecognized browser must not reveal anything and must
not route anywhere until the email's owner approves that browser.

## Agreed design (starting point for the plan, not up for re-litigation)

- **TrustedDevice** entity (Elasticsearch, new table in `V1__init.sql` — snapshot rule:
  edit migrations in place): stores a *hash* of a 256-bit device token; the raw token
  lives in a long-lived `HttpOnly`/`Secure`/`SameSite=Lax` cookie. Tracks the approved
  `(device, email)` pairs plus created/lastUsed.
- **PendingDeviceApproval** entity: single-use emailed token, the requesting deviceId,
  the email, ~10-minute expiry — model on `PendingSignUp` / `DefaultSignUpService`.
- **Lookup behavior change**: when the calling device is not approved for the submitted
  email, respond with an *identical* `{type:"device_verification"}` body whether or not
  the email has an account — the response stops being an oracle entirely. Side effect:
  an email is sent — "approve this browser" (with account) or "no account here — want
  to sign up?" (without). Once the device is approved for that email, lookup behaves
  exactly as today.
- **Approval binds to the requesting device**, taken from the pending record — people
  open email on their phone; the approving browser is often not the one waiting.
- **SPA surface**: an approval page for the emailed link, and a "check your email /
  I've approved it" waiting state in the login flow.
- **Rate limiting** per email and per device — without it, lookup becomes an
  email-bombing service.

## Implementation subtleties the plan must address

- The emailed approval must complete via an explicit POST behind a button click, never
  a bare GET link — corporate mail scanners prefetch GET links and would silently
  approve attacker-initiated requests.
- Store only token hashes server-side (both device tokens and pending-approval tokens).
- The identical-response requirement includes timing: sending vs. not sending an email
  must not be distinguishable from the HTTP response.

## Open questions the plan should answer (or put to the owner)

- Device-cookie lifetime and rotation; one email per device row vs. a set.
- Should a successful social/password login also mark the device approved for that
  email, so the user isn't re-challenged later by the email-first flow?
- Device management UI (list/revoke trusted devices) — in scope now or later?
- Unknown-email case: send the "sign up?" email or send nothing (silent drop)?

## Codebase pointers

- `kinotic-domain/.../internal/api/rest/OrganizationLoginHandler.java` — `handleLookup`
  (~`:61`), `resolveSsoOrPassword` (~`:165`)
- `kinotic-domain/.../internal/api/rest/ApplicationLoginHandler.java` — app-scope lookup
- `kinotic-domain/.../internal/api/rest/support/AuthEndpointSupport.java` — shared
  response helpers
- `PendingSignUp` + `DefaultSignUpService` — the pending-token pattern to mirror
- `EmailService` (kinotic-domain) — verification-email sending, including the
  log-instead-of-send dev mode
- `kinotic-migration/.../migrations/V1__init.sql` / `V2__kinotic_data_inserts.sql`
- `kinotic-frontend/src/pages/login/LoginPage.vue` — where the email-first UX will
  re-enter the UI

## Deliverable

A phase-by-phase plan (~10 files per phase, each phase compiling and independently
reviewable) with a **STOP for explicit approval after each phase**, naming exact files
to create/modify, the entity/endpoint/wire shapes, and per-phase verification steps
(build commands for the cloud environment are in `CLAUDE.md`).
