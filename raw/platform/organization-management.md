# Organization Management

> How organizations are created, how their users authenticate, and how OIDC providers are wired up.

## Overview

A Kinotic deployment hosts many customer organizations. Each org has its own users, applications, and (optionally) its own enterprise SSO configuration. This page describes how an org is created, who can log in to it, and how the OIDC plumbing is shared across orgs without leaking access between them.

System-level platform operators (the people who run kinotic-server itself) have no login path today; it is planned to move to Microsoft Entra, separate from everything described here.

## Mental Model

Four persistent entities carry the auth state, and one short-lived entity bridges each signup:

<table>
<thead>
  <tr>
    <th>
      Entity
    </th>
    
    <th>
      Purpose
    </th>
    
    <th>
      Lifecycle
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        OrgSignupOidcConfiguration
      </code>
    </td>
    
    <td>
      A Kinotic-curated social provider (GitHub, Google, Microsoft) shown as a login/signup button to everyone. Belongs to no organization
    </td>
    
    <td>
      Seeded by SQL migration
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        Organization
      </code>
    </td>
    
    <td>
      A customer org. <code>
        ssoConfigId
      </code>
      
       names its single enterprise SSO provider, or null
    </td>
    
    <td>
      Created at the end of signup
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        OidcConfiguration
      </code>
    </td>
    
    <td>
      A provider record (clientId, authority, …) owned by one organization
    </td>
    
    <td>
      Created by org admins
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ParticipantIdentity
      </code>
    </td>
    
    <td>
      A scoped identity carried structurally by <code>
        organizationId
      </code>
      
       / <code>
        applicationId
      </code>
      
      . One row per (person, scope)
    </td>
    
    <td>
      Created during signup, by invitation, or by an admin
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        PendingSignUp
      </code>
    </td>
    
    <td>
      Holds a signup in flight — the email-verification state for a local signup, or the verified identity for a social one, discriminated by <code>
        authType
      </code>
    </td>
    
    <td>
      Short-lived; deleted once <code>
        /api/auth/org/signup/complete
      </code>
      
       or <code>
        /api/auth/org/signup/social/complete
      </code>
      
       succeeds
    </td>
  </tr>
</tbody>
</table>

Configurations are referenced, never embedded, so one row can serve more than one purpose: an org's `ssoConfigId` and one of its applications' `oidcConfigurationIds` may name the same `OidcConfiguration` when the same Okta tenant serves both.

### Two distinct OIDC roles

<table>
<thead>
  <tr>
    <th>
      Role
    </th>
    
    <th>
      Entity
    </th>
    
    <th>
      Referenced from
    </th>
    
    <th>
      What the user sees
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <strong>
        Platform OIDC
      </strong>
      
       (social)
    </td>
    
    <td>
      <code>
        OrgSignupOidcConfiguration
      </code>
    </td>
    
    <td>
      Nothing — every enabled row is offered
    </td>
    
    <td>
      A "Continue with GitHub/Google/…" button on the login <em>
        and
      </em>
      
       signup pages
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Per-org SSO
      </strong>
      
       (enterprise)
    </td>
    
    <td>
      <code>
        OidcConfiguration
      </code>
    </td>
    
    <td>
      <code>
        Organization.ssoConfigId
      </code>
    </td>
    
    <td>
      No visible button — reached via the email-first lookup flow when their org has SSO configured
    </td>
  </tr>
</tbody>
</table>

The roles are separate *types* in separate indices rather than one type with a flag, because the difference between them is scope. An org-scoped query cannot return a platform provider and a platform lookup cannot reach another org's SSO, so an org admin configuring SSO can never make it appear as a global button, and a seeded social provider can never affect an org's SSO settings.

## Org Creation

There are two entry points, both producing an `Organization` and an admin `ParticipantIdentity` scoped to it. The SPA's `/signup` page currently offers **GitHub only** (a hardcoded button — every Kinotic project is backed by a GitHub repository, so the founding user signs up with GitHub and installs the GitHub App right after). The email/password signup entry point remains fully mounted on the backend; its previous UI (`Signup.vue`) is kept in the tree unrouted until other methods return. Login is not restricted: invitees must be able to sign back in however they joined, so the login page keeps every enabled provider plus a password form.

### Email/password signup (backend flow — not currently reachable from the SPA)

```text
1. User loads /signup, enters email + displayName
2. POST /api/auth/org/signup
3. SignUpService.initiateLocalSignUp:
   - rejects if a sign-up is already pending for this email, or
     if an ParticipantIdentity already exists at ORGANIZATION scope for this email
   - creates a PendingSignUp with a 24h verification token
   - EmailService sends the verification link (logs it instead when email is disabled)
4. User clicks /signup/verify?token=<verificationToken> in their inbox
5. /signup/verify form (VerifyEmail.vue) prompts for orgName + password + confirm
6. POST /api/auth/org/signup/complete  { token, orgName, orgDescription?, password }
7. SignUpService.completeLocalSignUp:
   - validates token, rejects if expired
   - creates Organization (auto-derived id from name)
   - creates ParticipantIdentity (authType=LOCAL, organizationId=org.id, applicationId=null, enabled=true)
   - links Organization.createdBy = user.id
   - creates IdentityCredential (bcrypt hash, separate index keyed by user.id)
   - deletes the PendingSignUp
8. The gateway establishes the browser session (204 + Set-Cookie); the frontend then calls
   userState.login() to open the realtime connection, authenticated by that session cookie.
```

Email verification is the security gate — no `Organization` or `ParticipantIdentity` exists until the link is clicked. With `KINOTIC_EMAIL_ENABLED=false` (the local default) the verification URL is logged to the kinotic-server console instead of sent; copy it into the browser to finish the flow.

### Social-IdP signup

```text
1. User loads /signup, clicks "Sign up with GitHub" (the page's only button today)
2. POST /api/auth/org/signup/social/start/github
3. OrganizationSignupHandler.handleSocialStart:
   - picks the platform OidcConfiguration whose provider key matches
     (orgSignupOidcConfigurationService.findEnabledByProvider)
   - generates state/nonce/PKCE, stashes them on the session cookie
   - 302 to <authority>/authorize?...
4. User authenticates at the IdP
5. IdP returns to GET /api/auth/org/signup/social/callback/<configId>
6. OrganizationSignupHandler.handleSocialCallback → createPendingSignUp:
   - validates state/nonce/PKCE, exchanges code for id_token + access_token
   - rejects if email_verified=false in the id_token
   - rejects with AccountExistsException if an ParticipantIdentity already exists for (sub, configId)
   - creates a PendingSignUp with the verified subject, configId, email, displayName
   - 302 to /register?token=<verificationToken>
7. /register prompts for orgName (CompleteOrg.vue)
8. POST /api/auth/org/signup/social/complete  { token, orgName, orgDescription? }
9. SignUpService.completeOidcWithNewOrg:
   - validates the pending token
   - creates Organization
   - creates ParticipantIdentity (authType=OIDC, organizationId=org.id, applicationId=null,
                      oidcSubject + oidcConfigId set, enabled=true)
   - links Organization.createdBy
   - deletes the PendingSignUp
10. The gateway establishes the browser session (204 + Set-Cookie). CompleteOrg.vue then
    calls userState.login() to open the realtime connection, authenticated by that session
    cookie. No token travels in the URL.
11. CompleteOrg.vue shows a "Connect GitHub" step explaining that the next GitHub
    round-trip authorizes repository access (distinct from the sign-in, which only
    proved identity). Clicking "Continue to GitHub" calls
    githubAppInstallations.startInstall("/applications") and redirects the whole tab to
    the returned GitHub install URL. The App requests user authorization (OAuth) during
    installation, so GitHub returns to /github/install/callback — the App's first
    registered callback URL — with code, installation_id, and the state minted by
    startInstall. The callback runs completeInstall, which consumes the state, exchanges
    the code with the github-platform credential for the authorizing user's access
    token, and binds the installation only when GitHub's /user/installations reports
    that user can access it (see [Defense in Depth](/platform/defense-in-depth)). It
    then lands on /applications — project creation requires the install, so the new org
    arrives ready. If the install can't start (e.g. a kinotic.disableGithub deployment)
    the page falls back to /applications directly.
```

The `PendingSignUp` is consumed once. The `?token=` in the redirect to `/register` is the short-lived `PendingSignUp` verification token, not an auth credential — the actual login is established by the session cookie set when the org-naming POST succeeds.

GitHub follows the same flow with one difference in step 6: GitHub is OAuth 2.0 only and issues no id_token, so its configuration row sets the endpoints explicitly instead of relying on discovery — `authorizationUri`/`tokenUri` drive the code flow, `userInfoUri` supplies `sub` and the display name, and `userEmailsUri` supplies the email plus its `verified` flag (primary address preferred). Any provider whose row sets these fields takes this path; nothing about it is GitHub-specific in code. Issuer, audience, and nonce validation don't apply (there is no token to carry them); the state match and the direct TLS code exchange are the security boundary.

## User Login

Once an org exists, members log in through one of three converging paths. The SPA's login page (`LoginPage.vue`) renders one button per enabled platform provider plus a single-step email + password form that posts straight to `POST /api/auth/org/login` — it does not call the email-first lookup below, which stays mounted for the future SSO-discovery UX.

### Email-first lookup → password or SSO (backend flow — not currently reachable from the SPA)

The unrouted `Login.vue` shows a single email field plus the platform OIDC buttons. Typing an email and submitting drives this:

```text
1. POST /api/auth/org/login/lookup { email }
2. OrganizationLoginHandler.handleLookup → resolveSsoOrPassword:
   - finds the user's ParticipantIdentity at ORGANIZATION scope (iamUserService.findByEmail)
   - if user.authType=OIDC AND the org's ssoConfigId names an enabled OidcConfiguration:
       generate state/nonce/PKCE, stash on session, return
       { "type": "sso", "redirect": "<authority>/authorize?..." }
       (frontend follows the redirect via window.location)
   - otherwise:
       return { "type": "password" }
       (frontend reveals the password field)
3. The "password" branch is deliberately ambiguous — it covers unknown email,
   a local user, and a user whose SSO config has been deleted. This avoids
   leaking which orgs use SSO via timing/responses.
```

A user may hold multiple `ParticipantIdentity` rows (multi-org membership keyed by `(oidcSubject, oidcConfigId)`). The org switcher (post-login) is where they hop between them.

#### Completing the password branch

When `lookup` returns `{type: "password"}`, the frontend reveals the password field and posts email + password to the gateway, which verifies the credential and **establishes a browser session** — there is no client-held token. The `fetch` uses `credentials: 'include'` so the gateway's `Set-Cookie` is stored even cross-origin:

```text
1. POST /api/auth/org/login { email, password }   (credentials: 'include')
2. OrganizationLoginHandler.handleLogin → AuthEndpointSupport.handlePasswordLogin:
   - LocalAuthenticationService.authenticateLocal(email, password)
       finds the ParticipantIdentity, requires authType=LOCAL + enabled,
       loads IdentityCredential, verifies the bcrypt hash
   - on success: establishSession(ctx, user) puts the user's Participant on the
                 Vert.x session (regenerating the session id), then 204 + Set-Cookie
   - on any failure: 401 "Invalid credentials"
                     (deliberately generic — covers unknown email, wrong password,
                      OIDC user, disabled user)
3. Login.vue calls userState.login() → Kinotic.connect().
   The default resolution supplies no auth headers in a browser, so the
   WebSocket upgrade is authenticated by the session cookie set in step 2.
```

The SPA never exchanges the password for a token and never sends raw passwords over the WebSocket. Non-UI clients (CLI, automation) do not use this path — they authenticate directly at the upgrade instead; the table in "The WebSocket upgrade" below covers what each client type presents.

### Social button

The buttons are populated from `GET /api/auth/org/login/providers`, which lists the unique provider keys present in the platform social configs (the invite-hint UI on `MembersPage` reads the same endpoint). Clicking a button:

```text
1. POST /api/auth/org/login/social/start/github?referer=<spa-path>
   - the router guard bounced an unauthenticated navigation to /login?referer=<spa-path>,
     and the login page forwards it here; a social login leaves the SPA, so the path
     cannot be kept client side the way the password path keeps it
2. OrganizationLoginHandler.handleSocialStart:
   - finds the platform OidcConfiguration with provider="github"
     (orgSignupOidcConfigurationService.findEnabledByProvider)
   - stores referer on the session when it names a path within the SPA
   - same state/nonce/PKCE setup as signup, then 302 to the IdP
3. IdP returns to GET /api/auth/org/login/social/callback/<configId>
4. OrganizationLoginHandler.handleSocialCallback → AuthEndpointSupport.completeOidcLogin:
   - validates state/nonce, exchanges code, validates id_token (sub + email_verified)
   - looks up an ParticipantIdentity by (oidcSubject, oidcConfigId)
   - if none exists: 302 /login?error=no_account so the frontend can show
                     a "no account, sign up?" CTA (signup is a separate flow)
   - if one exists (matched by (oidcSubject, oidcConfigId) at org scope):
       establishSession(ctx, user), then redirectSuccess → 302 to the stored
       referer, or the SPA root when the flow stored none, with Set-Cookie.
       No token travels in the URL.
5. The SPA loads with the session cookie set; userState.login() opens the realtime
   connection, authenticated by that cookie.
```

The email-first SSO branch (`type: "sso"`) returns to `GET /api/auth/org/login/sso/callback/:configId` instead, but finishes the same way — `establishSession` + `redirectSuccess`.

### Platform operators (SYSTEM scope)

Platform operators are not organization members — their `IamUser` has neither `organizationId` nor `applicationId`. They sign in through a separate, password-only route used by the system console (there is no signup, SSO, or social path for SYSTEM scope):

```text
1. POST /api/auth/system/login { email, password }   (credentials: 'include')
2. SystemLoginHandler.handleLogin → AuthEndpointSupport.handlePasswordLogin:
   - LocalAuthenticationService.authenticateLocal(email, password, null, null)
       scoped to SYSTEM, so an ORGANIZATION- or APPLICATION-scope user with the
       same email can never authenticate here
   - success/failure behave exactly like the org password branch:
     204 + Set-Cookie, or a generic 401
```

The session established here is the same browser-session mechanism as every other login path; `/api/auth/me` and `/api/auth/logout` apply unchanged.

### The WebSocket upgrade (the final step in every path)

Authentication happens at the WebSocket upgrade (handshake), not in a STOMP CONNECT frame. How the upgrade is authenticated depends on the client:

<table>
<thead>
  <tr>
    <th>
      Client
    </th>
    
    <th>
      Upgrade credentials
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Browser SPA
    </td>
    
    <td>
      Session cookie established by the REST login; the default credential resolution supplies no headers
    </td>
  </tr>
  
  <tr>
    <td>
      Node client, credentials
    </td>
    
    <td>
      <code>
        clientId
      </code>
      
      , <code>
        clientSecret
      </code>
      
      , plus <code>
        organizationId
      </code>
      
       / <code>
        applicationId
      </code>
      
       scope headers (via <code>
        BasicCredentialsResolver
      </code>
      
       or the <code>
        KINOTIC_CLIENT_*
      </code>
      
       environment variables)
    </td>
  </tr>
  
  <tr>
    <td>
      Node client, Bearer token
    </td>
    
    <td>
      <code>
        Authorization: Bearer <jwt>
      </code>
      
       (via <code>
        BearerCredentialsResolver
      </code>
      
      ); a Kinotic JWT carries the <code>
        organizationId
      </code>
      
       / <code>
        applicationId
      </code>
      
       claims
    </td>
  </tr>
</tbody>
</table>

The browser SPA never holds a JWT — its login establishes a session cookie and that cookie authenticates the upgrade. The Bearer path is for non-browser clients: the CLI obtains an access token through the OAuth device-code grant at `POST /api/auth/oauth/token`, which mints a Kinotic JWT carrying `sub` / `email` / `organizationId` / `applicationId`. For that path the kinotic-server validates the JWT signature against its signing keys and creates the `Session`. The CLI persists a rotating refresh token to mint fresh access tokens before each connect.

## Provider-Specific Quirks

OIDC is a standard, but providers diverge on a few details. The validation helpers in `OAuth2Util` (`isIssuerValid`, `isEmailVerified`) handle these declaratively — the provider key on `OidcConfiguration` selects the right behaviour. No provider needs handler-level branching, and no provider's endpoints live in code: a row either names an `authority` for OIDC discovery or sets `authorizationUri`/`tokenUri`/`userInfoUri`/`userEmailsUri`/`scopes` explicitly, and every handler consumes the same claims shape either way.

<table>
<thead>
  <tr>
    <th>
      Provider key
    </th>
    
    <th>
      <code>
        iss
      </code>
      
       shape
    </th>
    
    <th>
      <code>
        email_verified
      </code>
      
       claim
    </th>
    
    <th>
      Other notes
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        github
      </code>
    </td>
    
    <td>
      No id_token — identity is fetched from the row's <code>
        userInfoUri
      </code>
      
       with the exchanged access token, so issuer/nonce validation doesn't apply
    </td>
    
    <td>
      Synthesized from the row's <code>
        userEmailsUri
      </code>
      
       — the primary verified address is preferred, any verified address accepted
    </td>
    
    <td>
      OAuth 2.0 only, so the row sets every endpoint explicitly. <code>
        sub
      </code>
      
       is the numeric GitHub account id. Uses the kinotic-ai GitHub App's user-authorization OAuth credential — the same credential that verifies installation ownership when an org links GitHub, so the row must never point at a different OAuth client. The app needs the "Email addresses: read-only" permission, and its scope param is ignored (GitHub App permissions govern access)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        google
      </code>
    </td>
    
    <td>
      Fixed <code>
        https://accounts.google.com
      </code>
    </td>
    
    <td>
      Emitted as boolean — required <code>
        true
      </code>
      
       to accept
    </td>
    
    <td>
      <code>
        sub
      </code>
      
       is per-OAuth-client pairwise (different Kinotic deployments see different <code>
        sub
      </code>
      
      s for the same person — fine since we key on <code>
        (sub, configId)
      </code>
      
      )
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        azure-ad
      </code>
      
       (single tenant)
    </td>
    
    <td>
      Fixed <code>
        https://login.microsoftonline.com/<tenant-id>/v2.0
      </code>
    </td>
    
    <td>
      Not emitted — email-presence is treated as verified (Entra verifies via tenant domain ownership)
    </td>
    
    <td>
      Used by per-org SSO configs that pin to a specific Entra tenant
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        azure-ad
      </code>
      
       (multi-tenant <code>
        /common
      </code>
      
       or <code>
        /organizations
      </code>
      
      )
    </td>
    
    <td>
      Per-user — substitutes user's home tenant id; we re-validate by extracting <code>
        tid
      </code>
      
       from the same signed JWT
    </td>
    
    <td>
      Same as single-tenant — not emitted, presence trusted
    </td>
    
    <td>
      Discovery doc returns a literal <code>
        {tenantid}
      </code>
      
       placeholder; we set <code>
        validateIssuer=false
      </code>
      
       and clear <code>
        jwtOptions.issuer
      </code>
      
       for this case so Vert.x's strict comparison doesn't reject
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        apple
      </code>
    </td>
    
    <td>
      Fixed <code>
        https://appleid.apple.com
      </code>
    </td>
    
    <td>
      Not emitted — presence trusted
    </td>
    
    <td>
      Email is <strong>
        only present on first sign-in
      </strong>
      
      ; later tokens omit it. Returning users are recognised by stable <code>
        sub
      </code>
      
      . May be a <code>
        …@privaterelay.appleid.com
      </code>
      
       private-relay address — still verified
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        keycloak
      </code>
      
      , <code>
        auth0
      </code>
      
      , <code>
        okta
      </code>
      
      , <code>
        salesforce
      </code>
      
      , <code>
        amazon-cognito
      </code>
      
      , <code>
        oidc
      </code>
      
       (generic)
    </td>
    
    <td>
      Fixed (issuer URL of the realm/tenant)
    </td>
    
    <td>
      Emitted as boolean — required <code>
        true
      </code>
    </td>
    
    <td>
      Discovery + standard validation
    </td>
  </tr>
</tbody>
</table>

`isEmailVerified` and `isIssuerValid` are the only places these differences live in code. Adding a new provider doesn't require code changes — an OIDC-compliant provider seeds a row with an `authority`, and a plain-OAuth2 provider seeds a row with explicit endpoint fields; only providers with non-standard claim quirks (Apple's first-login-only email, Microsoft's `/common` issuer template) need to be classified explicitly in those helpers.

## Per-Org SSO Configuration

The data model already supports per-org SSO: an `OidcConfiguration` row named by `Organization.ssoConfigId` will be picked up by the email-first lookup flow. The piece that's not built yet is the **admin UI** for an org admin to create that row and link it to their org.

For now, per-org SSO can be wired manually:

1. Create the `OidcConfiguration` directly in Elasticsearch (via a migration).
2. Append its id to the org's `oidcConfigurationIds`.
3. Add the redirect URI `https://<apiBaseUrl>/api/auth/org/login/sso/callback/<configId>` to the IdP app registration. For same-origin deploys (`kinotic.domain.apiBaseUrl` unset) this falls back to `<appBaseUrl>`; for split-origin deploys (SPA on Static Web Apps, backend on AKS) it must be the backend's hostname so the IdP returns the browser to the kinotic-server pod, not the SPA.

A user who logs in via this path lands at the `/api/auth/org/login/sso/callback/:configId` handler — the IdP doesn't care that the configId is org-scoped instead of platform.

## System Authentication

Kinotic has no login path for system-level operators today; the plan is to move it to Microsoft Entra, separate from the routes documented above. The curated social providers are intentionally limited to end-user self-service signup and grant org-scoped access only, so no route here can produce a `SYSTEM`-scoped session.

## Endpoint Reference

All routes mount under `/api/*` on the api-gateway port (default `58503`). CORS for the SPA origin is applied at the router root. A Vert.x `SessionHandler` covers every `/api/*` route (and the STOMP WebSocket path), so the same session cookie carries the OIDC roundtrip state and the post-login identity; the cookie is `HttpOnly`, `Secure`, `SameSite=Lax`, with a configurable timeout (`kinotic.api-gateway.session-timeout`).

Routes are namespaced under `/api/auth/...`. Organization login and signup are the SPA's paths; the application-login, OAuth, and invite routes are listed for completeness.

<table>
<thead>
  <tr>
    <th>
      Method
    </th>
    
    <th>
      Path
    </th>
    
    <th>
      Owner
    </th>
    
    <th>
      Purpose
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        GET
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/org/login/providers
      </code>
    </td>
    
    <td>
      <code>
        OrganizationLoginHandler
      </code>
    </td>
    
    <td>
      Unique platform social provider keys for the button row
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/org/login/lookup
      </code>
    </td>
    
    <td>
      <code>
        OrganizationLoginHandler
      </code>
    </td>
    
    <td>
      Email-first lookup; <code>
        {type: "sso", redirect}
      </code>
      
       or <code>
        {type: "password"}
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/org/login
      </code>
    </td>
    
    <td>
      <code>
        OrganizationLoginHandler
      </code>
    </td>
    
    <td>
      Email + password; on success establishes the browser session (204 + Set-Cookie)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/org/login/social/start/:provider
      </code>
    </td>
    
    <td>
      <code>
        OrganizationLoginHandler
      </code>
    </td>
    
    <td>
      Begin social-button login; redirects to the IdP
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        GET
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/org/login/social/callback/:configId
      </code>
    </td>
    
    <td>
      <code>
        OrganizationLoginHandler
      </code>
    </td>
    
    <td>
      Social IdP returns here; establishes session, 302 to the SPA root
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        GET
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/org/login/sso/callback/:configId
      </code>
    </td>
    
    <td>
      <code>
        OrganizationLoginHandler
      </code>
    </td>
    
    <td>
      Per-org SSO IdP returns here; establishes session, 302 to the SPA root
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/org/signup
      </code>
    </td>
    
    <td>
      <code>
        OrganizationSignupHandler
      </code>
    </td>
    
    <td>
      Submit email + displayName; sends verification email
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/org/signup/complete
      </code>
    </td>
    
    <td>
      <code>
        OrganizationSignupHandler
      </code>
    </td>
    
    <td>
      Verify token + orgName + password; creates Organization + admin ParticipantIdentity; establishes session
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/org/signup/social/start/:provider
      </code>
    </td>
    
    <td>
      <code>
        OrganizationSignupHandler
      </code>
    </td>
    
    <td>
      Begin social-IdP signup; redirects to the IdP
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        GET
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/org/signup/social/callback/:configId
      </code>
    </td>
    
    <td>
      <code>
        OrganizationSignupHandler
      </code>
    </td>
    
    <td>
      IdP returns here; creates PendingSignUp; redirects to <code>
        /register
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/org/signup/social/complete
      </code>
    </td>
    
    <td>
      <code>
        OrganizationSignupHandler
      </code>
    </td>
    
    <td>
      Consume PendingSignUp; create Org + ParticipantIdentity; establishes session
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        GET
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/app/:orgId/:appId/login/providers
      </code>
    </td>
    
    <td>
      <code>
        ApplicationLoginHandler
      </code>
    </td>
    
    <td>
      Enabled OIDC configs the application references
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/app/:orgId/:appId/login/lookup
      </code>
    </td>
    
    <td>
      <code>
        ApplicationLoginHandler
      </code>
    </td>
    
    <td>
      App-scoped email-first lookup
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/app/:orgId/:appId/login
      </code>
    </td>
    
    <td>
      <code>
        ApplicationLoginHandler
      </code>
    </td>
    
    <td>
      App-scoped email + password; establishes session
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        GET
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/app/:orgId/:appId/login/oidc/callback/:configId
      </code>
    </td>
    
    <td>
      <code>
        ApplicationLoginHandler
      </code>
    </td>
    
    <td>
      App IdP returns here; establishes session
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        GET
      </code>
    </td>
    
    <td>
      <code>
        /.well-known/oauth-authorization-server
      </code>
    </td>
    
    <td>
      <code>
        OAuthServerHandler
      </code>
    </td>
    
    <td>
      RFC 8414 authorization-server metadata
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        GET
      </code>
    </td>
    
    <td>
      <code>
        /.well-known/oauth-protected-resource[/mcp]
      </code>
    </td>
    
    <td>
      <code>
        OAuthServerHandler
      </code>
    </td>
    
    <td>
      RFC 9728 resource metadata for <code>
        /mcp
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        GET
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/oauth/authorize
      </code>
    </td>
    
    <td>
      <code>
        OAuthServerHandler
      </code>
    </td>
    
    <td>
      PKCE authorization-code flow; redirects to the SPA <code>
        /oauth/consent
      </code>
      
       page
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/oauth/device_authorization
      </code>
    </td>
    
    <td>
      <code>
        OAuthServerHandler
      </code>
    </td>
    
    <td>
      RFC 8628 device grant — issue device/user codes; requires the CLI's <code>
        client_id
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/oauth/token
      </code>
    </td>
    
    <td>
      <code>
        OAuthServerHandler
      </code>
    </td>
    
    <td>
      Token endpoint: <code>
        authorization_code
      </code>
      
      , <code>
        refresh_token
      </code>
      
      , and device-code grants
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        GET
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/invite/details
      </code>
    </td>
    
    <td>
      <code>
        InviteHandler
      </code>
    </td>
    
    <td>
      Invitation details + the scope's live provider list
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/invite/accept
      </code>
    </td>
    
    <td>
      <code>
        InviteHandler
      </code>
    </td>
    
    <td>
      Accept by setting a password; establishes session
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/invite/oidc/start/:configId
      </code>
    </td>
    
    <td>
      <code>
        InviteHandler
      </code>
    </td>
    
    <td>
      Accept via OIDC; redirects to the IdP
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        GET
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/invite/oidc/callback/:configId
      </code>
    </td>
    
    <td>
      <code>
        InviteHandler
      </code>
    </td>
    
    <td>
      IdP returns here; accepts the invite, establishes session
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        GET
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/me
      </code>
    </td>
    
    <td>
      <code>
        SessionEndpointHandler
      </code>
    </td>
    
    <td>
      <code>
        204
      </code>
      
       if the session cookie authenticates the caller, else <code>
        401
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/logout
      </code>
    </td>
    
    <td>
      <code>
        SessionEndpointHandler
      </code>
    </td>
    
    <td>
      Destroys the browser session
    </td>
  </tr>
</tbody>
</table>

For the underlying architectural rationale (scope isolation, credential separation, why standalone `OidcConfiguration`), see [System Security](/platform/system-security).
