---
title: "Organization Management"
description: "How organizations are created, how their members are added and sign in, and how sign-in providers are configured."
canonical_url: "https://kinotic.ai/platform/organization-management"
---
# Organization Management

> How organizations are created, how their members are added and sign in, and how sign-in providers are configured.

## Overview

A Kinotic deployment hosts many organizations. Each has its own members, its own applications and, optionally, its own SSO. This page covers how an organization is created, how people join it and sign in, and how its sign-in providers are set up. For the design behind it, see [System Security](https://kinotic.ai/platform/system-security); the [Sign-In Design](https://kinotic.ai/platform/reference/sign-in-design) reference draws the flows.

System users sign in to the system console separately; see [System Authentication](#system-authentication).

## Concepts

<table>
<thead>
  <tr>
    <th>
      Concept
    </th>
    
    <th>
      What it is
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Organization
    </td>
    
    <td>
      A customer of the deployment. Its id is derived from its name and is the label of its hosts.
    </td>
  </tr>
  
  <tr>
    <td>
      Organization user
    </td>
    
    <td>
      A member of one organization (<code>
        OrganizationParticipantIdentity
      </code>
      
      ). A person in several organizations is a separate user of each, with its own sign-in methods.
    </td>
  </tr>
  
  <tr>
    <td>
      Application user
    </td>
    
    <td>
      A user of one application (<code>
        ApplicationParticipantIdentity
      </code>
      
      ), always in a tenant: one of its own when the system manages the application's tenants, otherwise one of the application's.
    </td>
  </tr>
  
  <tr>
    <td>
      Sign-in method
    </td>
    
    <td>
      A user's password, or a provider identity bound to it (<code>
        OidcBinding
      </code>
      
      ).
    </td>
  </tr>
  
  <tr>
    <td>
      Sign-in provider
    </td>
    
    <td>
      What a login offers: <strong>
        Email and password
      </strong>
      
       (<code>
        PasswordConfiguration
      </code>
      
      ) or an OIDC configuration. Each belongs to exactly one login — the organization's, an application's or a tenant's.
    </td>
  </tr>
  
  <tr>
    <td>
      Pending user
    </td>
    
    <td>
      A user an administrator added that nobody has signed in as yet. Its first sign-in method claims it.
    </td>
  </tr>
  
  <tr>
    <td>
      Invitation
    </td>
    
    <td>
      An emailed link (<code>
        PendingInvite
      </code>
      
      ) that lets the invited address claim its pending user.
    </td>
  </tr>
</tbody>
</table>

Provider configurations come in kinds, one per owner and scope:

<table>
<thead>
  <tr>
    <th>
      Kind
    </th>
    
    <th>
      Offered at
    </th>
    
    <th>
      What people see
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
      Every organization's login and sign-up; the system's own (GitHub)
    </td>
    
    <td>
      "Sign in with GitHub" / "Sign up with GitHub"
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        OrganizationOidcConfiguration
      </code>
    </td>
    
    <td>
      The organization's own login, <code>
        /login/<organizationId>
      </code>
    </td>
    
    <td>
      "Sign in with <provider>"
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ApplicationOidcConfiguration
      </code>
    </td>
    
    <td>
      One application's login, through the organization's own client (<code>
        OWN
      </code>
      
      ) or Kinotic's Google or Microsoft registration (<code>
        MANAGED
      </code>
      
      )
    </td>
    
    <td>
      "Continue with <name>" in the application's UI
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        TenantOidcConfiguration
      </code>
    </td>
    
    <td>
      One tenant's login, before the application's providers
    </td>
    
    <td>
      "Continue with <name>" on the tenant's sites
    </td>
  </tr>
</tbody>
</table>

Kinotic's Google and Microsoft registrations (`ApplicationSocialOidcConfiguration`) are never offered on their own; a `MANAGED` application configuration signs in through them, and switching it to `OWN` keeps its users.

## Creating an organization

Every sign-up founds a new organization with the founder as its first user. The portal's `/signup` page offers GitHub only, because every Kinotic project lives in a GitHub repository:

1. **Sign up with GitHub** starts the provider round-trip; GitHub must report a verified email.
2. The portal's `/register` page asks for the organization's name.
3. The organization and its founder are created, the founder bound to their GitHub identity, and the browser is signed in.
4. The page then offers **Continue to GitHub** to install the Kinotic GitHub App, which project creation needs.

Where the management server sets `kinotic.managementServer.passwordSignUp`, as the `demonstration` profile does, the `/signup` page offers an email and a password in place of GitHub (`/signup/email`): `POST /api/auth/org/signup` sends a verification link to `/signup/verify`, where the founder names the organization and sets a password, and the organization takes passwords from the start. Without the property the route answers `403`, and `GET /api/auth/org/signup/methods` tells the portal which page to show. A pending sign-up that has expired gives way to a new one.

When `kinotic.domain.email.enabled` is `false`, as on a demonstration server, nothing is sent: the sign-up's response carries its verification link as `verificationUrl`, which the portal opens at once, so the address is never proven to be the founder's, and asking again for an address with a pending sign-up returns the same link. Each link is also logged to the management server's console.

A UI other than the portal can install the GitHub App itself: it calls `startInstall` with the absolute URL of one of its own pages as `returnTo`, on an origin `kinotic.cors.allowedOriginPattern` names, and calls `completeInstall` when GitHub returns there.

## Members

Administrators add people on the **Members** page with **Add member**: an email, an optional name, and a switch, on by default, that emails an invitation (`MemberService.addOrganizationUser`).

- The person becomes a **pending** member at once.
- **With an invitation**, they claim it by setting a password (while the organization takes passwords) or by signing in through one of its providers under the invited address.
- **Without one**, they claim it on their first sign-in through one of the organization's own providers under that address, so the switch can only be off when the organization has such a provider.
- **Send invitation** sends a pending member their invitation again, or a first one. **Remove member** deletes the user with its sign-in methods, delegates and any unaccepted invitation.
- **Where email is disabled** (`kinotic.domain.email.enabled=false`, as on a demonstration server), each invitation's accept link is the administrator's to send on: after adding a member or user with an invitation, or sending one again, the page shows the link in a dialog to copy (`MemberService.findInvitationLink(email, applicationId)`). Anyone holding the link can accept the invitation as the invited address.

An application's users are added the same way on its **Users** page (`MemberService.addApplicationUser`); see [Authentication](https://kinotic.ai/apps/security/authentication#user-management).

An organization's own provider signs in only the members its administrators added: an address that is no member's gets `no_user`, and such a provider never creates users.

### Email templates

The portal's emails link to the portal. A server that embeds Kinotic's modules to serve its own UI passes an `EmailTemplate` in process instead — to `MemberInvitationService.addOrganizationUser`, `addApplicationUser` and `sendInvitation`, and to `SignUpService.initiateLocalSignUp`. No published service accepts one.

- `uiBaseUrl` is the UI whose `/invite/accept?token=` or `/signup/verify?token=` page the link opens: an absolute http(s) URL with no path suffix, query, fragment or trailing slash.
- `subject`, `htmlBody` and `textBody` are Handlebars sources. Write URLs with triple braces, `{{{acceptUrl}}}`, so they are not escaped.
- An invalid template fails the call with `IllegalArgumentException` before anything is saved.

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
      Invitation
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
      
       (application invitations only), <code>
        acceptUrl
      </code>
      
      , <code>
        expiresInDays
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      Email and password sign-up
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

An application with its own invitation template sends its content, still linking to the passed template's UI.

## Signing in

A login is addressed to one organization and signs in one of its users. The portal has two pages:

- **/login** — for someone whose organization is unknown. It offers **Sign in with GitHub**, a field for the organization, a **Don't know your organization?** link, and **Create Organization**. A browser that signed in before is sent straight to that organization's page.
- **/login/<organizationId>** — offers what that organization's login offers: its providers, then an email and password form while it takes passwords.

No route asks about an email before a method is chosen, and a failed password login always answers a generic `401`, so the pages reveal nothing about who has an account. A session acts in one organization; to use another, sign in at its page.

**GitHub from /login.** No organization is named, so the identity's bound users decide: one organization signs in directly; several show a choice of organizations, and the person signs in at the chosen one's page; none gives `no_user`.

**Starting from a link.** An organization's provider sign-in also starts from a plain `GET` of `/api/auth/org/login/<organizationId>/start/<configId>`, so a provider's application launcher can link straight to it.

**Errors** come back to the login page as `?error=`: `no_user` and `email_not_verified` (each with the provider in `provider`), `user_disabled`, and `multiple_organizations`.

### Finding one's organizations

**Don't know your organization?** emails the address a link to each organization it is an enabled member of (`POST /api/auth/org/login/find`). The answer is the same whether or not the address belongs to any, so nothing is revealed. Because it sends mail, it requires a Cloudflare Turnstile token, configured under `kinotic.managementServer.turnstile.*`; set `kinotic.managementServer.turnstile.enabled=false` where there is no Turnstile site, as in the e2e stack or on a demonstration server.

Where email is disabled, nothing is sent: the `POST` answers `200` with `{organizations: [{name, loginUrl}]}` and the landing page lists them, so anyone who asks learns which organizations the address belongs to. The `GET` reports this as `showLinksInUI`.

### Clients outside a browser

A browser's login sets a session cookie, which authenticates its WebSocket upgrade. Other clients authenticate at the upgrade itself:

<table>
<thead>
  <tr>
    <th>
      Client
    </th>
    
    <th>
      Presents
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Browser
    </td>
    
    <td>
      The session cookie
    </td>
  </tr>
  
  <tr>
    <td>
      Node, with credentials
    </td>
    
    <td>
      <code>
        clientId
      </code>
      
       and <code>
        clientSecret
      </code>
      
       with <code>
        organizationId
      </code>
      
       / <code>
        applicationId
      </code>
      
       scope headers (<code>
        BasicCredentialsResolver
      </code>
      
      , or <code>
        EnvCredentialsResolver
      </code>
      
       reading <code>
        KINOTIC_CLIENT_ID
      </code>
      
      , <code>
        KINOTIC_CLIENT_SECRET
      </code>
      
      , <code>
        KINOTIC_ORGANIZATION_ID
      </code>
      
      , <code>
        KINOTIC_APPLICATION_ID
      </code>
      
      )
    </td>
  </tr>
  
  <tr>
    <td>
      Node, with a token
    </td>
    
    <td>
      <code>
        Authorization: Bearer <jwt>
      </code>
      
       (<code>
        BearerCredentialsResolver
      </code>
      
      ). The CLI gets its token through the device-code grant and refreshes it before each connect.
    </td>
  </tr>
</tbody>
</table>

Each server admits only its own kind of user; see [System Security](https://kinotic.ai/platform/system-security#authentication-methods).

## Password sign-in

**Email and password** is a provider like any other: at most one per login, saved through `OwnedPasswordConfigurationService`, and enabled or disabled from the login's provider list.

- An organization has none until an administrator adds it, except one founded with the email and password sign-up.
- While an organization takes no password, its page shows no form, its password login answers `401`, and its invitations are accepted only through a provider.
- Disabling it is refused while an enabled member signs in with a password alone, and disabling an organization's own provider is refused while it is some member's only way in. The refusal names those members.
- An application user's password works where their own login takes one: their tenant's, else the application's.
- Password **sign-up** (`createUserOnSignIn`) exists only at an application's own login, while the system manages its tenants.

## Configuring SSO

An administrator manages the organization's providers under **Organization settings → Authentication providers**, or through `OwnedOidcConfigurationService`.

1. **Add provider**, then choose Google, Microsoft or Okta (or **Email and password**), and enter the button's name, the organization's account at the provider (the Entra directory id, never `common` or `organizations`; or the Okta domain), the client ID, and the client secret or **Public client**. The server derives the provider's URLs from the account, so no URL is entered.
2. Register the **callback URL** the form then shows, `<kinotic.managementApi.apiBaseUrl>/api/auth/org/login/sso/callback/<configId>`, with the provider. Logins, invitations and profile connections all return to it.
3. Editing keeps the provider and its scope fixed. Leaving the secret empty keeps the one held; **Make this a public client** deletes it.
4. The row menu enables, disables or deletes the provider, and **Copy to application** creates an application configuration from it, with its own callback URL to register as well.

The **Direct sign-in link** on each provider is the launcher URL described under [Signing in](#signing-in).

## Provider quirks

Providers differ in how they prove an email. `OAuth2Util` holds every difference, so no handler branches on the provider:

<table>
<thead>
  <tr>
    <th>
      Provider
    </th>
    
    <th>
      Identity
    </th>
    
    <th>
      Email verified by
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      GitHub
    </td>
    
    <td>
      OAuth 2.0 only: the user and email endpoints, called with the access token
    </td>
    
    <td>
      GitHub's <code>
        verified
      </code>
      
       flag on the address
    </td>
  </tr>
  
  <tr>
    <td>
      Google
    </td>
    
    <td>
      id_token, issuer <code>
        https://accounts.google.com
      </code>
    </td>
    
    <td>
      <code>
        email_verified
      </code>
      
       must be <code>
        true
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      Microsoft Entra
    </td>
    
    <td>
      id_token; a <code>
        common
      </code>
      
       / <code>
        organizations
      </code>
      
       registration validates the issuer against the token's <code>
        tid
      </code>
    </td>
    
    <td>
      Presence of the email. For an application's sign-up through a registration admitting every directory, only <code>
        email_verified
      </code>
      
       or <code>
        xms_edov
      </code>
      
       true, or a personal account
    </td>
  </tr>
  
  <tr>
    <td>
      Okta
    </td>
    
    <td>
      id_token, issuer derived from the Okta domain
    </td>
    
    <td>
      <code>
        email_verified
      </code>
      
       from the userinfo endpoint
    </td>
  </tr>
  
  <tr>
    <td>
      Apple
    </td>
    
    <td>
      id_token
    </td>
    
    <td>
      Presence; the email arrives on the first sign-in only
    </td>
  </tr>
</tbody>
</table>

When an id_token has an email but no `email_verified` (other than Entra or Apple), the userinfo endpoint supplies it; its `sub` must match. An application's users are bound by an identifier stable across client registrations — Google's `sub`, Entra's `<tid>/<oid>` — so switching a configuration between `MANAGED` and `OWN` keeps them.

## System Authentication

System users sign in to the system console with an email and password (`POST /api/auth/system/login`). There is no sign-up, SSO or social path for them; they are created by migration fixtures and listed read-only under **Members → Users**. The system login authenticates only system users, and every organization login only organization users, so the same email signs in to each with that scope's own user.

## Endpoint Reference

Each server mounts the routes of the modules it loads, on its own gateway port (management `58503`, system `58504`, app `58505` by default). Every `/api/*` route shares a session cookie, `__Host-kinotic-<server>-session` (`HttpOnly`, `Secure`, `SameSite` from `kinotic.apiGateway.sessionCookieSameSite`, timeout `kinotic.apiGateway.sessionTimeout`, 30 minutes by default).

The app server answers at each application's own API host, `<organizationId>--<applicationId>` under `kinotic.domain.appApiBaseUrl`. Sign-ins through Kinotic's shared Google and Microsoft registrations return to that base host instead and are handed to the application's host under a single-use code that only the starting browser can redeem.

**Organization sign-in and sign-up** (management server)

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
      The system's sign-up provider keys, for the landing and sign-up buttons
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
      What the organization's login offers, <code>
        [{id, type, name, provider}]
      </code>
      
      ; <code>
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
      Email and password; <code>
        204
      </code>
      
       with the session cookie, <code>
        400
      </code>
      
       without both fields, otherwise <code>
        401
      </code>
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
      Start a sign-in through a provider the organization offers
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
      Start a GitHub sign-in from the landing page
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
      The organizations a landing-page sign-in found, for two minutes
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        GET
      </code>
      
      , <code>
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/org/login/find
      </code>
    </td>
    
    <td>
      Turnstile site key and <code>
        showLinksInUI
      </code>
      
      ; email the address its organizations, or answer with them where email is disabled
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
      Return from a system registration (login, sign-up, invitation or connect)
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
      Return from an organization's own provider
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
      Connect a provider to the signed-in user from the Profile page
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
        /api/auth/org/signup/methods
      </code>
    </td>
    
    <td>
      <code>
        {password}
      </code>
      
      : whether the sign-up page offers an email and a password
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
      
      , <code>
        /signup/complete
      </code>
    </td>
    
    <td>
      Email and password sign-up, where <code>
        kinotic.managementServer.passwordSignUp
      </code>
      
       is set (<code>
        403
      </code>
      
       otherwise); the response carries <code>
        verificationUrl
      </code>
      
       where email is disabled
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
      
      , <code>
        /signup/social/complete
      </code>
    </td>
    
    <td>
      Provider sign-up
    </td>
  </tr>
</tbody>
</table>

**Invitations** (management server)

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
        /api/auth/invite/details
      </code>
    </td>
    
    <td>
      The invitation, the providers of the login it is accepted at, <code>
        localEnabled
      </code>
      
       (whether a password is accepted), and for an application <code>
        signInUrl
      </code>
      
      , its primary UI
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
      Accept with a password: <code>
        400
      </code>
      
       while the login takes none, <code>
        409
      </code>
      
       once someone signs in as the user, <code>
        403
      </code>
      
       for a disabled user
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
      Accept an organization invitation through one of its providers; the verified email must be the invited one
    </td>
  </tr>
</tbody>
</table>

**Application sign-in** (app server)

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
        /api/auth/app/login/methods[?tenantId]
      </code>
    </td>
    
    <td>
      The login's providers, <code>
        [{configurationId, type, name, provider, signUp}]
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
        /api/auth/app/login
      </code>
    </td>
    
    <td>
      Email and password, <code>
        {email, password[, tenantId]}
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
        /api/auth/app/login/oidc/start/:configId[?tenantId]
      </code>
    </td>
    
    <td>
      Start a provider sign-in; answers <code>
        {redirect}
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
        /api/auth/app/login/oidc/callback/:configId
      </code>
    </td>
    
    <td>
      Return from the application's own provider
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
      
      , <code>
        /social/callback/:configId
      </code>
      
      , <code>
        /social/complete/:code
      </code>
    </td>
    
    <td>
      The shared-registration round-trip and its handoff
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
      
      , <code>
        /signup/complete
      </code>
    </td>
    
    <td>
      Email and password sign-up, where offered
    </td>
  </tr>
</tbody>
</table>

**Every server**

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
        /api/auth/system/login
      </code>
    </td>
    
    <td>
      System user email and password (system server)
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
        204
      </code>
      
       while the calling page is signed in, else <code>
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
      End the calling page's login
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
      
      , <code>
        /.well-known/oauth-protected-resource[/mcp]
      </code>
    </td>
    
    <td>
      OAuth metadata
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        GET
      </code>
      
      , <code>
        POST
      </code>
    </td>
    
    <td>
      <code>
        /api/auth/oauth/authorize
      </code>
      
      , <code>
        /request/:requestId
      </code>
      
      , <code>
        /approve
      </code>
      
      , <code>
        /deny
      </code>
      
      , <code>
        /token
      </code>
    </td>
    
    <td>
      OAuth authorization-code flow with consent, and the token endpoint
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
      
      , <code>
        /device/approve
      </code>
    </td>
    
    <td>
      Device-code grant for the CLI (management server)
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
      MCP endpoint (management and system servers; see <a href="https://kinotic.ai/platform/mcp-tools">
        MCP Tools
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
        /api/github/webhook
      </code>
    </td>
    
    <td>
      GitHub App webhook (management server)
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
        /api/github/install/callback
      </code>
    </td>
    
    <td>
      GitHub App install return, forwarded to the UI that started it
    </td>
  </tr>
</tbody>
</table>


## Sitemap

See the full [sitemap](https://kinotic.ai/sitemap.md) for all pages.
