# Organization Management

> How organizations are created, how their users authenticate, and how OIDC providers are wired up.

## Overview

A Kinotic deployment hosts many customer organizations. Each org has its own users, applications, and (optionally) its own enterprise SSO configuration. This page describes how an org is created, who can log in to it, and how the OIDC plumbing is shared across orgs without leaking access between them.

System-level platform operators (the people who run kinotic-server itself) authenticate through a separate path that is **not** OIDC-based and is out of scope here.

## Mental Model

Three persistent entities carry the auth state, and one short-lived entity bridges the social-signup flow:

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
        KinoticSystem
      </code>
      
       (singleton, id <code>
        kinotic-system
      </code>
      
      )
    </td>
    
    <td>
      Holds the list of platform-wide OIDC configs (e.g. Google, Microsoft) shown as login/signup buttons to everyone
    </td>
    
    <td>
      Bootstrapped from helm config at startup
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        Organization
      </code>
    </td>
    
    <td>
      A customer org. Holds the list of its OIDC configs (typically one — the org's enterprise SSO)
    </td>
    
    <td>
      Created at the end of signup
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        IamUser
      </code>
    </td>
    
    <td>
      A scoped identity (<code>
        authScopeType
      </code>
      
       + <code>
        authScopeId
      </code>
      
      ). One row per (person, org)
    </td>
    
    <td>
      Created during signup or auto-provisioned on first OIDC login
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        OidcConfiguration
      </code>
    </td>
    
    <td>
      A reusable OIDC provider record (clientId, authority, etc.). Referenced by zero or more entities via <code>
        oidcConfigurationIds
      </code>
    </td>
    
    <td>
      Created by <code>
        PlatformOidcBootstrap
      </code>
      
       (platform configs) or by org admins (per-org SSO)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        PendingRegistration
      </code>
    </td>
    
    <td>
      Holds the verified OIDC identity between an IdP callback and the user supplying an org name
    </td>
    
    <td>
      Short-lived; deleted after <code>
        /api/signup/complete-org
      </code>
      
       succeeds
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        SignUpRequest
      </code>
    </td>
    
    <td>
      Holds the org-creation form between submission and email-verification click
    </td>
    
    <td>
      Short-lived; deleted after <code>
        /api/signup/complete
      </code>
      
       succeeds
    </td>
  </tr>
</tbody>
</table>

The relationship between `OidcConfiguration` and the scope that uses it is **by reference**, never embedded. The same Google config can be referenced by `KinoticSystem.oidcConfigurationIds` (so it shows as a button) and by no orgs, or by one org's SSO list and by no system entries — the config itself does not know which scope it serves.

### Two distinct OIDC roles

<table>
<thead>
  <tr>
    <th>
      Role
    </th>
    
    <th>
      Where the configId lives
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
        KinoticSystem.oidcConfigurationIds
      </code>
    </td>
    
    <td>
      A "Continue with Google/Microsoft/…" button on the login <em>
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
        Organization.oidcConfigurationIds
      </code>
    </td>
    
    <td>
      No visible button — reached via the email-first lookup flow when their org has SSO configured
    </td>
  </tr>
</tbody>
</table>

There is no boolean flag distinguishing the two. The **scope that references the config** determines the role. An org admin who configures an SSO provider does not see it appear as a global login button; the platform operator who bootstraps a social provider does not affect any org's SSO settings.

## Org Creation

There are two entry points, both producing an `Organization` and an admin `IamUser` scoped to it:

### Email/password signup

```text
1. User loads /signup, enters orgName + email + displayName
2. POST /api/signup
3. SignUpService.initiateSignUp:
   - rejects if a sign-up is already pending for this email, or
     if an IamUser already exists at ORGANIZATION scope for this email
   - creates a SignUpRequest with a 24h verification token
   - EmailService sends the verification link (logs it instead when email is disabled)
4. User clicks /signup/verify?token=<verificationToken> in their inbox
5. /signup/verify form prompts for password + confirm
6. POST /api/signup/complete  { token, password }
7. SignUpService.completeSignUp:
   - validates token, rejects if expired
   - creates Organization (auto-derived id from name)
   - creates IamUser (authType=LOCAL, authScopeType=ORGANIZATION, scopeId=org.id, enabled=true)
   - links Organization.createdBy = user.id
   - creates IamCredential (bcrypt hash, separate index keyed by user.id)
   - deletes the SignUpRequest
8. Frontend redirects to /login; user can sign in
```

Email verification is the security gate — no `Organization` or `IamUser` exists until the link is clicked. With `KINOTIC_EMAIL_ENABLED=false` (the local default) the verification URL is logged to the kinotic-server console instead of sent; copy it into the browser to finish the flow.

### Social-IdP signup

```text
1. User loads /signup, clicks "Continue with Microsoft"
2. POST /api/signup/start/azure-ad
3. OidcSignupHandler.handleStart:
   - picks the platform OidcConfiguration whose provider key matches
     (looked up via kinoticSystemService.getOidcConfigurations())
   - generates state/nonce/PKCE, stashes them on the session cookie
   - 302 to <authority>/authorize?...
4. User authenticates at the IdP
5. IdP returns to GET /api/signup/callback/<configId>
6. OidcSignupHandler.handleCallback:
   - validates state/PKCE, exchanges code for id_token + access_token
   - rejects if email_verified=false in the id_token
   - rejects with AccountExistsException if an IamUser already exists for (sub, configId)
   - creates a PendingRegistration with the verified subject, configId, email, displayName
   - 302 to /register?token=<verificationToken>
7. /register prompts for orgName (CompleteOrg.vue)
8. POST /api/signup/complete-org  { token, orgName, orgDescription? }
9. PendingRegistrationService.completeWithNewOrg:
   - validates the pending token
   - creates Organization
   - creates IamUser (authType=OIDC, scopeType=ORGANIZATION, scopeId=org.id,
                      oidcSubject + oidcConfigId set, primary=true, enabled=true)
   - links Organization.createdBy
   - deletes the PendingRegistration
10. Backend returns a Kinotic JWT (60s TTL ticket).
    Frontend (App.vue consumeTokenFragment) lifts it from the URL fragment and
    opens the STOMP CONNECT with Authorization: Bearer <jwt>.
```

The `PendingRegistration` is consumed once. The fragment-delivered JWT never hits the server log because browsers don't send URL fragments on requests.

## User Login

Once an org exists, members log in through one of three converging paths:

### Email-first lookup → password or SSO

The login page shows a single email field plus the platform OIDC buttons. Typing an email and submitting drives this:

```text
1. POST /api/login/lookup { email }
2. LoginHandler.handleLookup:
   - finds the user's primary IamUser at ORGANIZATION scope
     (DefaultIamUserService.findByEmailPrimary — term-queries on email + primary=true)
   - if user.authType=OIDC AND the org has an enabled OidcConfiguration:
       generate state/nonce/PKCE, stash on session, return
       { "type": "sso", "redirect": "<authority>/authorize?..." }
       (frontend follows the redirect)
   - otherwise:
       return { "type": "password" }
       (frontend reveals the password field)
3. The "password" branch is deliberately ambiguous — it covers unknown email,
   a local user, and a user whose SSO config has been deleted. This avoids
   leaking which orgs use SSO via timing/responses.
```

A user with multiple `IamUser` rows (multi-org membership keyed by `(oidcSubject, oidcConfigId)`) is routed through whichever row has `primary=true`. The org switcher (post-login) is where they hop to the others.

#### Completing the password branch

When `lookup` returns `{type: "password"}`, the frontend collects the password and exchanges email + password for a Kinotic JWT — **the same JWT shape the OIDC paths produce** — so STOMP CONNECT only ever carries a Bearer token in the SPA flow:

```text
1. POST /api/login/token { email, password }
2. LoginHandler.handleToken:
   - LocalAuthenticationService.authenticateLocal(email, password)
       finds the primary IamUser, requires authType=LOCAL + enabled,
       loads IamCredential, verifies the bcrypt hash
   - on success: mints the 60s Kinotic JWT (scopeType + scopeId + aud=kinotic)
                 returns { "token": "<jwt>" }
   - on any failure: 401 "Invalid credentials"
                     (deliberately generic — covers unknown email, wrong password,
                      OIDC user, disabled user)
3. Frontend calls userState.loginWithToken(token) → STOMP CONNECT with
   Authorization: Bearer <jwt> + the scope headers lifted from the JWT claims
```

The frontend never sends raw passwords over the WebSocket. Direct `login`/`passcode` STOMP CONNECT remains available for non-UI clients (CLI, automation) that already know their `(authScopeType, authScopeId)`; the SPA does not use that path.

### Social button

The buttons are populated from `GET /api/login/providers`, which lists the unique provider keys present in `KinoticSystem.oidcConfigurationIds`. Clicking a button:

```text
1. POST /api/login/start/google
2. OidcLoginHandler.handleSocialStart:
   - finds the platform OidcConfiguration with provider="google"
     (via kinoticSystemService.getOidcConfigurations())
   - same state/PKCE setup as signup, then 302 to the IdP
3. IdP returns to GET /api/login/callback/<configId>
4. OidcLoginHandler.handleCallback:
   - validates state, exchanges code, validates id_token
   - looks up an IamUser by (oidcSubject, oidcConfigId)
   - if none exists: 302 /login?error=no_account so the frontend can show
                     a "no account, sign up?" CTA (signup is a separate flow)
   - if one exists (or multiple — picks the one with primary=true):
       mint a 60s Kinotic JWT carrying scopeType+scopeId+aud=kinotic
       302 to <loginSuccessPath>#token=<jwt>
5. App.vue lifts the JWT from the fragment and opens STOMP CONNECT
```

### STOMP CONNECT (the final step in every path)

Whether the JWT came from a fragment redirect or from the user typing a password, the actual session is established by the STOMP CONNECT frame:

<table>
<thead>
  <tr>
    <th>
      Auth method
    </th>
    
    <th>
      CONNECT headers
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Local password
    </td>
    
    <td>
      <code>
        login
      </code>
      
      , <code>
        passcode
      </code>
      
      , <code>
        authScopeType
      </code>
      
      , <code>
        authScopeId
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      Kinotic JWT (from any OIDC path)
    </td>
    
    <td>
      <code>
        Authorization: Bearer <jwt>
      </code>
      
      , <code>
        authScopeType
      </code>
      
      , <code>
        authScopeId
      </code>
    </td>
  </tr>
</tbody>
</table>

The kinotic-server validates the JWT signature against its rotated signing keys, asserts `aud=kinotic`, and creates the `Session`. The JWT TTL is 60s — long enough to open the WebSocket once, not long enough to be useful if leaked.

## Provider-Specific Quirks

OIDC is a standard, but providers diverge on a few details. The validation helpers in `OAuth2AuthFactory` (`isIssuerValid`, `isEmailVerified`) handle these declaratively — the provider key on `OidcConfiguration` selects the right behaviour. No provider needs handler-level branching.

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

`isEmailVerified` and `isIssuerValid` are the only places these differences live. Adding a new provider that follows the standard set of conventions doesn't require code changes; only providers with non-standard quirks (Apple's first-login-only email, Microsoft's `/common` issuer template) need to be classified explicitly in those helpers.

## Per-Org SSO Configuration

The data model already supports per-org SSO: an `OidcConfiguration` row whose configId is on `Organization.oidcConfigurationIds` will be picked up by the email-first lookup flow. The piece that's not built yet is the **admin UI** for an org admin to create that row and link it to their org.

For now, per-org SSO can be wired manually:

1. Create the `OidcConfiguration` directly in Elasticsearch (POST through the OpenAPI endpoint or via a migration).
2. Append its id to the org's `oidcConfigurationIds`.
3. Add the redirect URI `https://<apiBaseUrl>/api/login/callback/<configId>` to the IdP app registration. For same-origin deploys (`kinotic.apiBaseUrl` unset) this falls back to `<appBaseUrl>`; for split-origin deploys (SPA on Static Web Apps, backend on AKS) it must be the backend's hostname so the IdP returns the browser to the kinotic-server pod, not the SPA.

A user who logs in via this path lands at the same `/api/login/callback/:configId` handler — the IdP doesn't care that the configId is org-scoped instead of platform.

## System Authentication

Kinotic does not use OIDC for system-level operators. The deferred plan is a separate authentication path (likely tied to infrastructure-level credentials) that does not flow through any of the routes documented above. Platform OIDC providers (`KinoticSystem.oidcConfigurationIds`) are intentionally limited to social providers for end-user self-service signup; they grant org-scoped access only.

## Endpoint Reference

All routes mount on the api-gateway port (default `58503`). CORS for the SPA origin is applied at the router root. The `/api/signup/*` and `/api/login/*` namespaces are session-cookie-scoped (clustered Vert.x sessions) for the IdP roundtrip; the cookie is `HttpOnly`, `Secure`, `SameSite=Lax`, with a 10-minute timeout.

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
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/signup
      </code>
    </td>
    
    <td>
      <code>
        SignUpHandler
      </code>
    </td>
    
    <td>
      Submit org signup form; sends verification email
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
        /api/signup/complete
      </code>
    </td>
    
    <td>
      <code>
        SignUpHandler
      </code>
    </td>
    
    <td>
      Verify token + set password; creates Organization + admin IamUser
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
        /api/signup/start/:provider
      </code>
    </td>
    
    <td>
      <code>
        OidcSignupHandler
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
        /api/signup/callback/:configId
      </code>
    </td>
    
    <td>
      <code>
        OidcSignupHandler
      </code>
    </td>
    
    <td>
      IdP returns here; creates PendingRegistration; redirects to <code>
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
        /api/signup/complete-org
      </code>
    </td>
    
    <td>
      <code>
        OidcSignupHandler
      </code>
    </td>
    
    <td>
      Consume PendingRegistration; create Org + IamUser; return Kinotic JWT
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
        /api/login/providers
      </code>
    </td>
    
    <td>
      <code>
        LoginHandler
      </code>
    </td>
    
    <td>
      Returns provider keys from <code>
        KinoticSystem.oidcConfigurationIds
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
        /api/login/lookup
      </code>
    </td>
    
    <td>
      <code>
        LoginHandler
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
        /api/login/token
      </code>
    </td>
    
    <td>
      <code>
        LoginHandler
      </code>
    </td>
    
    <td>
      Email + password → Kinotic JWT (<code>
        {token}
      </code>
      
      ). UI path; non-UI clients can keep using direct STOMP creds
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
        /api/login/start/:provider
      </code>
    </td>
    
    <td>
      <code>
        LoginHandler
      </code>
    </td>
    
    <td>
      Begin social-button login
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
        /api/login/callback/:configId
      </code>
    </td>
    
    <td>
      <code>
        LoginHandler
      </code>
    </td>
    
    <td>
      IdP returns here; validates, mints Kinotic JWT, redirects with <code>
        #token=…
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
        /api/register/complete
      </code>
    </td>
    
    <td>
      <code>
        LoginHandler
      </code>
    </td>
    
    <td>
      Consume PendingRegistration in <code>
        REGISTRATION_REQUIRED
      </code>
      
       mode (separate from <code>
        complete-org
      </code>
      
      )
    </td>
  </tr>
</tbody>
</table>

For the operational steps to bootstrap a platform OIDC provider in any environment, see `docs/local-oidc-setup.md` in the repo. For the underlying architectural rationale (scope isolation, credential separation, why standalone `OidcConfiguration`), see [System Security](/platform/system-security).
