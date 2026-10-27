---
title: "System Security"
description: "The security model behind Kinotic OS sign-in — scopes, users, credentials, sign-in providers and authorized clients."
canonical_url: "https://kinotic.ai/platform/system-security"
---
# System Security

> The security model behind Kinotic OS sign-in — scopes, users, credentials, sign-in providers and authorized clients.

## Overview

This page explains *why* Kinotic's identity model is shaped the way it is. For how to create organizations, add members and configure providers, see [Organization Management](https://kinotic.ai/platform/organization-management); for the checks applied to every request, see [Defense in Depth](https://kinotic.ai/platform/defense-in-depth).

The model rests on a few rules:

- A person is a separate user in every scope they sign in to.
- A login authenticates only its own scope's users.
- Credentials are stored apart from users and never leave the server.
- Each sign-in provider belongs to exactly one login, and its owner can never reach anything of the system's.

## Users and scopes

There are three scopes, each with its own kind of user:

<table>
<thead>
  <tr>
    <th>
      Scope
    </th>
    
    <th>
      User
    </th>
    
    <th>
      Signs in at
    </th>
    
    <th>
      Sign-in methods
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      System
    </td>
    
    <td>
      <code>
        SystemParticipantIdentity
      </code>
      
       — runs the deployment
    </td>
    
    <td>
      The system console
    </td>
    
    <td>
      Password
    </td>
  </tr>
  
  <tr>
    <td>
      Organization
    </td>
    
    <td>
      <code>
        OrganizationParticipantIdentity
      </code>
      
       — a member of one organization
    </td>
    
    <td>
      The organization's login, <code>
        /login/<organizationId>
      </code>
    </td>
    
    <td>
      Password and providers
    </td>
  </tr>
  
  <tr>
    <td>
      Application
    </td>
    
    <td>
      <code>
        ApplicationParticipantIdentity
      </code>
      
       — a user of one application, in one of its tenants
    </td>
    
    <td>
      The application's (or tenant's) login
    </td>
    
    <td>
      Password and providers
    </td>
  </tr>
</tbody>
</table>

A system user, a member of organization A, a member of organization B and a user of an application, all with `jane@example.com`, are four unrelated users with separate passwords and provider identities. Signing in to one grants nothing at the others. Users are unique by email within their scope, and a user's id is derived from its type, scope and email, so two simultaneous creations of the same user contend for one row.

Three scopes rather than two, because they are real trust boundaries:

- An organization's developers should not automatically be users of every application they build.
- An application's users should not reach the organization's tooling.
- Applications of one organization can serve unrelated user populations.

A signed-in caller is a `Participant` whose type is its scope — `SystemParticipant`, `OrganizationParticipant` or `ApplicationParticipant` — so authority follows from the type, never from a header the client sends. `ParticipantIdentityService.resolveParticipant` is the one place an identity becomes a participant: a disabled identity, or one outside the requested scope, is refused with the same answer a wrong password gets.

## Authentication Methods

- **Password** — verified against a bcrypt hash kept in a separate `IdentityCredential` index, which no published service exposes. A login takes a password only while its scope has an enabled **Email and password** provider.
- **Provider** — an OIDC sign-in, whose token is validated for signature, issuer, audience and expiry; GitHub, which is OAuth 2.0 only, is identified through its user and email endpoints. A provider identity signs in the user it is bound to, never a user matched by email.

**Browsers** never hold a token. A login sets a session cookie, and the WebSocket upgrade reads the signed-in participant back out of the session. Each login in a session is bound to the page that made it (see [Defense in Depth](https://kinotic.ai/platform/defense-in-depth#browser-sessions)). Each server has its own cookie, `__Host-kinotic-<server>-session`, and its own session store, so a session one server issued means nothing to another. The `__Host-` prefix means a sibling host, such as a published site, cannot set or override it. The cookie is `SameSite=Lax` by default; `kinotic.apiGateway.sessionCookieSameSite: NONE` is for deployments whose sites and API live on unrelated domains.

**Other clients** present, at the upgrade, either a Kinotic access token (`Authorization: Bearer`) or a `clientId` and `clientSecret` — a user's email and password with scope headers, or a machine's id and secret. Each server admits only its own kind of caller, and anything else fails as invalid credentials:

<table>
<thead>
  <tr>
    <th>
      Server
    </th>
    
    <th>
      Admits
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      management
    </td>
    
    <td>
      Organization users, the clients they authorized, and the organization's machines other than application runtimes
    </td>
  </tr>
  
  <tr>
    <td>
      system
    </td>
    
    <td>
      System users, the clients they authorized, and system machines such as the vm-manager
    </td>
  </tr>
  
  <tr>
    <td>
      app
    </td>
    
    <td>
      Application users, the clients they authorized, the application's machines, and its organization's runtime workloads — on an application's API host, only that application's
    </td>
  </tr>
</tbody>
</table>

## Sign-in providers

Every provider is an `OidcConfiguration`, stored in one index under a `type` discriminator. The kinds differ by owner and by the one scope they are offered at:

<table>
<thead>
  <tr>
    <th>
      Kind
    </th>
    
    <th>
      Owner
    </th>
    
    <th>
      Offered at
    </th>
    
    <th>
      Client secret
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        OrgSignupOidcConfiguration
      </code>
      
       (GitHub)
    </td>
    
    <td>
      The system
    </td>
    
    <td>
      Every organization's login and sign-up
    </td>
    
    <td>
      Named by <code>
        secretNameRef
      </code>
      
       in the system's secret storage
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ApplicationSocialOidcConfiguration
      </code>
      
       (Google, Microsoft)
    </td>
    
    <td>
      The system
    </td>
    
    <td>
      Nowhere directly — an application's <code>
        MANAGED
      </code>
      
       configuration signs in through it
    </td>
    
    <td>
      As above
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        OrganizationOidcConfiguration
      </code>
    </td>
    
    <td>
      An organization
    </td>
    
    <td>
      Its own login
    </td>
    
    <td>
      Held in the organization's secret storage, keyed by the configuration
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ApplicationOidcConfiguration
      </code>
    </td>
    
    <td>
      An organization
    </td>
    
    <td>
      One application's login
    </td>
    
    <td>
      As above; unused while <code>
        MANAGED
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
      An organization
    </td>
    
    <td>
      One tenant's login
    </td>
    
    <td>
      As above
    </td>
  </tr>
</tbody>
</table>

**Why separate types instead of one with a flag.** Ownership and scope are what must never go wrong, so the types carry them: only a system registration has a `secretNameRef`, so nothing an organization writes can name a system secret; only an organization's configurations have an `organizationId`, and only application and tenant ones an `applicationId`, each required. Every write is held to its kind's owner and every lookup filters on the kind, so an organization's SSO can never appear as a global button and a system registration can never change an organization's settings.

**One configuration, one scope.** A configuration is offered only where its kind says. A tenant's login offers the tenant's configurations first, then the application's. To use one Okta application at both the organization's login and an application's, copy the configuration (`OwnedOidcConfigurationService.copy`); the copy has its own callback URL and its own copy of the secret. `OidcConfigurationService.findEnabledForScope` answers which providers a login page shows, and `findOffered` checks that a start or callback names one of them, so a page and its endpoints cannot disagree.

**Creating users.** A configuration's `createUserOnSignIn` lets a verified identity no user is bound to become a new user. It is refused on an organization's configuration — organizations add their members — and it creates a user only where that user gets a tenant: at an application's login while the system manages its tenants, or at a tenant's login through the tenant's own configuration. A configuration that admits every Microsoft directory creates a user only under an email the token proves (`email_verified` or `xms_edov`, or a personal account), since any directory's administrator can set its users' emails.

**Claiming pending users.** A user an administrator added is claimed by its first sign-in through a provider of its own login under the same email; at an organization, only through the organization's own provider. After that, only the binding identifies the user.

**Where the server connects.** An organization never enters a URL. It picks a provider type and gives its account there (an Entra directory id, an Okta domain); the server fills the type's issuer template on every save, and account patterns admit no scheme, port, path or foreign host. So every host the server fetches belongs to the provider. Identity endpoints are fetched without following redirects, and the deployment's egress policy blocks the node's metadata addresses.

**Where secrets live.** No secret is stored on a configuration row. A system registration's secret is fetched from the system's secret storage at sign-in time. An organization's secret is given on save, stored in the organization's own scope keyed by the configuration, and never read back through any service; `confidentialClient` only says whether one is held. A save without a secret keeps the one held.

## Sign-up

Organization sign-up always founds a new organization with the founder as its first user, whatever other organizations the person belongs to. People join an existing organization only by being added.

An application may let people sign up from its own UIs: through a provider with `createUserOnSignIn`, or with an email and a password verified by an emailed link — both only while the system manages its tenants, so each new user gets a tenant of their own. Where the application's administrators manage tenants, users are added by an administrator, who names the tenant, or created by a tenant's own provider at the tenant's login. From a browser, sign-up and sign-in are accepted only from a page that is one of the application's UIs. See [Authentication](https://kinotic.ai/apps/security/authentication#user-management).

## Design Decisions

### Credential separation

Password hashes and machine secret hashes live in `IdentityCredential`, keyed by identity id and exposed by no published service. Identities are returned by CRUD calls and shown in UIs; keeping secrets elsewhere means no such path can leak one.

### One identity hierarchy

System users, organization users, application users, delegates and machines share the sealed `ParticipantIdentity` hierarchy and one index, discriminated by `type`. Each subtype holds only its own fields — a system user has no scope fields — while the services that find, authenticate and resolve identities stay one set. Scoped identities store `organizationId` and `applicationId` as plain strings rather than references, which keeps queries to term filters and decouples users from the organization and application models.

### Bindings as separate rows

A provider identity is an `OidcBinding` row, keyed by `(oidcConfigId, subject)` plus the user's scope — never by email, which is mutable and can belong to different people at different providers. The key makes "one identity signs in at most one user per organization and per application" a constraint the store enforces, and a login reads the binding by id, so a new binding is visible at once. An application's users are bound by an identifier stable across client registrations (Entra's `<tid>/<oid>`, elsewhere `sub`), so switching a configuration between Kinotic's registration and the organization's own keeps them.

### Delegates

A client a person authorizes — the CLI through the device-code grant, or an MCP host through the authorization-code grant — acts as a `DelegatingParticipantIdentity`. It carries the exact scope of the login that approved it, is unique per owner, client and scope, and authenticates only with Kinotic tokens whose `sub` is its own id, so every request records which client acted.

- A grant can be redeemed only at the server (and, on the app server, the application host) that started it.
- Authentication re-reads the delegate on every request, and a delegate works only while its owner may act in its scope, so disabling a user or a delegate takes effect at once.
- Users list and revoke their clients on the **Connected apps** page (`DelegateService`) and manage their own profile and sign-in methods on **Profile** (`ProfileService`). A member sets a password there, and it counts as one of their sign-in methods, only while their organization's login takes an email and a password. Application users have neither page yet.

### Machine identities

A machine connects like any client, with its identity id as `clientId` and the secret issued at provisioning as `clientSecret`; its scope comes from its own identity. A `clientId` containing `@` is a user's email, so a user's credentials can never authenticate as a machine. The secret is shown once and stored only as a hash, so disabling the machine or rotating the secret takes effect at its next connection.

<table>
<thead>
  <tr>
    <th>
      <code>
        machineType
      </code>
    </th>
    
    <th>
      Scope
    </th>
    
    <th>
      Managed by
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        CLIENT
      </code>
    </td>
    
    <td>
      System — a daemon such as the vm-manager
    </td>
    
    <td>
      The system console, <strong>
        Members → Machines
      </strong>
      
       (<code>
        SystemMemberService
      </code>
      
      )
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        CLIENT
      </code>
    </td>
    
    <td>
      Application — an API client of one application
    </td>
    
    <td>
      Organization members, the application's <strong>
        Machines
      </strong>
      
       page (<code>
        MachineService
      </code>
      
      )
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        PROJECT_SYNC
      </code>
    </td>
    
    <td>
      Organization — a deployment's sync workload
    </td>
    
    <td>
      The deployment
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        APP_RUNTIME
      </code>
    </td>
    
    <td>
      Organization — a microservice's runtime workload
    </td>
    
    <td>
      The deployment
    </td>
  </tr>
</tbody>
</table>

### Organization ids

An organization's id is its slugified name and the label of its hosts, so no UUID appears in a URL. Reserved ids: anything beginning `kinotic`; `xn` (application host labels begin `<organizationId>--`, and DNS reserves `xn--`); `system`, the system's own owner id; the organization login's route segments `find`, `social`, `sso`, `providers` and `choices`; and the labels the platform holds under its domains.

### Client identity is a domain, not a string

MCP hosts identify themselves with an [OAuth Client ID Metadata Document](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-client-id-metadata-document-01): the `client_id` is an HTTPS URL whose JSON document registers the client's name and redirect URIs. A client must control that host to serve the document, so the consent page shows the `client_id`'s host beside the claimed name — no one can pose as `claude.ai` without owning it. The page also names the account the approval binds to, and warns a system user that a system-scoped approval reaches every organization's MCP tools.

The document is fetched on each authorization request (cached for `kinotic.domain.oauth.clientMetadataCacheTtl`) and validated as the draft requires: an `https` URL with a path and no fragment, userinfo or dot segments; a host that resolves to no special-use address; a `200` answer with no redirects, under `clientMetadataMaxBytes`; and a document that names its own URL, carries no secret, and supports the `none` token endpoint authentication method. Failures abort the request and are never cached.

`kinotic.domain.oauth.allowedClientIds` lists the documents that may start a flow; it is required, so every client is onboarded explicitly. The servers ship these:

<table>
<thead>
  <tr>
    <th>
      Client
    </th>
    
    <th>
      <code>
        client_id
      </code>
    </th>
    
    <th>
      Servers
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      ChatGPT custom MCP servers and plugins
    </td>
    
    <td>
      <code>
        https://chatgpt.com/oauth/client.json
      </code>
    </td>
    
    <td>
      management
    </td>
  </tr>
  
  <tr>
    <td>
      Codex
    </td>
    
    <td>
      <code>
        https://chatgpt.com/oauth/codex/client.json
      </code>
    </td>
    
    <td>
      management
    </td>
  </tr>
  
  <tr>
    <td>
      Grok connectors and Grok Bot
    </td>
    
    <td>
      <code>
        https://cursor.com/oauth/mcp-client.json
      </code>
    </td>
    
    <td>
      management
    </td>
  </tr>
  
  <tr>
    <td>
      Grok Build
    </td>
    
    <td>
      <code>
        https://kinotic.ai/oauth/grok-build-client.json
      </code>
    </td>
    
    <td>
      management
    </td>
  </tr>
  
  <tr>
    <td>
      Claude Code
    </td>
    
    <td>
      <code>
        https://claude.ai/oauth/claude-code-client-metadata
      </code>
    </td>
    
    <td>
      management, system, app
    </td>
  </tr>
  
  <tr>
    <td>
      Claude web, desktop, and mobile custom connectors
    </td>
    
    <td>
      <code>
        https://claude.ai/oauth/mcp-oauth-client-metadata
      </code>
    </td>
    
    <td>
      management, system, app
    </td>
  </tr>
</tbody>
</table>

The authorization server returns its issuer as `iss` on every callback (RFC 9207), so a client can share one document across Kinotic environments; loopback redirect ports may vary (RFC 8252). Grok Build has no document of its own, so Kinotic publishes one at `kinotic.ai`; a document under `kinotic.ai` may register only loopback redirect URIs, so a website change can never send authorization codes off the user's machine.

## Bootstrap

`kinotic-migration` runs before the servers start, applying each versioned SQL migration once. `V1__init.sql` creates the indices and seeds the system's registrations — GitHub as `ORG_SIGNUP`, Google and Microsoft as `APP_SOCIAL`. A migration with environment suffixes, such as `V2__system_identities.demonstration.test.e2e.sql`, runs only in those environments, which keeps fixtures out of production. `V6__system_sign_in.demonstration.sql` turns the three registrations off on a demonstration server, which holds none of their secrets, so its sign-up and sign-in pages offer an email and a password alone. Seeded rows name their secrets; the secrets themselves live in secret storage, so none passes through a migration or a config file.

## Network Security

- Every client connection uses TLS outside local development, terminated at each server's gateway.
- Each server's gateway port (management `58503`, system `58504`, app `58505` by default) serves the auth routes under `/api/*`, the WebSocket at `/api/v1`, and OAuth discovery under `/.well-known/`.

## Not yet covered

- **Authorization** — roles and policies; a session's roles list is empty today.
- **Groups.**
- **System-scope SSO** — system users sign in with a password; moving them to Microsoft Entra would add a new kind of system registration.
- **More sign-in methods for application users** — organization users connect providers from their Profile page; application users have no such page.


## Sitemap

See the full [sitemap](https://kinotic.ai/sitemap.md) for all pages.
