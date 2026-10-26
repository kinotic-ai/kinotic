---
title: "Organization Management"
description: "How organizations are created, how their users authenticate, and how OIDC providers are wired up."
canonical_url: "https://kinotic.ai/platform/organization-management"
---
# Organization Management

> How organizations are created, how their users authenticate, and how OIDC providers are wired up.

## Overview

A Kinotic deployment hosts many customer organizations. Each org has its own users, applications, and (optionally) its own enterprise SSO configuration. This page describes how an org is created, who can log in to it, and how the OIDC plumbing is shared across orgs without leaking access between them.

System users sign in to the system server, separately from everything described here; see [System Authentication](#system-authentication).

## Mental Model

Ten persistent entities carry the auth state, and a short-lived entity bridges each signup. The [Sign-In Design](https://kinotic.ai/platform/reference/sign-in-design) reference draws them and the flows between them.

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
      A registration the system holds with a provider (GitHub) shown as a login/signup button to everyone. Belongs to no organization
    </td>
    
    <td>
      Seeded by SQL migration
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ApplicationSocialOidcConfiguration
      </code>
    </td>
    
    <td>
      A registration the system holds with a social provider (Google, Microsoft) that an application's configuration of the same kind signs in through when its <code>
        clientRegistrationType
      </code>
      
       is <code>
        MANAGED
      </code>
      
      . Belongs to no organization
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
      A customer org
    </td>
    
    <td>
      Created at the end of signup
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        OrganizationOidcConfiguration
      </code>
    </td>
    
    <td>
      A provider record (provider type, account at the provider, clientId) owned by one organization for its own login; its client secret is held in the organization's scope of the secret storage, never on the record
    </td>
    
    <td>
      Created by org admins
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ApplicationOidcConfiguration
      </code>
    </td>
    
    <td>
      The same record made for the login of one of the organization's applications (<code>
        applicationId
      </code>
      
      ); its <code>
        clientRegistrationType
      </code>
      
       says whether it signs in through the organization's own client (<code>
        OWN
      </code>
      
      ) or Kinotic's registration of its provider kind (<code>
        MANAGED
      </code>
      
      )
    </td>
    
    <td>
      Created by org admins
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        Tenant
      </code>
    </td>
    
    <td>
      A tenant of an application whose tenants its administrators manage: a customer of the application, whose users carry its id and whose sites, <code>
        <tenantId>--<applicationId>--<uiName>
      </code>
      
      , sign them in
    </td>
    
    <td>
      Created by org admins, <code>
        TenantService
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        TenantOidcConfiguration
      </code>
    </td>
    
    <td>
      The same record made for the login of one tenant of an application (<code>
        applicationId
      </code>
      
      , <code>
        tenantId
      </code>
      
      )
    </td>
    
    <td>
      Created by org admins
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        OrganizationParticipantIdentity
      </code>
    </td>
    
    <td>
      A member of one organization (type <code>
        ORGANIZATION_USER
      </code>
      
      ): carries <code>
        organizationId
      </code>
      
       and is unique by email within that organization. A person in several organizations is a separate organization user of each, with its own password, bindings and <code>
        enabled
      </code>
      
       flag
    </td>
    
    <td>
      Created at signup, by an administrator adding a member, or by invitation
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ApplicationParticipantIdentity
      </code>
    </td>
    
    <td>
      A user of one application (type <code>
        APPLICATION_USER
      </code>
      
      ): carries <code>
        organizationId
      </code>
      
      , <code>
        applicationId
      </code>
      
       and, for an application with tenants, <code>
        tenantId
      </code>
      
      , and belongs to that application alone
    </td>
    
    <td>
      Created by an admin, invitation, a provider's <code>
        createUserOnSignIn
      </code>
      
      , or sign-up with an email and a password
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        OidcBinding
      </code>
    </td>
    
    <td>
      A provider identity bound to a user: <code>
        (oidcConfigId, subject)
      </code>
      
       → identity id, at most once among each organization's users and once among each application's users, so one GitHub account may sign in a user of every organization it is bound in; an application user's <code>
        subject
      </code>
      
       is the identifier the provider gives the person under every client it registers (Google's <code>
        sub
      </code>
      
      , Microsoft's <code>
        <tid>/<oid>
      </code>
      
      ). With the optional password these are the user's sign-in methods
    </td>
    
    <td>
      Created at signup, by invitation, by an organization's provider claiming the member an administrator added, by <code>
        createUserOnSignIn
      </code>
      
       for an application's user, or by the user from the Profile page
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        PendingApplicationSignUp
      </code>
    </td>
    
    <td>
      Holds a sign-up to an application with an email and a password, from the verification email until the person chooses a password
    </td>
    
    <td>
      Short-lived; deleted once <code>
        /api/auth/app/signup/complete
      </code>
      
       succeeds
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

A configuration is offered at the one scope its kind names. When the same Okta tenant serves both the org's own login and one of its applications', the org copies the configuration into the application's scope with `OwnedOidcConfigurationService.copy`; the copy is registered with the provider under its own callback URL and keeps its own copy of the client secret. An application signs in through the system's Google and Microsoft registrations with an `ApplicationOidcConfiguration` of that kind whose `clientRegistrationType` is `MANAGED`; switching it to `OWN` with the organization's own client ID and secret is an edit of the same configuration, and its users stay the same users.

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
      Offered by
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
        Social sign-in
      </strong>
      
       (org sign-up)
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
      A "Continue with GitHub" button on the login <em>
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
        OrganizationOidcConfiguration
      </code>
    </td>
    
    <td>
      The org, at its own login, by creating the configuration
    </td>
    
    <td>
      A "Sign in with <provider>
        " button on the organization's own login page, <code>
          /login/<organizationId>
        </code>
      </provider>
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Application sign-in
      </strong>
    </td>
    
    <td>
      <code>
        ApplicationOidcConfiguration
      </code>
    </td>
    
    <td>
      The org, at one of its applications' logins, by creating the configuration for that application, through its own client or, for Google and Microsoft, Kinotic's <code>
        ApplicationSocialOidcConfiguration
      </code>
    </td>
    
    <td>
      A "Continue with <name>
        " button on that application's own sign-in page, and on its sign-up page when the configuration signs people up
      </name>
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Tenant SSO
      </strong>
    </td>
    
    <td>
      <code>
        TenantOidcConfiguration
      </code>
    </td>
    
    <td>
      The org, at the login of one tenant of one of its applications, by creating the configuration for that tenant
    </td>
    
    <td>
      A "Continue with <name>
        " button on the tenant's own sites, before the application's buttons
      </name>
    </td>
  </tr>
</tbody>
</table>

An `ApplicationSocialOidcConfiguration` is never offered on its own: it is the credentials a `MANAGED` application configuration signs in through. The roles are separate *subtypes* of `OidcConfiguration`, stored in one index under a `type` discriminator the way the kinds of `ParticipantIdentity` are, rather than one type with a flag, because the difference between them is ownership. Every lookup filters on the discriminator and every write is held to its kind's owner, so an org admin configuring SSO can never make it appear as a global button or name a system secret, and a seeded system registration can never affect an org's SSO settings.

## Org Creation

There are two entry points, both producing an `Organization` and an admin `ParticipantIdentity` scoped to it. Every sign-up founds a new organization with the founder as its first user, whether or not the email or the provider identity already signs in to another organization. The SPA's `/signup` page currently offers **GitHub only** (a hardcoded button — every Kinotic project is backed by a GitHub repository, so the founding user signs up with GitHub and installs the GitHub App right after). The email/password signup entry point remains fully mounted on the backend; its previous UI (`Signup.vue`) is kept in the tree unrouted until other methods return. Login is not restricted: invitees must be able to sign back in however they joined, so an organization's login page keeps every provider the organization offers plus a password form.

### Email/password signup (backend flow — not currently reachable from the SPA)

```text
1. User loads /signup, enters email + displayName
2. POST /api/auth/org/signup
3. SignUpService.initiateLocalSignUp:
   - rejects if a sign-up is already pending for this email
   - creates a PendingSignUp with a 24h verification token
   - EmailService sends the verification link (logs it instead when email is disabled), which
     opens the portal
4. User clicks /signup/verify?token=<verificationToken> in their inbox
5. /signup/verify form (VerifyEmail.vue) prompts for orgName + password + confirm
6. POST /api/auth/org/signup/complete  { token, orgName, orgDescription?, password }
7. SignUpService.completeLocalSignUp:
   - validates token, rejects if expired
   - creates Organization (auto-derived id from name)
   - creates the founder as the organization's first user (OrganizationParticipantIdentity,
     organizationId = the new organization, enabled=true) with an IdentityCredential (bcrypt
     hash, separate index keyed by the organization user's id)
   - links Organization.createdBy = the organization user's id
   - deletes the PendingSignUp
8. The gateway establishes the browser session (204 + Set-Cookie); the frontend then calls
   userState.login() to open the realtime connection, authenticated by that session cookie.
```

Email verification is the security gate — no `Organization` or organization user exists until the link is clicked. With `KINOTIC_EMAIL_ENABLED=false` (the local default) the verification URL is logged to the management server's console instead of sent; copy it into the browser to finish the flow.

### Social-IdP signup

```text
1. User loads /signup, clicks "Sign up with GitHub" (the page's only button today)
2. POST /api/auth/org/signup/social/start/github
3. OrganizationSignupHandler.handleSocialStart:
   - picks the OrgSignupOidcConfiguration whose provider key matches
     (oidcConfigurationService.findEnabledOrgSignupByProvider)
   - generates state/nonce/PKCE, stashes them on the session cookie
   - 302 to <authority>/authorize?...
4. User authenticates at the IdP
5. IdP returns to GET /api/auth/org/login/social/callback/<configId>, the one callback the
   registration is registered with; the flow's purpose, recorded at its start, routes it
6. OrganizationOidcCallbackHandler → OrganizationSignupHandler.complete:
   - validates state/nonce/PKCE, exchanges code for id_token + access_token
   - rejects with 302 /login?error=email_not_verified&provider=<key> unless the provider
     verified the email (see Provider-Specific Quirks)
   - SignUpService.createOidcPending creates a PendingSignUp with the verified subject,
     configId, email, displayName
   - 302 to /register?token=<verificationToken> on the UI the sign-up started from, when
     kinotic.cors.allowedOriginPattern names its origin other than through *, and on the portal otherwise
7. /register prompts for orgName (CompleteOrg.vue)
8. POST /api/auth/org/signup/social/complete  { token, orgName, orgDescription? }
9. SignUpService.completeOidcWithNewOrg:
   - validates the pending token
   - creates Organization
   - creates the founder as the organization's first user (enabled=true) with an
     OidcBinding of (configId, sub) to it in the new organization
   - links Organization.createdBy
   - deletes the PendingSignUp
10. The gateway establishes the browser session (204 + Set-Cookie). CompleteOrg.vue then
    calls userState.login() to open the realtime connection, authenticated by that session
    cookie. No token travels in the URL.
11. CompleteOrg.vue shows a "Connect GitHub" step explaining that the next GitHub
    round-trip authorizes repository access (distinct from the sign-in, which only
    proved identity). Clicking "Continue to GitHub" calls
    githubAppInstallations.startInstall("/") and redirects the whole tab to
    the returned GitHub install URL. The App requests user authorization (OAuth) during
    installation, so GitHub returns to the management server's /api/github/install/callback
    — the App's first registered callback URL — with code, installation_id, and the state
    minted by startInstall, and that route forwards them to the portal's
    /github/install/callback. The callback runs completeInstall, which consumes the state, exchanges
    the code with the github-platform credential for the authorizing user's access
    token, and binds the installation only when GitHub's /user/installations reports
    that user can access it (see [Defense in Depth](/platform/defense-in-depth)). It
    then lands on / — project creation requires the install, so the new org arrives
    ready. If the install can't start, the page falls back to / directly. The portal's /
    opens the Quickstart until the organization has its first project, and Applications
    after that.
```

Another UI signed in through the management server, such as one built on Kinotic, links GitHub
without the portal: it calls `startInstall` with the absolute URL of one of its own pages as
`returnTo`, which must be on an origin `kinotic.cors.allowedOriginPattern` names (a pattern of
`*` names none);
`startInstall` refuses any other URL, and a network-path reference (a `returnTo` starting with `//`
or `/\`). After the install, `/api/github/install/callback` forwards GitHub's `installation_id`,
`state` and `code` to that page as query parameters, and the page calls `completeInstall` itself.

The `PendingSignUp` is consumed once. The `?token=` in the redirect to `/register` is the short-lived `PendingSignUp` verification token. It is not a login, which the session cookie set when the org-naming POST succeeds establishes, but whoever holds it can found the organization as the verified identity, so it is handed only to a UI the CORS pattern trusts.

GitHub follows the same flow with one difference in step 6: GitHub is OAuth 2.0 only and issues no id_token, so its configuration row sets the endpoints explicitly instead of relying on discovery — `authorizationUri`/`tokenUri` drive the code flow, `userInfoUri` supplies `sub` and the display name, and `userEmailsUri` supplies the email plus its `verified` flag (primary address preferred). Any curated row that sets these fields takes this path; nothing about it is GitHub-specific in code, and only a curated row can set them. Issuer, audience, and nonce validation don't apply (there is no token to carry them); the state match and the direct TLS code exchange are the security boundary.

## Members

An organization's administrators add its members on the Members page, one action for every case: **Add member**, with the person's email, an optional display name and a switch, on by default, that sends them an invitation email (`MemberService.addMember(email, displayName, sendInvitation)`, `Kinotic.members.addMember`).

- **The person** becomes a user of the organization at once, with no sign-in method, so the person appears among the members as *Pending*. With the switch on, the invitation they receive claims that user: they set a password, or sign in through one of the organization's providers under the same address. With the switch off, nothing is sent and the person signs in through one of the organization's own providers (`OrganizationOidcConfiguration`), whose first sign-in under the invited address claims the user — so the switch can only be off when the organization has one, and the server refuses the request otherwise. An address that is already a member of the organization is refused. A user of another organization, a system user or an application's user with the same address is a different identity, which neither the invitation nor the member list sees.
- **Pending members** are listed with the invitations alike, each with *Send invitation* (`MemberService.sendInvitation(email)`, which sends the one they have again or a new one for a member added without) and *Remove*.
- **Removing a member** deletes the organization's user, with its password, its bindings and its delegates, and cancels the invitation it never accepted. The person's users of other organizations are unaffected.
- **An application's users** are still invited (`MemberService.inviteMember(email, displayName, applicationId)`), since their rows belong to the application and are created when the invitation is accepted.

### Email templates

The portal's invitations and verification links open the portal and use Kinotic's own emails. A server
that embeds Kinotic's modules to serve a UI of its own sends them in that UI's name instead, by passing an
`EmailTemplate` in process: management-api's `MemberInvitationService.addMember`, `sendInvitation` and
`inviteMember` (the calls `MemberService` makes, run as the organization user bound to the call) and
kinotic-domain's `SignUpService.initiateLocalSignUp`. `EmailTemplate` is not part of any published service, so no
gateway connection can choose one.

A template's `uiBaseUrl` is the UI whose `/invite/accept?token=` or `/signup/verify?token=` page its link
opens, with no trailing slash, and its `subject`, `htmlBody` and `textBody` are Handlebars sources rendered
with the email's variables. A call that would send an email with a template whose `uiBaseUrl` is not an absolute
http or https URL naming a host, without user info, a query, fragment or trailing slash, or whose source is missing
or does not compile and render, fails with
`IllegalArgumentException` before it saves anything. Write a URL variable in the HTML with triple braces, such as `{{{acceptUrl}}}`, so it is not
HTML-escaped.

<table>
<thead>
  <tr>
    <th>
      Email
    </th>
    
    <th>
      Variables
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      An invitation into an organization or one of its applications
    </td>
    
    <td>
      <code>
        inviterName
      </code>
      
      , <code>
        organizationName
      </code>
      
      , <code>
        applicationName
      </code>
      
       (absent for an organization invitation), <code>
        acceptUrl
      </code>
      
      , <code>
        expiresInDays
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      A sign-up with email and password
    </td>
    
    <td>
      <code>
        displayName
      </code>
      
      , <code>
        verificationUrl
      </code>
    </td>
  </tr>
</tbody>
</table>

That UI's invitation page calls the `/api/auth/invite/*` routes below. An acceptance through a provider
returns to the page that started it, and a failed one to that page's `/invite/accept?error=` (`email_not_verified` also names the provider's key in `provider`), when
that page is the portal or `kinotic.cors.allowedOriginPattern` names it, and to the portal otherwise. An
application invitation whose application has its own invitation template is sent with that template's
content, and its link still opens the passed template's UI.

The Members page lists the organization's members as `OrganizationMember` rows (`MemberService.findMembers` / `searchMembers`): each is one of the organization's users, so `enabled` is the user's own flag, `joined` its creation, and `pending` marks a member who has not signed in yet. An application's Users page lists the application's users themselves, `ApplicationParticipantIdentity` rows (`MemberService.findApplicationUsers` / `searchApplicationUsers`), whose standing is the user's own.

An organization's own provider therefore signs in only the members its administrators added: a person the provider verifies whose address no pending member holds gets `no_user`, and `createUserOnSignIn` is refused on an `OrganizationOidcConfiguration`. Nothing an organization's provider asserts can reach a user of another organization.

## User Login

A login is addressed to an organization and signs in one of its users. A person in several organizations is a separate user of each, so nothing about the person alone says where they are signing in; the organization's id, the slug of its hostnames (see [Applications and Projects](https://kinotic.ai/apps/application-structure/applications-and-projects#applications)), is what every login route and page is addressed by. The portal has two login pages:

- `/login`, the landing page, for someone whose organization is not yet known: the system's social buttons (GitHub), which sign in without naming an organization when the identity is bound to a user of exactly one and otherwise offer the organizations of its users ([below](#social-button-on-the-landing-page)); an organization field that leads to that organization's page; a "Don't know your organization?" link that emails the person the organizations their address belongs to; and a **Create Organization** link that opens sign-up.
- `/login/<organizationId>`, the organization's page (`OrganizationLoginPage.vue`), which offers exactly what that organization's login supports, as `GET /api/auth/org/login/<organizationId>/providers` lists it: one button per configuration — the system's org sign-up registrations (GitHub) followed by the organization's own `OrganizationOidcConfiguration`s — and, only while the organization's login takes a password ([password sign-in](#password-sign-in)), an email + password form. The organization's own host, `<organizationId>.<sitesDomain>`, redirects here.

The browser remembers, in `localStorage`, the organization it last signed in to, whichever method signed it in: password, GitHub, an organization's own provider, sign-up or an accepted invitation. A returning browser that opens `/login` is sent on to that organization's page, keeping the query and so the `referer`. The landing page stays put when nothing is remembered, and when its query carries `?error=`, so a failed sign-in or a choice of organizations is shown there; its organization field is then prefilled with the remembered organization. The organization page's **Not your organization?** link forgets the remembered organization before it returns to `/login`, so the landing page stays put. When the remembered organization no longer exists, the organization page forgets it and returns to the landing page itself, which reports that there is no organization by that name and leaves the name in its organization field to correct; an organization that was typed and does not exist is reported on its own page.

No route asks about an email first: the page offers every method up front, and the password login answers a generic `401`, so nothing tells whether an email has a user anywhere. What the page does disclose, to anyone who knows an organization's id, is that the organization exists and the names and provider kinds of the configurations its login offers, as any SSO login page does; the id is already public as the organization's hostname, and the configurations are the buttons the page shows. A session acts in one organization. To use another, the person signs in at that organization's page, `/login/<organizationId>`, with a method that organization's login offers.

### Password

The form, shown while the organization's login takes a password ([password sign-in](#password-sign-in)), posts email + password to the organization's login route, which verifies the credential and **establishes a browser session** — there is no client-held token. The `fetch` uses `credentials: 'include'` so the gateway's `Set-Cookie` is stored even cross-origin:

```text
1. POST /api/auth/org/login/<organizationId> { email, password }   (credentials: 'include')
2. OrganizationLoginHandler.handleLogin → AuthEndpointSupport.handlePasswordLogin:
   - LocalAuthenticationService.authenticateOrganizationUser(email, password, organizationId)
       requires the organization's login to take a password, finds the organization's
       user with that email (a user of another organization, a system user or an
       application user with the same email is another row and never signs in here),
       requires it enabled, loads its IdentityCredential and verifies the bcrypt hash; a
       user with no password fails here
   - ParticipantIdentityService.resolveParticipant(user, organizationId, null)
       makes the OrganizationParticipant of the organization's user
   - on success: establishSession(ctx, participant) puts it on the Vert.x session
                 (regenerating the session id), then 204 + Set-Cookie
   - on any failure: 401 "Invalid credentials"
                     (deliberately generic — covers unknown email, wrong password,
                      no password, disabled user, an organization whose login takes
                      no password)
3. OrganizationLoginPage.vue calls userState.login() → Kinotic.connect().
   The default resolution supplies no auth headers in a browser, so the
   WebSocket upgrade is authenticated by the session cookie set in step 2.
```

The same check holds for a client that presents an email and password at the WebSocket upgrade with an organization's scope header: an organization whose login takes no password refuses it with the same generic error.

The SPA never exchanges the password for a token and never sends raw passwords over the WebSocket. Non-UI clients (CLI, automation) do not use this path — they authenticate directly at the upgrade instead; the table in "The WebSocket upgrade" below covers what each client type presents.

### A provider the organization offers

Each button on the organization's page is a form posting to the organization's start route for that configuration. The route also answers `GET`, so a link — from a bookmark, or from the organization's own tile in its provider's application launcher — starts the same flow:

```text
1. POST|GET /api/auth/org/login/<organizationId>/start/<configId>?referer=<spa-path>
   - the router guard bounced an unauthenticated navigation to /login?referer=<spa-path>,
     and the login pages forward it here; a provider sign-in leaves the SPA, so the path
     cannot be kept client side the way the password path keeps it
2. OrganizationLoginHandler.handleStart:
   - an enabled OrgSignupOidcConfiguration under that id, which every organization's
     login offers; otherwise the organization's own configuration under it
     (oidcConfigurationService.findOffered(configId, organizationId, null)); 400 for
     anything else
   - stashes the organization on the flow session, with the referer when it names a
     path within the SPA, then state/nonce/PKCE as for signup, and 302 to the IdP
   - the completed login is bound to the page that started it, or to the portal when a
     GET link, which names no page, started it; a page that is neither the portal nor one
     kinotic.cors.allowedOriginPattern names is not recorded, and the flow returns to
     the portal holding no login for it
3. IdP returns to the callback registered for the configuration:
     GET /api/auth/org/login/social/callback/<configId>   an org sign-up registration
     GET /api/auth/org/login/sso/callback/<configId>      the organization's own configuration
4. OrganizationLoginHandler.completeOrganizationLogin:
   - validates state/nonce, exchanges code, validates id_token (sub + email_verified);
     an unverified email: 302 /login?error=email_not_verified&provider=<key>
   - looks up the stashed organization's user bound to (configId, sub)
     (ParticipantIdentityService.findOrganizationUserByOidcIdentity)
   - no user bound to the identity: through the organization's own configuration, the
                 member an administrator added under the identity's verified email that
                 nobody has signed in as yet is claimed — the identity is bound to that
                 user as its first sign-in method and it signs in; otherwise
                 302 /login?error=no_user
   - a disabled user: 302 /login?error=user_disabled
   - otherwise: establishSession(ctx, participant), then redirectSuccess → 302 to the
       stored referer, or the SPA root when the flow stored none, with Set-Cookie.
       No token travels in the URL.
5. The SPA loads with the session cookie set; userState.login() opens the realtime
   connection, authenticated by that cookie.
```

### Social button on the landing page

The landing page's buttons are populated from `GET /api/auth/org/login/providers`, which lists the unique provider keys present in the org sign-up configs (the Members page reads the same endpoint). Every organization's login offers those registrations, so a sign-in there needs no organization named: its callback finds the users the identity is bound to, one in each organization at most.

```text
1. POST /api/auth/org/login/social/start/github?referer=<spa-path>
2. OrganizationLoginHandler.handleSocialStart:
   - finds the OrgSignupOidcConfiguration with provider="github"
     (oidcConfigurationService.findEnabledOrgSignupByProvider)
   - same flow-session setup as above, with no organization stashed, then 302 to the IdP
3. IdP returns to GET /api/auth/org/login/social/callback/<configId>
4. OrganizationLoginHandler.completeLandingLogin:
   - validates the callback as above
   - finds every organization's user bound to (configId, sub) and keeps the enabled ones
     (ParticipantIdentityService.findOrganizationUsersByOidcIdentity)
   - exactly one: signs in to that user's organization as above
   - none bound: 302 /login?error=no_user
   - every bound user disabled: 302 /login?error=user_disabled
   - several: their organizations' ids are kept in the session for the page that started
     the flow and 302 /login?error=multiple_organizations&referer=<spa-path> — the person
     has proven who they are, so the landing page reads the ids from
     GET /api/auth/org/login/choices, answered for two minutes and until the page starts a
     login at an organization, and offers those organizations to choose from; the person
     signs in at the chosen one's own page, which lands on the referer. The redirect
     carries no ids, so the page that started the flow learns only the code
```

### Finding one's organizations

`POST /api/auth/org/login/find { email, turnstileToken }` emails the address every organization it is an enabled member of, each with a link to that organization's login page (`OrganizationService.sendOrganizationsEmail`); an organization where the address's user is disabled is left out, as it cannot sign in there. The route always answers `204` and the email is sent whether or not the address belongs to any organization — one that belongs to none says so — so the request reveals nothing about the address. With `KINOTIC_EMAIL_ENABLED=false` the links are logged to the management server's console instead of sent.

The route is open to anyone and sends an email on every call, so it requires a person at the browser: a Cloudflare Turnstile token earned on the page, verified by the management server with the site's secret (`TurnstileVerifier`, configured under `kinotic.managementServer.turnstile.*`). `GET /api/auth/org/login/find` answers `{turnstileSiteKey}`, the site whose challenge the landing page renders before it enables the button, and a request whose token is missing, already verified or refused answers `400`. An environment with no Turnstile site, such as the e2e stack, turns the requirement off with `kinotic.managementServer.turnstile.enabled=false`, and the `GET` answers `{}`; development uses Cloudflare's always-passing test site.

### System users (SYSTEM scope)

A system user is a `SystemParticipantIdentity` (type `SYSTEM_USER`): unique by email among system users, acting in the system scope alone. The same email may also be an organization user and a user of any application, each with its own password. System users sign in through a separate, password-only route used by the system console (there is no signup, SSO, or social path for SYSTEM scope):

```text
1. POST /api/auth/system/login { email, password }   (credentials: 'include')
2. SystemLoginHandler.handleLogin → AuthEndpointSupport.handlePasswordLogin:
   - LocalAuthenticationService.authenticateSystemUser(email, password)
       verifies the system user's password, then resolveParticipant(user.id, null, null)
       makes its SystemParticipant — an organization user or an application user, even
       with the same email, is another row and can never authenticate here
   - success/failure behave exactly like the org password branch:
     204 + Set-Cookie, or a generic 401
```

The session established here is the same browser-session mechanism as every other login path; `/api/auth/me` and `/api/auth/logout` apply unchanged.

The console lists them at **Members → Users**, read-only, from `SystemMemberService.findUsers` in the system zone, which lists the `SystemParticipantIdentity` rows. System users are created outside the console — by the migration fixtures, or by the identity provider once SYSTEM scope is behind Entra — so the page reports who holds access rather than granting it.

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

The browser SPA never holds a JWT — its login establishes a session cookie and that cookie authenticates the upgrade. The Bearer path is for non-browser clients: the CLI obtains an access token through the OAuth device-code grant at `POST /api/auth/oauth/token`, which mints a Kinotic JWT carrying `sub` / `email` / `organizationId` / `applicationId`. For that path the management server validates the JWT signature against its signing keys and creates the `Session`. Every path admits only the server's own kind of participant: a credential that proves an identity of another scope fails as invalid (see [System Security](https://kinotic.ai/platform/system-security#authentication-methods)). The CLI persists a rotating refresh token to mint fresh access tokens before each connect.

## Provider-Specific Quirks

OIDC is a standard, but providers diverge on a few details. The validation helpers in `OAuth2Util` (`isIssuerValid`, `isEmailVerified`) handle these declaratively — the provider key on `OidcConfiguration` selects the right behaviour. No provider needs handler-level branching: a row either has an `authority` for OIDC discovery — derived from the organization's `providerAccount` for an organization's row, seeded for a curated one — or, for a curated row only, sets `authorizationUri`/`tokenUri`/`userInfoUri`/`userEmailsUri`/`scopes` explicitly, and every handler consumes the same claims shape either way.

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
        okta
      </code>
    </td>
    
    <td>
      Fixed, <code>
        https://<okta domain>
      </code>
      
      , derived from the organization's Okta domain
    </td>
    
    <td>
      Not in the id_token of a code exchange — read from the discovered <code>
        userinfo_endpoint
      </code>
      
       with the access token, then required <code>
        true
      </code>
    </td>
    
    <td>
      Discovery + standard validation
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
      
      ; read from the userinfo endpoint when the id_token states none
    </td>
    
    <td>
      Discovery + standard validation; not yet offered to organizations, since no issuer template exists for them
    </td>
  </tr>
</tbody>
</table>

An id_token that carries an email but no `email_verified`, from a provider other than Entra or Apple, takes `email_verified` from the provider's userinfo endpoint (`OidcFlowOrchestrator`): the response's `sub` must equal the id_token's, or the sign-in fails with `invalid_token`, and its `email_verified` counts only when its `email` is the id_token's. An endpoint that answers nothing, or none at all, leaves the claims as the id_token gave them, so the sign-in fails with `email_not_verified`, and the failure is logged. `isEmailVerified` and `isIssuerValid` are the only places the other differences live in code. Adding a curated provider doesn't require code changes — an OIDC-compliant provider seeds a row with an `authority`, and a plain-OAuth2 provider seeds a row with explicit endpoint fields; only providers with non-standard claim quirks (Apple's first-login-only email, Microsoft's `/common` issuer template) need to be classified explicitly in those helpers. Offering a type to organizations is one line on `OidcProviderType`: its issuer template and the pattern the organization's account must match.

### Password sign-in

An email and a password is a sign-in provider like the others: a `PasswordConfiguration` of one sign-in scope — the organization's own login, one of its applications', or one tenant's — listed among the scope's providers in the portal as **Email and password**, and saved through the published `OwnedPasswordConfigurationService` (`Kinotic.ownedPasswordConfigurations` in `@kinotic-ai/management-api`, and an MCP tool), which acts for the caller's organization alone. A scope has at most one, whose id is derived from the scope (`<organizationId>[:<applicationId>[:<tenantId>]]`); `enabled` decides whether the scope's login takes a password, and `createUserOnSignIn`, taken by an application's own configuration only and only while the system manages its tenants, turns on password sign-up there (see [Authentication](https://kinotic.ai/apps/security/authentication#signing-up)). A login takes a password only while `PasswordConfigurationService.findOffered` finds one: the organization's own login its configuration, an application's its own, and a tenant's the tenant's, else the application's. No organization has one until an administrator adds it, except an organization founded through the email and password sign-up routes, which gets an enabled one with the organization, since its founder has no other way in.

While an organization's login takes no password, its login page shows no password form and its password login answers `401`, and its invitations are accepted only through its providers: `GET /api/auth/invite/details` answers `localEnabled: false` and the accept page offers no password, and `POST /api/auth/invite/accept` answers `400`. Disabling or deleting the organization's own configuration is refused, naming the members, while an enabled member holds a password and no identity bound through a configuration the organization's login offers, since that member would have no way in; a member nobody signs in as yet is claimed through the organization's provider and holds nothing back. The same rule holds the other way: disabling or deleting one of the organization's own OIDC configurations is refused while it is the only way in for an enabled member who signs in today (`OrganizationSignInGuard`). An application's user's password signs in where the user's own login takes one: its tenant's, else its application's, whichever login it names, so a connection's headers, which name no tenant, find it too. Deleting an application or a tenant deletes its configuration, so one created again under the same id starts with none. An application's invitations are accepted with a password alone, so one is made, and accepted, only while the application's login takes one.

## Per-Org SSO Configuration

An `OrganizationOidcConfiguration` is offered at the org's own login page, `/login/<organizationId>`, by existing. An organization admin manages the organization's configurations in the portal under **Organization settings → Authentication providers**, or through the published `OwnedOidcConfigurationService` the page calls (`Kinotic.ownedOidcConfigurations` in `@kinotic-ai/management-api`, and an MCP tool), which acts for the caller's organization alone.

1. **Add provider** opens the form in a drawer on the right. Pick the provider (Google, Microsoft or Okta) and enter the display name the login page prints on its button, the organization's account at the provider where the kind takes one (the **Directory (tenant) ID** for Microsoft Entra ID, never `common` or `organizations`, which an application's provider may name, the **Okta domain** on `okta.com`, `oktapreview.com` or `okta-emea.com` for Okta), the client ID and the client secret, or tick **Public client (no secret)**. The form checks the account against the kind's pattern before it saves, and shows the server's message when the server still refuses. The audience is optional, under **Advanced**. Saving calls `OwnedOidcConfigurationService.save(configuration, clientSecret)`: the authority is derived from the kind's template, so no URL is given, and the `organizationId` is the caller's whatever was sent. A secret is held in the organization's own scope of the secret storage — the row itself never names one, and nothing reads it back through the service; `confidentialClient` says whether one is held. `createUserOnSignIn` is refused on an organization's configuration, and the form does not offer it: the organization's administrators add its members (see [Members](#members)), and the provider signs in the member it verifies under a pending member's address, claiming that organization user on the first sign-in, or the organization user its identity is already bound to. A person the provider verifies whose address is no member's gets `no_user`, and so does one whose address is a member someone already signs in as: that member connects the provider from the Profile page.
2. Once saved, the form shows the configuration's **callback URL**, `<kinotic.managementApi.apiBaseUrl>/api/auth/org/login/sso/callback/<configId>`, which `OwnedOidcConfigurationService.findCallbackUrl(configId)` returns. Add it to the IdP app registration as a redirect URI; sign-ins through the configuration fail until it is there. It is the only one the configuration needs: a login, an invitation accepted through the provider, and a member connecting the provider to their organization user from the Profile page all return to it, and the flow's purpose, recorded on the session when it starts, says which it is. For a same-origin deploy that base is the portal's own origin; for a split-origin deploy (the SPA on the sites Worker, the backend on AKS) it is the backend's hostname, so the IdP returns the browser to the management server, not the SPA. The provider's row, its **Edit** form and its details all show the same URL again.
3. Editing a configuration leaves its kind fixed, since the kind is the scope it is offered at and the server refuses a save that changes it. The client secret field starts empty: a save with it empty keeps the secret held (`save` with a null or blank secret keeps it), and a secret entered replaces it. **Make this a public client**, which asks for confirmation, deletes the held secret with `OwnedOidcConfigurationService.deleteClientSecret(configId)`.
4. The row menu also enables or disables the configuration (a disabled one is listed on no login page and signs no one in), deletes it after a confirmation (sign-ins through it stop at once and its held secret is deleted), and copies it to an application: **Copy to application** calls `OwnedOidcConfigurationService.copy(configurationId, applicationId)`, which makes an `ApplicationOidcConfiguration` with the source's provider, account, client ID and secret, and shows the copy's own callback URL, `<the application's API host>/api/auth/app/login/oidc/callback/<copyId>`, to register with the provider as well. The application's API host is its label under `kinotic.domain.appApiBaseUrl`.

A user who logs in via this path lands at the `/api/auth/org/login/sso/callback/:configId` handler — the IdP doesn't care that the configId is org-scoped instead of system-scoped. The provider's application launcher can link straight to `<kinotic.managementApi.apiBaseUrl>/api/auth/org/login/<organizationId>/start/<configId>`, which starts the same flow from a plain `GET`; the form and the row's details show it as the **Direct sign-in link**.

## System Authentication

System users (`SystemParticipantIdentity`) sign in to the system server with an email and password, `POST /api/auth/system/login`, and the plan is to move it to Microsoft Entra, separate from the routes documented above. The system login authenticates system users alone, so an organization user is refused there as a wrong password is, and every organization login authenticates organization users alone, so a system user is refused there the same way. A person who is both signs in to each with that scope's own user and password. The curated social providers are intentionally limited to end-user self-service signup and grant org-scoped access only, so no organization route can produce a `SYSTEM`-scoped session.

## Endpoint Reference

Each handler is a component of the module that serves it, so a server mounts the routes of the modules it loads, on its own gateway port (org `58503`, system `58504`, app `58505` by default):

<table>
<thead>
  <tr>
    <th>
      Handler
    </th>
    
    <th>
      Module
    </th>
    
    <th>
      Management server
    </th>
    
    <th>
      System server
    </th>
    
    <th>
      App server
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        OrganizationOidcCallbackHandler
      </code>
      
      , <code>
        OrganizationLoginHandler
      </code>
      
      , <code>
        OrganizationSignupHandler
      </code>
      
      , <code>
        OrganizationUserSignInMethodHandler
      </code>
      
      , <code>
        InviteHandler
      </code>
      
      , <code>
        DeviceAuthorizationHandler
      </code>
      
      , <code>
        GitHubWebhookHandler
      </code>
    </td>
    
    <td>
      <code>
        kinotic-server-management
      </code>
    </td>
    
    <td>
      ✓
    </td>
    
    <td>
      
    </td>
    
    <td>
      
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        SystemLoginHandler
      </code>
    </td>
    
    <td>
      <code>
        kinotic-server-system
      </code>
    </td>
    
    <td>
      
    </td>
    
    <td>
      ✓
    </td>
    
    <td>
      
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ApplicationLoginHandler
      </code>
      
      , <code>
        ApplicationSocialLoginHandler
      </code>
    </td>
    
    <td>
      <code>
        kinotic-server-app
      </code>
    </td>
    
    <td>
      
    </td>
    
    <td>
      
    </td>
    
    <td>
      ✓
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        SessionEndpointHandler
      </code>
      
      , <code>
        OAuthServerHandler
      </code>
    </td>
    
    <td>
      <code>
        kinotic-domain
      </code>
    </td>
    
    <td>
      ✓
    </td>
    
    <td>
      ✓
    </td>
    
    <td>
      ✓
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        McpJsonRpcHandler
      </code>
    </td>
    
    <td>
      <code>
        kinotic-management-api
      </code>
    </td>
    
    <td>
      ✓
    </td>
    
    <td>
      ✓
    </td>
    
    <td>
      
    </td>
  </tr>
</tbody>
</table>

The app server serves every application at an API host of its own, `<organizationId>--<applicationId>` under `kinotic.domain.appApiBaseUrl` (see [Configuration](https://kinotic.ai/platform/configuration#application-api-hosts)). Its login and OAuth routes act for the application whose host the request is addressed to and answer `404` on any other host, and that host is the application's OAuth issuer. The one exception is a sign-in through a system social registration (`ApplicationSocialOidcConfiguration`): a provider returns the browser only to a redirect URI registered in advance, and the registration is shared by every application that offers it, so its `authorize` and `callback` routes are served at `kinotic.domain.appApiBaseUrl` itself, the shared host, and the verified sign-in is handed to the application's own host under a single-use code (`SocialLoginHandoffService`, an Ignite cache with a one-minute TTL) where the session is established. The code completes the sign-in only in the browser that started it: the start at the application's host put a value in that browser's session, the shared host carries it through its own session into the handoff, and the completion requires the browser to present it again, so a completion URL passed to another person signs no one in.

CORS for the server's UI origin is applied at the router root. A Vert.x `SessionHandler` covers every `/api/*` route (and the STOMP WebSocket path), so the same session cookie carries the OIDC roundtrip state and the post-login identity; the cookie is `HttpOnly`, `Secure`, `SameSite=Lax`, with a configurable timeout (`kinotic.api-gateway.session-timeout`).

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
      Unique provider keys of the system's org sign-up registrations, for the landing and sign-up pages' button rows and the Members page
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
        /api/auth/org/login/choices
      </code>
    </td>
    
    <td>
      <code>
        OrganizationLoginHandler
      </code>
    </td>
    
    <td>
      The ids of the organizations whose users the last landing-page sign-in the requesting page started found the identity bound to, for two minutes and until the page starts a login at an organization; <code>
        []
      </code>
      
       otherwise
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
        /api/auth/org/login/find
      </code>
    </td>
    
    <td>
      <code>
        OrganizationLoginHandler
      </code>
    </td>
    
    <td>
      <code>
        {turnstileSiteKey}
      </code>
      
      , the Cloudflare Turnstile site whose token the <code>
        POST
      </code>
      
       requires; <code>
        {}
      </code>
      
       where the deployment requires none
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
        /api/auth/org/login/find
      </code>
    </td>
    
    <td>
      <code>
        OrganizationLoginHandler
      </code>
    </td>
    
    <td>
      <code>
        {email, turnstileToken}
      </code>
      
      ; emails the address the login links of the organizations it is an enabled member of; <code>
        204
      </code>
      
       once the token verifies, whether or not the address belongs to an organization; <code>
        400
      </code>
      
       for a token missing or refused
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
      Begin a social-button login from the landing page, addressed to no organization; redirects to the IdP
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
        OrganizationOidcCallbackHandler
      </code>
    </td>
    
    <td>
      The one callback a system registration is registered with: a login signs in to the stashed organization, or, from the landing page, to the one organization whose user the identity is bound to; a sign-up, an invitation or a connect is completed by its own handler, as the flow's recorded purpose says
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
        OrganizationOidcCallbackHandler
      </code>
    </td>
    
    <td>
      The one callback an organization's own configuration is registered with: a login establishes the session, an invitation or a connect is completed by its own handler
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
        /api/auth/org/login/:orgId/providers
      </code>
    </td>
    
    <td>
      <code>
        OrganizationLoginHandler
      </code>
    </td>
    
    <td>
      The sign-in providers the organization's login offers, <code>
        [{id, type, name, provider}]
      </code>
      
      : its email and password (<code>
        PASSWORD
      </code>
      
      ) when it takes one, then its configurations (<code>
        OIDC
      </code>
      
      ); <code>
        404
      </code>
      
       for an unknown organization
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
        /api/auth/org/login/:orgId
      </code>
    </td>
    
    <td>
      <code>
        OrganizationLoginHandler
      </code>
    </td>
    
    <td>
      Email + password, scoped to the organization; on success establishes the browser session (204 + Set-Cookie); <code>
        401
      </code>
      
       for any failure, including an organization whose login takes no password
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        POST
      </code>
      
      , <code>
        GET
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/org/login/:orgId/start/:configId
      </code>
    </td>
    
    <td>
      <code>
        OrganizationLoginHandler
      </code>
    </td>
    
    <td>
      Begin a sign-in to the organization through a configuration its login offers; redirects to the IdP; <code>
        400
      </code>
      
       for one not offered
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
        /api/auth/org/methods/start/:configurationId
      </code>
    </td>
    
    <td>
      <code>
        OrganizationUserSignInMethodHandler
      </code>
    </td>
    
    <td>
      Connect a configuration to the signed-in organization user: a system registration, or an organization's own configuration when the user may sign in there (<code>
        403
      </code>
      
       otherwise); redirects to the IdP, whose callback binds the identity and 302s to <code>
        /account/profile?connected=<name>
      </code>
      
      , or <code>
        ?error=<code>
      </code>
      
       when it is bound to another user of the organization (<code>
        identity_in_use
      </code>
      
      ) or may not sign in (<code>
        invalid_token
      </code>
      
      , <code>
        email_not_verified
      </code>
      
      , which also names the provider's key in <code>
        provider
      </code>
      
      )
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
      Verify token + orgName + password; creates the Organization and the founder as its first user; establishes session
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
      Consume PendingSignUp; create the Organization and the founder as its first user, bound to the GitHub identity; establishes session
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
        /api/auth/system/login
      </code>
    </td>
    
    <td>
      <code>
        SystemLoginHandler
      </code>
    </td>
    
    <td>
      System user email + password; establishes session
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
        /api/auth/app/login/methods[?tenantId]
      </code>
    </td>
    
    <td>
      <code>
        ApplicationLoginHandler
      </code>
    </td>
    
    <td>
      The sign-in providers the application's login offers, <code>
        [{configurationId, type, name, provider, signUp}]
      </code>
      
      , its email and password (<code>
        PASSWORD
      </code>
      
      ) first, or the tenant's login from one of the tenant's sites or when <code>
        tenantId
      </code>
      
       names one; <code>
        403
      </code>
      
       from a page that is not one of the application's UIs or is a site of another tenant, <code>
        404
      </code>
      
       for an application that does not exist or a tenant it does not have
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
        /api/auth/app/login
      </code>
    </td>
    
    <td>
      <code>
        ApplicationLoginHandler
      </code>
    </td>
    
    <td>
      <code>
        {email, password[, tenantId]}
      </code>
      
      , scoped to the application, or to the tenant from one of the tenant's sites or when <code>
        tenantId
      </code>
      
       names one; establishes session; <code>
        403
      </code>
      
       from a page that is not one of the application's UIs or is a site of another tenant
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
        /api/auth/app/signup
      </code>
    </td>
    
    <td>
      <code>
        ApplicationSignUpHandler
      </code>
    </td>
    
    <td>
      <code>
        {email}
      </code>
      
      ; <code>
        202
      </code>
      
       for every email before anything is sent, then emails the link to <code>
        /signup/verify?token=
      </code>
      
       on the UI that sent it, or nothing to an email that has a user or whose link from an earlier request is still valid; <code>
        400
      </code>
      
       for an email with no <code>
        @
      </code>
      
      ; <code>
        403
      </code>
      
       while the application does not offer password sign-up, from a tenant's site, or from a page that is not one of its UIs; <code>
        404
      </code>
      
       for an application that does not exist
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
        /api/auth/app/signup/complete
      </code>
    </td>
    
    <td>
      <code>
        ApplicationSignUpHandler
      </code>
    </td>
    
    <td>
      <code>
        {token, password[, displayName]}
      </code>
      
      , the name at most 200 characters; creates the application's user with a tenant of its own and establishes the session (204); <code>
        400
      </code>
      
       for an unknown, spent or expired token, one sent for another application, or a missing password; <code>
        403
      </code>
      
       while the application no longer offers password sign-up, from a tenant's site, or from a page that is not one of its UIs; <code>
        404
      </code>
      
       for an application that does not exist; <code>
        409
      </code>
      
       when the email has a user
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
        /api/auth/app/login/oidc/start/:configId[?tenantId]
      </code>
    </td>
    
    <td>
      <code>
        ApplicationLoginHandler
      </code>
    </td>
    
    <td>
      Begin a sign-in through a configuration the login offers; <code>
        {redirect}
      </code>
      
       naming the IdP's authorization URL, or the shared host's <code>
        authorize
      </code>
      
       URL for a configuration using Kinotic's registration, carrying a <code>
        binding
      </code>
      
       the start put in the browser's session; <code>
        400
      </code>
      
       for one not offered, <code>
        403
      </code>
      
       from a page that is not one of the application's UIs
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
        /api/auth/app/login/oidc/callback/:configId
      </code>
    </td>
    
    <td>
      <code>
        ApplicationLoginHandler
      </code>
    </td>
    
    <td>
      App IdP returns here; establishes session for the scope the flow started for, 302 to the page that started the login; a failed callback 302s to that page's <code>
        /login
      </code>
      
       with the error, to the primary UI's when no flow session names one
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
        /api/auth/app/login/social/authorize
      </code>
    </td>
    
    <td>
      <code>
        ApplicationSocialLoginHandler
      </code>
    </td>
    
    <td>
      Shared host: begins the provider round-trip for <code>
        organizationId
      </code>
      
      , <code>
        applicationId
      </code>
      
      , <code>
        configurationId
      </code>
      
      , <code>
        tenantId
      </code>
      
       when the sign-in is to a tenant, the page at <code>
        origin
      </code>
      
      , which must be one of the application's UIs, and the <code>
        binding
      </code>
      
       the start put in the browser's session at the application's host; redirects to the IdP
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
        /api/auth/app/login/social/callback/:configId
      </code>
    </td>
    
    <td>
      <code>
        ApplicationSocialLoginHandler
      </code>
    </td>
    
    <td>
      Shared host: the IdP returns here, <code>
        configId
      </code>
      
       naming the system registration; verifies the identity, finds or creates the user through the application's configuration the flow started with, 302 to the application's host with a single-use code; a failed callback 302s to the starting page's <code>
        /login
      </code>
      
       with the error, <code>
        400
      </code>
      
       when no flow session names one
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
        /api/auth/app/login/social/complete/:code
      </code>
    </td>
    
    <td>
      <code>
        ApplicationSocialLoginHandler
      </code>
    </td>
    
    <td>
      Consumes the code; establishes session, 302 to the page that started the login; <code>
        handoff_invalid
      </code>
      
       for a spent, expired or foreign code, or one presented by a browser other than the one that started the sign-in
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
        McpJsonRpcHandler
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
        POST
      </code>
    </td>
    
    <td>
      <code>
        /mcp
      </code>
    </td>
    
    <td>
      <code>
        McpJsonRpcHandler
      </code>
    </td>
    
    <td>
      MCP JSON-RPC endpoint (see <a href="https://kinotic.ai/platform/mcp-tools">
        MCP Tools
      </a>
      
      )
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
      PKCE authorization-code flow; redirects to the <code>
        /oauth/consent
      </code>
      
       page on the server's UI, on the app server the application's primary UI (<code>
        400
      </code>
      
       until one is chosen)
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
        /api/auth/oauth/request/:requestId
      </code>
    </td>
    
    <td>
      <code>
        OAuthServerHandler
      </code>
    </td>
    
    <td>
      The request awaiting consent, for the consent page's signed-in user
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
        /api/auth/oauth/approve
      </code>
    </td>
    
    <td>
      <code>
        OAuthServerHandler
      </code>
    </td>
    
    <td>
      Approves the request as the consent page's signed-in user; answers the client redirect URL carrying the code
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
        /api/auth/oauth/deny
      </code>
    </td>
    
    <td>
      <code>
        OAuthServerHandler
      </code>
    </td>
    
    <td>
      Denies the request; answers the client redirect URL carrying <code>
        error=access_denied
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
        /api/auth/oauth/device_authorization
      </code>
    </td>
    
    <td>
      <code>
        DeviceAuthorizationHandler
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
        /api/auth/oauth/device/approve
      </code>
    </td>
    
    <td>
      <code>
        DeviceAuthorizationHandler
      </code>
    </td>
    
    <td>
      Approves the user code the <code>
        /device
      </code>
      
       page's signed-in user confirmed
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
      
       and <code>
        refresh_token
      </code>
      
       grants, plus every extension grant a module publishes — on the management server <code>
        DeviceAuthorizationHandler
      </code>
      
      's device-code grant
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
      Invitation details, the organization's live provider list (empty for an application invitation), and <code>
        localEnabled
      </code>
      
       — whether it may be accepted with a password, only while the login of its scope takes one (<a href="#password-sign-in">
        password sign-in
      </a>
      
      )
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
      Accept with a password, refused with <code>
        400
      </code>
      
       while the login of the invitation's scope takes none: sets it on the new user of the organization or application, or on a pending member as its first sign-in method; <code>
        409
      </code>
      
       when the invited email is a member someone already signs in as, who signs in instead, and <code>
        403
      </code>
      
       for a disabled member; an organization member's session is established, while an application member's response names the application's <code>
        primaryUiUrl
      </code>
      
       (<code>
        null
      </code>
      
       until the application designates a primary UI), where the accept page sends the browser to sign in
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
      Accept an organization invitation through a configuration the organization's login offers; redirects to the IdP, whose callback completes the acceptance: the identity's verified email must be the invited one; it signs in the organization's user it is bound to, binds a pending member as its first sign-in method, or creates and binds a new user of the organization, and establishes an organization member's session; <code>
        user_exists
      </code>
      
       when the email is a member someone already signs in as and the identity is not bound to it, who signs in instead; <code>
        user_disabled
      </code>
      
       when the user is disabled. An application invitation is accepted with a password
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
      
       if the session cookie holds a login for the calling page, else <code>
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
      Ends the calling page's login; the session ends once no page's login or sign-in in progress remains
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
        /api/github/webhook
      </code>
    </td>
    
    <td>
      <code>
        GitHubWebhookHandler
      </code>
    </td>
    
    <td>
      GitHub App webhook, verified against the App's webhook secret
    </td>
  </tr>
</tbody>
</table>

For the underlying architectural rationale (scope isolation, credential separation, the two kinds of `OidcConfiguration`), see [System Security](https://kinotic.ai/platform/system-security).


## Sitemap

See the full [sitemap](https://kinotic.ai/sitemap.md) for all pages.
