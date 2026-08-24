# Authentication

> Authentication methods and identity management in Kinotic.

## Overview

Kinotic supports multiple authentication methods to secure your applications. Every connection to a Kinotic server requires authentication — the platform identifies the caller and establishes a session before any service calls are allowed.

### Authentication Methods

- **Email and Password** — Built-in user management with secure credential storage. Ideal for getting started quickly or for applications that manage their own user base.
- **OIDC Providers** — Connect any standard OpenID Connect provider including Google, Microsoft, Okta, and others. Your organization admin enables OIDC configurations for your application — including platform-provided social providers pre-registered with Kinotic OS.

This page covers what an application developer needs to know. For the platform-level model behind these mechanisms — scope isolation, the `OidcConfiguration` entity, signup and login flows — see [System Security](/platform/system-security) and [Organization Management](/platform/organization-management).

### Scoped Authentication

Authentication happens at the **WebSocket upgrade (handshake)**, not in a STOMP CONNECT frame. Every authentication carries a **scope** that identifies which layer the caller is authenticating against. Scope is structural — it is determined by which of two optional ids are supplied:

<table>
<thead>
  <tr>
    <th>
      <code>
        organizationId
      </code>
    </th>
    
    <th>
      <code>
        applicationId
      </code>
    </th>
    
    <th>
      Resulting scope
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      absent
    </td>
    
    <td>
      absent
    </td>
    
    <td>
      <code>
        SYSTEM
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      present
    </td>
    
    <td>
      absent
    </td>
    
    <td>
      <code>
        ORGANIZATION
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      present
    </td>
    
    <td>
      present
    </td>
    
    <td>
      <code>
        APPLICATION
      </code>
    </td>
  </tr>
</tbody>
</table>

For application developers this is always the `APPLICATION` scope, so both ids are supplied: your organization's id plus your application's id. Scope isolation ensures users are authenticated against the correct user pool — a user in Application A cannot access Application B, even if both applications use the same OIDC provider.

## Connecting with Email and Password

In Node, pass a `BasicCredentialsResolver` carrying the credential and scope values (`clientId`, `clientSecret`, `organizationId`, `applicationId`); they are presented as headers on the WebSocket upgrade:

```typescript
import { BasicCredentialsResolver, Kinotic } from '@kinotic-ai/core'
import { ensureNodeWebSocket } from '@kinotic-ai/core/node'

ensureNodeWebSocket()
await Kinotic.connect({
    credentials: new BasicCredentialsResolver('user@example.com', 'password', 'my-organization', 'my-application')
})
```

The constructor is `BasicCredentialsResolver(clientId, clientSecret, organizationId?, applicationId?)`. The `clientId` is your user's email and `clientSecret` is the password — or a machine identity's id and provisioned secret. The platform looks up the user by email within your application's scope, verifies the password against a securely stored bcrypt hash, and establishes a session.

With no `credentials` at all, `Kinotic.connect()` resolves through the default chain: the `KINOTIC_CLIENT_ID` / `KINOTIC_CLIENT_SECRET` (plus optional `KINOTIC_ORGANIZATION_ID` / `KINOTIC_APPLICATION_ID`) environment variables, then the browser session. Server settings resolve the same way — the `server` option, the `KINOTIC_SERVER_HOST/PORT/USE_SSL` variables, the page's own location in a browser, then `https://api.kinotic.ai`. To pin the server explicitly, pass it alongside the credentials:

```typescript
await Kinotic.connect({
    server: { host: 'kinotic.example.com', useSSL: true },
    credentials: new BasicCredentialsResolver('user@example.com', 'password', 'my-organization', 'my-application')
})
```

Absent `server` fields fall through to the environment — `{ host }` alone means no TLS and the gateway port `58503`.

## Connecting with OIDC

For token-based authentication, pass a Bearer token via `BearerCredentialsResolver`. The resolver sends `Authorization: Bearer <token>` on every (re)connect; pass a supplier function to refresh a short-lived token before each connect:

```typescript
import { BearerCredentialsResolver, Kinotic } from '@kinotic-ai/core'
import { ensureNodeWebSocket } from '@kinotic-ai/core/node'

ensureNodeWebSocket()
await Kinotic.connect({
    credentials: new BearerCredentialsResolver(async () => await getToken())
})
```

A Kinotic-minted JWT already carries the `organizationId` / `applicationId` claims, so no scope headers are needed alongside it. The platform validates the JWT against the OIDC configurations enabled for your application, matches the token's email to a pre-existing user in your application's scope, and establishes a session.

### Browser applications

A browser SPA does **not** supply credentials. It logs in through the OIDC/REST flow, which establishes a session cookie, then connects with nothing — the default resolution finds the session, and the server settings come from the page's own location:

```typescript
import { Kinotic } from '@kinotic-ai/core'

await Kinotic.connect()
```

### How OIDC Works with Your Application

1. Your platform administrator enables one or more OIDC configurations for your application (e.g., Google, Microsoft)
2. Your frontend initiates the OAuth flow with the provider — the user sees the provider's consent screen
3. The provider returns a JWT to your frontend
4. Your frontend connects to Kinotic — in the browser via the session cookie established by the REST login; the Kinotic-minted JWT carries the `organizationId` / `applicationId` claims that identify the scope
5. The platform validates the token, looks up the user, and creates a session

### Reusable Providers

Kinotic OS registers once with providers like Google and Microsoft. The platform operator provisions these as `OidcConfiguration` entities; your organization admin enables them for your application by adding the configuration id to your application's `oidcConfigurationIds`. End users see "Kinotic OS" on the consent screen, and your application benefits without any provider registration.

The same `OidcConfiguration` shape is used for an organization's enterprise SSO (e.g. a corporate Okta instance) — see [System Security](/platform/system-security) for how the platform-vs-org distinction is captured by reference rather than by a flag on the entity.

## User Management

Application-scope users are **pre-created** by an administrator before they can authenticate. This is a deliberate security design — having a valid Google account does not automatically grant access to your application. Self-service signup applies to the [Organization scope](/platform/organization-management) (the admin who runs the org); the same data model supports application-scope self-signup, but enabling it is admin-controlled.

Your organization administrator manages your application's users from the organization's **Members** page:

- **Local users** are created with an email and password
- **OIDC users** are created with an email only — the OIDC subject is linked automatically on first login

For details on user and OIDC configuration management, see the [Organization Management](/platform/organization-management) platform guide.
