# Phase Prompt

I would like you to break this work into a series of phases where ideally each phase changes around 10 files. After each phase I would like you to wait for me to review the code before proceeding with the next phase. Once I give approval, you can move onto the next phase. No phase may rewrite, refactor, or restructure what an earlier phase produced, and if a later phase would force that, the earlier phase drew its boundary wrong: say so and re-plan rather than churning code.


### IamUser refactor

* Fix DefaultPendingRegistrationService.applyPendingScope (Should not be needed)
* Review login handlers and KinoticSecurityService in detail.
* Verify DefaultOpenAPIService.addNamedQueryPathItems (call looks up named queries without org)
* No OpenAPI routes have the org in the path.

### App auth return-to-application redirect

* App OIDC login callbacks (`ApplicationLoginHandler` → `redirectSuccess`) currently land app
  end-users on the web app `/` because no per-app frontend URL exists in the model. The
  return-to-application redirect belongs with the per-app URL / distributable-components work
  already deferred. Same gap applies to where app-invite emails link (today: the hosted
  `/invite/accept` page, which is the intended fallback).

  
### JWT audience check (dropped, needs to come back)

Tokens are still minted with the correct audience — `KinoticAudience`, the `aud` stamping in
`KinoticJwtIssuer.sign`, and the `RefreshToken.audience` lineage pinning all remain, so each grant
records the surface it serves and rotation re-stamps the same one. What was dropped is the
verification: `KinoticJwtIssuer.authenticate` no longer compares the claim, so no entry point cares
what a token says it was issued for.

| `aud` | `KinoticAudience` | Issued by | Intended for |
|---|---|---|---|
| `kinotic` | `PUBLISHED_SERVICES` | the device-code grant (the CLI) | the STOMP WebSocket handshake |
| `kinotic-mcp` | `MCP_TOOLS` | the authorization-code grant (MCP hosts) | `POST /mcp` |

This table and the scheme around it are deliberately not on the website. Publishing which surface a
token names, while nothing verifies the name, describes the gap precisely enough to be worth using.
It belongs back in `website/content/02.platform/05.system-security.md` once the check is enforced,
where it reads as a security property rather than an inventory.

Only the check was dropped because enforcing it requires the entry point to tell
`KinoticSecurityService` which surface it serves, and the only channel for that was the
`SecurityService` contract — a change worth designing properly rather than rushing. The contract
stays `authenticate(Map<String, String>)`.

Keeping the minting side intact means restoring the check needs no data migration: refresh lineages
issued in the meantime already carry the right audience, and access tokens already carry the right
claim. Turning the comparison back on is the whole change.

**What this costs us right now.** Every Kinotic-minted token is accepted at every Kinotic entry
point. The CLI's device-grant token calls MCP tools. An MCP host's authorization-code token opens a
STOMP WebSocket and reaches the full published-service RPC surface. The two grants have very
different consent stories — the device grant is a developer typing a code into their own terminal,
the authorization-code grant is an end user clicking approve on a consent screen for a third-party
MCP host — and a token from either now carries the union of both surfaces' reach. A user who
approves a Claude connector for read-only tool access has handed it something that can also open a
STOMP connection as them.

**Why an audience and not scopes.** Scopes bound what a token may do; the audience bounds where it
may be presented. They are different failure modes. Even with per-tool scopes, a token presented at
a surface it was never issued for is a category error, and `aud` is the standard, one-comparison way
to reject it. It also fails closed against a class of bug we cannot otherwise exclude: any future
entry point that accepts a bearer token inherits every existing token unless it declares what it
accepts. The `aud` check is what makes adding an endpoint safe by default.

**The other thing it bought us.** A non-Kinotic JWT — including a valid token from a trusted IdP —
used to be rejected because it carried neither of our audience values. That is now only rejected by
the signature check. Signature verification is the right guard, but the audience was a second,
cheaper one that did not depend on key hygiene being perfect.

**What restoring the check needs.** The blocker is getting the audience from the entry point to
`KinoticSecurityService` without putting a JWT concern in `kinotic-core`, since core is used without
the OS and authenticates for one reason only. The direction we converged on:

- `SecurityService.authenticate(AuthenticationContext, Map<String, String>)` — the map stays a
  separate parameter and the new overload gets a `default` implementation delegating to the existing
  one-arg method, so existing implementations (including customers reusing our security service)
  compile and run untouched until they opt in.
- `AuthenticationContext` is a class rather than a parameter so later inputs to the decision are
  added without breaking the contract again.
- The audience is carried as an opaque `String authenticationFor` — an open set, since customers
  mount their own entry points and core must not own the catalog. Making the label *be* the required
  `aud` value keeps the mapping an identity comparison, so an unrecognized label matches no token and
  fails closed with no default branch to get wrong.

**The trap to avoid when restoring it.** A `default` method that ignores `authenticationFor` and
delegates to the one-arg method is a silent fail-open: an implementation that has not opted in will
accept a token minted for any surface. That is fine for `TestSecurityService`, which does no
token-based auth. It is not fine for an entry point that assumes the label is honored — MCP would go
back to accepting CLI tokens while looking like it enforces something. Entry points that depend on
the label need to not silently accept an implementation that drops it.

`kinotic-js/e2e-tests/test/native/OAuthMcp.test.ts` currently asserts a device-grant token gets
`200` from `POST /mcp`. That assertion is the marker: restoring the check should turn it back into a
`401` with a `WWW-Authenticate` challenge.

### Self-service OIDC provisioning into an application scope

`OidcConfiguration` used to carry a `provisioningMode` field (`UserProvisioningMode` —
`AUTO` / `REGISTRATION_REQUIRED` / `INVITE_ONLY`) for this, but nothing ever read it, so an admin
setting it got silence rather than the behaviour the name promised. Removed along with
`rolesClaimPath`, `additionalScopes`, and the four unread redirect/domain fields.

If we want it, the pieces are: a policy on the configuration, `completeOidcLogin` consulting it
instead of always refusing an unknown subject, and the per-application admin UI to set it. Today an
application's users arrive by invitation or admin creation only.

### Outstanding requests count in a system UI

Nothing that dispatches a request and waits for a reply bounds how long it waits. `McpToolInvoker`
holds a `Promise` in `pendingCalls` until the reply arrives, and `DefaultRpcServiceProxyHandle` does
the same with `responseMap`. `sendWithAck` only acks receipt, so a service that accepts a request and
then dies leaves the entry there for the life of the process, and for MCP that also means the HTTP
response is never written.

A global timeout is the wrong answer — we do not know how long a published service is entitled to
take, and any number we pick is wrong for somebody. Instead expose the size of those maps as a
counted metric in a system UI, per node. Then we can see whether outstanding requests actually
accumulate in practice, and how, before deciding whether anything needs bounding at all. If the
count is flat under real load the whole concern is theoretical; if it climbs monotonically, the shape
of the climb tells us what the right mechanism is.

### OAuth base URL split (`issuerBaseUrl`)

`kinotic.domain.oauth.issuerBaseUrl` exists because two different parties reach the gateway and,
today, they can reach it at different URLs. A browser follows the OIDC `redirect_uri`s built from
`apiBaseUrl`; an MCP host's backend calls the token endpoint built from `issuerBaseUrl`, having never
been near the browser. A development gateway on `localhost` whose OAuth surface is tunnelled is the
case that forced the split: one value cannot be both browser-local and internet-reachable.

The cost is the FIXMEs — five places now know the OAuth surface has its own base URL
(`OAuthProperties.issuerBaseUrl`, `DomainProperties.resolveIssuerBaseUrl`,
`AuthEndpointSupport.issuerUrl`, `OAuthServerHandler.issuer`, `McpJsonRpcHandler.armDiscoveryChallenge`).
Nothing enforces the choice: every externally reached URL added from here on has to pick `issuerUrl`
over `absoluteUrl`, and picking wrong fails only in the tunnelled topology, which is exactly the one
nobody runs in CI.

**The split disappears if the browser and the internet reach the gateway at one URL.** That is a
topology decision, not a code one, and it is the reason to keep these markers rather than settle.

**Option A — keep the split.** No infrastructure change. Development works today; production sets
nothing and falls back. Carries the shotgun surgery indefinitely.

**Option B — dev-server proxy.** Have the vite dev server proxy `/api`, `/mcp`, `/.well-known` and
the `/v1` STOMP upgrade to the gateway, and tunnel the dev server rather than the gateway. One origin
in development, so `issuerBaseUrl` is never needed. Costs a second IdP callback registration (the
tunnel host) and leaves the development topology different from production — the proxy hop exists
nowhere else — so it removes the property without removing the underlying asymmetry.

**Option C — one origin everywhere, via path-based routing at the ingress.** SPA and API behind a
single hostname, `/api` and `/v1` routed to kinotic-server and everything else to the SPA. Then
`appBaseUrl`, `apiBaseUrl` and `issuerBaseUrl` collapse to one value and all five FIXMEs delete. On
Azure this means a layer-7 front end (Application Gateway or Front Door) where today's LoadBalancer
is layer 4 — a real, recurring cost, which is the thing to price before choosing this.

**On making the session cookie stricter.** Worth recording because it is the natural next thought and
it does not work: `SameSite=Strict` is not available to us at any topology, single origin or not.
Strict withholds the cookie on *every* cross-site request including top-level navigations, and the
IdP returning the browser to `/api/auth/org/login/social/callback/:configId` is exactly that. The
handler needs `OIDC_FLOW_SESSION_KEY` from the session to validate `state`; without the cookie it
finds nothing and every social login fails with `state_mismatch`. `Lax` is the floor for as long as
we terminate an OIDC redirect flow — a single origin buys us the base-URL collapse, not a stricter
cookie. Getting to Strict would mean the callback landing on a bounce page that re-enters same-site
before the session is read, which is a different design.

### Evict sessions and kill connections when an identity is disabled or deleted

Disabling or deleting a `ParticipantIdentity` writes the row and stops. Everything already
authenticated keeps working:

* `DefaultMemberService.setMemberEnabled` / `removeMember` and
  `DefaultMachineService.setMachineEnabled` / `removeMachine` only save or delete the identity.
  `DefaultDelegateService.revokeDelegate` is the one path that also calls
  `refreshTokenService.revokeAllFor` — it is the model the other four should follow.
* Access tokens live an hour (`AuthEndpointSupport.ACCESS_TOKEN_TTL_SECONDS`) and nothing
  re-reads the identity while one is valid, so REST and MCP keep serving a deleted identity
  until its token expires.
* `EndpointConnectionHandler.handshake` returns success without calling
  `securityService.authenticate` when the session already carries a `ConnectedInfo` with a
  participant. A live session therefore outlives both the `enabled` flag and the row itself —
  reconnecting is enough, the identity is never looked up again.
* An open STOMP connection is never revisited at all. It authenticated once at handshake and
  the `Participant` in `ConnectedInfo` is what authorization uses from then on.

What the feature needs:

* Revocation on the disable and delete paths, so a refresh token cannot mint a new access token.
* The handshake to re-check the identity when it resumes from a session, or to stop
  short-circuiting.
* A way to find an identity's sessions. The store is a Vert.x `SessionStore` —
  `ClusteredSessionStore` when clustered (`ApiGatewayConfiguration.sessionStore`) — keyed by
  session id only, so an identity → session-id index has to exist before anything can evict by
  identity.
* A registry of live connections. `DefaultStompServerHandler` constructs one
  `EndpointConnectionHandler` per connection and registers it nowhere, so today nothing can
  enumerate or close the connections belonging to an identity.
* Cluster-wide reach. A connection terminates on the gateway node that owns it, so eviction is a
  broadcast, not a local map lookup.

The access-token window is a separate decision from the connection kill — a shorter TTL and a
revocation check at the entry points are two different answers and only the second closes it.

### Alert on `OutOfDirectMemoryError` in the gateway logs

Set up an alert that fires when `io.netty.util.internal.OutOfDirectMemoryError` appears in a
kinotic-server log. It is logged at ERROR by `DefaultStompServerHandler` as "Client Caused
Exception", so the string is there to match on without any code change.

It needs an alert because nothing else will tell us. Verified by exhausting a gateway's direct
memory on purpose: Netty refuses the allocation inside the frame decoder and the connection is
closed with **no STOMP ERROR frame** — `clientCausedException(t, false)` calls `close()` — so the
client sees a bare socket reset and simply reconnects. The JVM does **not** exit, even though
`OutOfDirectMemoryError` extends `OutOfMemoryError` and the image sets `-XX:+ExitOnOutOfMemoryError`
(that flag hooks the JVM's own OOM path, not an error thrown by library code — tested with the flag
on, the process stayed up). `/health` keeps returning 204 throughout.

Worse, the damage is not confined to whoever caused it: direct memory is one process-wide budget, so
a connection that was idle and sending nothing gets dropped too — measured. So the observable symptom
is a healthy-looking pod quietly dropping arbitrary connections while clients reconnect in a loop,
which is exactly what sent us chasing a client bug at the start of this. Pair the log alert with a
gauge on `jvm.buffer.memory.used{pool=direct}` approaching `MaxDirectMemorySize`.

### Re-measure gateway sizing with `preferNativeTransport` on

`KinoticVertxConfig.vertx` never calls `setPreferNativeTransport(true)`, so Vert.x runs on NIO. The
epoll and kqueue natives are now on the classpath for both x86 and aarch64, so turning it on is one
line — but every direct-memory number in `docs/future-prompts/Gateway memory sizing validation.md`
was measured against the NIO allocator. The finding that direct memory is flat in connection count
(byte-identical 9,992 KB at 1k/5k/10k) rests on Netty's magazines being bounded by
`MAX_STRIPES = availableProcessors() * 2`; epoll allocates on its own path, so both that finding and
the `-XX:MaxDirectMemorySize=512m` in `org.kinotic.java-application-conventions.gradle` may not
describe the server once the flag is on.

Re-derive the direct-memory and heap figures the way that doc describes, with the flag on, at the
same 1k/5k/10k connection counts. Then update its table, its "Direct memory does not scale with
connections" finding and the sizing artifact, and decide from the numbers whether the flag stays.

Assert the transport actually took, do not infer it from the flag: when the native is unavailable
`VertxBootstrapImpl.instantiateVertx` swaps in `NioTransport.INSTANCE` and only stashes the cause,
so a run with the flag set and no native looks identical to a run without it. Check it with
`Transport.EPOLL.available()`.

### Sign the session cookie (`SessionHandler.setSigningSecret`)

Deferred until the platform-secret layout in Azure is reworked — this needs one more secret and it
is not worth adding to the current arrangement.

`SessionHandler` never gets a signing secret today, so a session id is accepted or rejected purely
by whether the store has it. Signing makes the handler verify the cookie before it looks anything
up. What that buys here is narrower than the usual pitch: ids are already 16 bytes of randomness
(`SessionHandler.DEFAULT_SESSIONID_MIN_LENGTH`), so signing does not make them harder to guess. It
means a forged id is rejected at the signature check instead of costing a lookup in the clustered
Ignite session cache, plus the retry window in `ApiGatewayConfiguration.sessionStore`. It is a
store-probing mitigation, not an authentication one.

**What makes it a secret-storage question rather than a one-line change.** The secret has to be
identical on every node and stable across restarts, or a rolling deploy invalidates every live
session. So it belongs next to `PlatformSecretsProperties.jwtSigningKeysPath`, mounted the same way,
not generated per instance.

**Rotation is a fleet-wide logout.** `setSigningSecret` takes a single secret with no
accepted-previous window, so rotating it fails verification for every outstanding cookie at once.
That is the opposite of how the JWT signing keys are meant to rotate, and it is the thing to decide
before wiring it up: either this secret is deliberately non-rotating, or a mass logout on rotation is
accepted.

**Two related suggestions that were looked at and rejected.**

* `setSessionCookieName(...)` to hide that the stack is Vert.x. The stack is already named by the
  `v12.stomp` subprotocol, the `/v1` upgrade path, and the `/api/auth/*` route shapes, so the
  obscurity does not survive. It also breaks `/api/auth/me`: `SessionEndpointHandler` checks
  `SessionHandler.DEFAULT_SESSION_COOKIE_NAME`, the constant rather than the configured value, so a
  rename makes that route return 401 for authenticated callers. Doing it properly means making the
  name shared config across two modules first. (Done since, for a different reason: published UIs
  live on sibling hosts of the API under `apps.kinotic.ai`, and the `__Host-` prefix keeps a page
  there from planting a session cookie the API would read. `EventConstants.SESSION_COOKIE_NAME` is
  the one name both modules use.)
* `setNagHttps(true)` is already in effect — `DEFAULT_NAG_HTTPS` is true and nothing disables it.
  That is the "session cookies without https" line in the dev logs. It is a log warning, not a
  control.

**Where this came from.** Chasing credentials that sit in a heap dump for the life of a WebSocket.
`clientSecret` and `Authorization` are now dropped from the upgrade request in
`EndpointConnectionHandler.handshake`, verified to zero. The session cookie is not: removing the
`Cookie` header only halves the copies because Vert.x also keeps the parsed jar on the response
(`Http1ServerResponse.cookies()`) for the connection's life, and clearing that is the same object
`SessionHandler` uses to emit `Set-Cookie`. Signing does not change that either — it is a separate
concern from what a dump contains.

### First-deployment placement ignores the microservice count

`ProjectDeployJobDefinitionFactory.resolveTarget` picks a first deployment's node with a probe
sized for the sync VM alone (`deployment().getSyncMemoryMb()`), in task 1. The commit's artifacts,
and so the number of runtime VMs the project needs, are only known in task 3, after the sync VM has
run on that node and reported them through `ProjectArtifactService.recordArtifacts`. Once each
microservice gets its own VM, every one of them must land on the node holding the checkout, so a
first deployment can pick a node that fits the sync VM and not the runtime VMs behind it.

The decision for now: probe for the sync VM only and let each runtime VM's placement on that node
fail loudly. What that costs: a project's first push can sync successfully and then fail at
"Ensure runtime workloads" on a node other projects could have used, and the project is pinned to
that node from then on (the checkout lives there; nothing re-homes a project). Later deployments do
not have the problem, the node is fixed by then.

Ways to do better, once there is something to design against:

* Size the probe from the artifacts of the previous deployment when there is one, which covers
  every redeploy after a node loss, and only leaves the genuinely first push blind.
* A reserve budget for first deployments (`syncMemoryMb + n × runtimeMemoryMb` for a configured
  `n`), which is a property nobody has asked for yet.
* Run discovery before choosing the node: a discovery VM on any node with its own shallow checkout,
  or a server-side read of the commit, so task 1 knows the count. The second is what Phase 1 started
  with and moved into the VM so discovery can use the Bun ecosystem.
* Re-home a project whose node cannot fit its runtime VMs: move the checkout and its VMs to a node
  that can. This is the one that fixes the pinning as well, and the one that needs real design.

### Outstanding
* Move secret storage stuff out of the kinotic-core
* Fix OidcFlowOrchestrator.java to not secretReferenceResolver.resolve for finding secrets. This won't work for our customers’ configs and should be done differently for our signup configs. 
* Move get rid of kinotic-core/src/main/java/org/kinotic/core/api/config/SslHelper.java


# Grafana Labs
* We may want the multi tenancy to be org.app.tenant , so metrics can be displayed to our customers’ users. 

# KinoticIgniteClusterManager
* revisit the statusFlux and changesFlux AI thinks they are good, I feel like they are redundant.|

# Docs
Make sure the ServiceDirectory logic is documented for TS code, once we finish implementing it.


# Kinotic TS
We store a bunch of maps during decorator processing that will not be used. We need to formalize this into the TS-Morph stuf we are going to do. Left here in case I forgot.


# Kinotic OS Security
* Add flags to specify what users are allowed to login, i.e. System, Org, App. This will allow us to only allow System logins from behind a  VPN.
  * Make sure this flag also affects if a JWT can be minted, basically will require the org or app id based on the allowed login hierarchy.
* Make the Kinotic CLI and LLM plugins authenticate with credentials that are not an IamUser, but associated with one. We heed a new type of credentials for external programs to use, similar to an IamUser but a different thing. 
* When we have a system UI, add support for setting the secrets for Github, Azure, and Google apps. I don't like the idea of doing it through a terminal where they end up having to be on a users machine and also in terminal history. 
* Make sure @Publihed services only live in expected java modules. (We should add a gradle task to check this)


# Kinotic CLI
* Remove or fix the init command. It does not have the OrgId and the way the AppId is handled is problematic.