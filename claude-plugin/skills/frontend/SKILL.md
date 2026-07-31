---
name: frontend
description: >
  How a frontend or Node client talks to a Kinotic application: Kinotic.connect,
  browser session-cookie authentication, KinoticOsCredentialsAuthProvider and
  BearerTokenAuthProvider for Node clients, OIDC login, and invoking published
  services from the UI. Use when building a Kinotic app frontend or SPA, wiring up
  login or authentication, or connecting any client to a Kinotic server.
---

# Frontends and Clients

Every client — browser SPA, Node service, script — speaks the same STOMP-over-WebSocket
protocol to the Kinotic RPC gateway. There is no REST layer for application services;
the only REST endpoints are platform auth (`/api/auth/*`) and MCP (`/mcp`). Frontends
live in `packages/ui` of the project scaffold and may use any framework that builds to
static assets. Docs: <https://kinotic.ai/apps/security/authentication>.

Authentication happens at the **WebSocket upgrade**, and every authentication carries a
scope. Application users always authenticate at APPLICATION scope — both
`organizationId` and `applicationId` are supplied (directly or via JWT claims). Scope
isolation means a user of one application can never reach another application, even
with the same identity provider.

## Recipe 1 — Browser SPA (session cookie)

The browser logs in through the REST/OIDC flow first, which sets a session cookie; the
WebSocket upgrade is then authenticated by the cookie — no credentials in JS:

```typescript
import { Kinotic, ConnectionInfo } from '@kinotic-ai/core'

const connectionInfo = new ConnectionInfo()
connectionInfo.host = 'localhost'
connectionInfo.port = 58503
connectionInfo.useSSL = false   // true in production
await Kinotic.connect(connectionInfo)
```

## Recipe 2 — Node client with email/password

```typescript
import { Kinotic, ConnectionInfo, createAuthenticatedWebSocketFactory } from '@kinotic-ai/core'
import { KinoticOsCredentialsAuthProvider } from '@kinotic-ai/os-api'

const connectionInfo = new ConnectionInfo()
connectionInfo.host = 'localhost'
connectionInfo.port = 58503
connectionInfo.webSocketFactory = createAuthenticatedWebSocketFactory(
    connectionInfo,
    new KinoticOsCredentialsAuthProvider('user@example.com', 'password', 'my-organization', 'my-application')
)
await Kinotic.connect(connectionInfo)
```

The constructor is `KinoticOsCredentialsAuthProvider(clientId, clientSecret,
organizationId?, applicationId?)` — clientId is the user's email. Supplying both ids
selects APPLICATION scope.

## Recipe 3 — Node client with a Bearer token (OIDC)

```typescript
import { Kinotic, ConnectionInfo, createAuthenticatedWebSocketFactory, BearerTokenAuthProvider } from '@kinotic-ai/core'

const connectionInfo = new ConnectionInfo()
connectionInfo.host = 'localhost'
connectionInfo.port = 58503
connectionInfo.webSocketFactory = createAuthenticatedWebSocketFactory(
    connectionInfo,
    new BearerTokenAuthProvider(async () => await getToken())
)
await Kinotic.connect(connectionInfo)
```

The supplier runs before every (re)connect, so short-lived tokens refresh
automatically. A Kinotic-minted JWT already carries the organization/application
claims — no extra scope headers.

## OIDC login for your application's users

1. An admin enables OIDC configurations (Google, Microsoft, a corporate IdP) on the
   application. To see what is enabled, call the MCP tool
   `os-api.org.kinotic.os.api.services.ApplicationService.getOidcConfigurations`
   with `{"applicationId": "..."}`.
2. The frontend runs the OAuth flow; the REST login establishes the session cookie;
   then connect as in Recipe 1.
3. Application-scope users are **pre-created** by an administrator — an OIDC identity
   alone does not grant access. OIDC users are created with an email only; the subject
   links automatically on first login.

## Calling services from the frontend

Once connected, the frontend calls published services exactly like any client — the
hand-written proxy pattern from the services skill:

```typescript
const proxy = Kinotic.serviceProxy('app.my-organization.my-application.com.example.OrderService')
const orders = await proxy.invoke('findOpenOrders', [customerId])
proxy.invokeStream('watchOrder', [orderId]).subscribe(update => render(update))
```

Generated entity repositories (entities-and-persistence skill) also work in the
browser after `Kinotic.use(PersistencePlugin)` — data access needs no bespoke backend
endpoints.

## Further reading

- Authentication: <https://kinotic.ai/apps/security/authentication>
- Service proxies: <https://kinotic.ai/apps/services/service-proxies>
- Platform auth model: <https://kinotic.ai/platform/system-security>
