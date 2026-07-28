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
