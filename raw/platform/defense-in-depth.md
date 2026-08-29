# Defense in Depth

> The layered enforcement strategies applied to every request path in Kinotic OS.

## Overview

The platform is built so that no single defect — a buggy query filter, corrupted stored data, a misbehaving client, a replayed token — can widen access on its own. Every request path crosses at least two independent enforcement layers that would each have to fail for an unauthorized action to succeed. This page catalogs those layers; the operational security model (scopes, credentials, OIDC) is on [System Security](/platform/system-security).

Two principles run through all of it:

- **Enforcement is structural, not pattern-based.** Authorization decisions read typed values — the participant type, the zone parsed from the [CRI](/platform/reference/cri-format) — never string patterns a crafted input might slip past.
- **Internal faults are logged, not leaked.** When an internal invariant is violated, the full detail (CRIs, ids) goes to the server log for operators, and the caller receives a generic error indistinguishable from a routine failure.

## Network Architecture

The target deployment shape gives every participant type its own plane — its own server, gateway, and credential surface — so isolation comes from what code exists where and what listens where, not from configuration flags.

<architecture-diagram>



</architecture-diagram>

Reading the diagram: solid lines are network paths (every internet crossing is one of two public HTTPS listeners, or the VPN gate); dotted lines are data access; dashed enclosures are security boundaries. The system server has no public listener — its only ingress is the VPN gate for operators, the VNet-internal path from workload nodes, and the OS-bus cluster interconnect. The two buses are never linked; shared data stores — every access scoped System, Org, or App — are the only coupling between the buses.

The layers, from the outside in:

1. **Network** — three internet ingress points: two public HTTPS gateways and a VPN gate. The system server is reachable only through the VPN or from inside the VNet; the two buses are never linked.
2. **Authentication** — a dedicated SecurityService per plane; each accepts only its own participant types, with the others' code absent, not disabled.
3. **Authorization** — zone rules plus service-level RBAC; machine identities are role-narrowed, one identity per node, instantly revocable.
4. **Isolation** — workloads run sandboxed; credentials reach them only as short-lived secret references, resolved at execution.
5. **Data** — every access scoped System, Org, or App over shared clusters, with least-privilege storage principals underneath.

## Zone Authorization

Every routable address carries its zone in the CRI itself, so the isolation boundary travels with the message and is validated wherever the message goes. Which zones a participant may address is derived from the participant type in one place — `ZoneRules` — and every enforcement point uses that same derivation. The full participant × zone matrix is in the [CRI format reference](/platform/reference/cri-format).

The derivation defends its own inputs:

- **Ids are validated before they form a zone.** An organization or application id must be a single dot-free label. A crafted id containing a dot would shift the `app.<organizationId>.<applicationId>` label structure — letting one (org, app) pair produce the same zone as a different pair plus a sub-zone — so such an id fails authentication instead of widening access.
- **Sub-zone matching respects label boundaries.** `app.acme-org.orders-app.billing` is a sub-zone of `app.acme-org.orders-app`, but `app.acme-org.orders-app-2` is not — the match requires a dot at the boundary, so a similarly-prefixed zone name grants nothing.
- **Un-zoned addresses are system-only.** An address without a zone carries no isolation boundary, so only participants allowed to send anywhere may address it.

## STOMP Endpoint

Every frame on a connection is checked against the participant's zones — authenticating once does not exempt any later send or subscribe:

- **Sends** must target a zone the participant may address, or match a single-use, exact-CRI temporary grant issued by the server for a specific expected message.
- **Subscriptions** are limited to the participant's subscribable zones plus its own reply destinations. Reply destinations are scoped by a server-generated `replyToId` — the client cannot pick a guessable or colliding value, and a connection can never subscribe to another connection's replies.
- **management-api and app-api are hosted in-process only.** No external connection may subscribe in those zones, so no external node can register itself as a platform service and intercept calls.

## MCP Endpoint

An MCP `tools/call` crosses two independent authorization layers:

1. **Resolution is scoped.** The tool lookup queries Elasticsearch with the caller's zone visibility filter — a tool outside the participant's zones is not found at all, exactly as if it did not exist.
2. **Dispatch is re-authorized.** Before the call is sent, the invoker re-checks the resolved CRI against the same `ZoneRules` the STOMP path enforces. If the stored directory data or the visibility query were ever wrong, the dispatch is refused, the CRIs are logged as a server fault, and the caller sees an unknown tool.

The surrounding surface is hardened the same way:

- **Tool names are always minted, never parsed.** A name is derived from the service's qualified name and function, making it unique system wide; no override exists, and nothing ever parses a name apart to make a decision — resolution is a caller-scoped directory query and authorization reads the zone from the stored CRI, so the name is never a trust input.
- **Listings never expose dispatch addresses.** The tool listing query excludes the CRI at the Elasticsearch `_source` level, so the internal address never leaves the data store for a listing.
- **Duplicate names are refused, not resolved.** Minted names are unique system wide, so more than one match for a name means the directory index is corrupted. The platform never picks a winner: the providing CRIs are logged for operators and the caller receives a generic internal error.

## GitHub Install Binding

Linking a GitHub account stores the record that authorizes every later repository operation for that org — repo creation, pushes, token minting all trace back to it — so the bind itself crosses three independent proofs before it is persisted:

- **The state token binds the round-trip to the org.** `startInstall` mints a single-use, 10-minute state bound to the caller's organization; `completeInstall` consumes it atomically and rejects a state staged for a different org. A replayed or forged callback dies here.
- **The user-authorization code binds the round-trip to a GitHub user.** The App requests user authorization (OAuth) during installation, so GitHub's post-install redirect carries a code that only the person who authenticated at GitHub in that browser can produce. The server exchanges it — using the GitHub App's own OAuth credential — for that user's access token.
- **GitHub attests the user controls the installation.** The `installation_id` in the redirect is browser-supplied and therefore attacker-controlled; installation ids are small sequential integers, and the App itself can resolve *any* of its installations, so no App-credential lookup can vet one. Instead the server asks GitHub which installations of this App the authorizing user can access (`GET /user/installations` with the user's token) and persists the binding only when the claimed id — with a matching App id — appears on that list. Claiming another customer's installation fails with an authorization error and nothing is stored.

The account login and type stored on the binding are read from the attested `/user/installations` entry, never from a lookup made with the App's own credentials.

## Authentication Cross-Checks

Documented in full on [System Security](/platform/system-security), one check is defense-in-depth by design:

- **JWT scope claims are cross-checked against the user record.** A Kinotic-minted JWT carries `organizationId`/`applicationId` claims, and authentication requires them to match the `ParticipantIdentity` the `sub` claim resolves to — a token minted before the user was moved or re-scoped is rejected even though its signature verifies.

Credential failures are also uniform: an unknown email and a wrong password produce the same `Invalid credentials` error, so the login surface cannot be used to enumerate which accounts exist.
