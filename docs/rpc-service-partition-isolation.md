# RPC Service Partition Isolation

**Status:** Design proposal (targets a new major release — breaking changes are acceptable)
**Scope:** `kinotic-core`, `kinotic-api-gateway`, `kinotic-js` (`core`, `os-api`, `persistence`), plus a follow-up in `kinotic-persistence`.

---

## 1. Problem

Applications built on Kinotic publish `@Publish` services (TypeScript, hosted by app workloads) that are invoked through TypeScript proxies. Every external client — including the app workloads that *host* services — reaches the event bus through the gateway. Today an authenticated participant can send to, and subscribe to host, **any** service address in a flat, global namespace. Two failures fall out of this:

1. **Cross-app invocation.** App A's proxy can call App B's service if it targets B's CRI.
2. **Host squatting / interception.** App B can subscribe to A's service address (intercepting A's calls) or shadow a platform service address.

The goal: an application can only call and host services within its own boundary; platform services remain reachable but protected; internal-only services (orchestration) are unreachable by apps. A hard partition — cross-app is impossible by construction, not by convention.

### Where the gap lives in code

The gateway builds one authorizer per connection, but it ignores the participant's org/app and allows everything:

```java
// StompAuthorizerFactory.java — same patterns for EVERY participant
List.of(SERVICE_DESTINATION_SCHEME + "://*.**", STREAM_DESTINATION_SCHEME + "://*.**"); // send AND subscribe
```

Both enforcement points route on the raw CRI:

```java
// EndpointConnectionHandler.send()      → services.eventBusService.sendWithAck(incomingEvent) // raw cri.baseResource()
// EndpointConnectionHandler.subscribe() → services.eventBusService.listen(cri)                 // raw cri
```

The routing key for both hosting and calling is `cri.baseResource()` = `scheme://[scope@]resourceName` (`DefaultEventBusService.java`). That single string is the seam.

The participant identity we need is already present and unforgeable — the gateway attaches it as the event sender:

```java
incomingEvent.setSender(connectedInfo.getParticipant()); // ApplicationParticipant(orgId, appId)
```

`ApplicationParticipant` and `OrganizationParticipant` are sibling interfaces (neither extends the other), which is why services built on `AbstractOrganizationScopedService` — calling `requireParticipant(OrganizationParticipant.class)` — already reject app callers. That structural fact protects most platform services today; see the audit in §9.

---

## 2. Core idea: partition the event-bus address space

Merge **partition** and **namespace** into the leading portion of the CRI `resourceName`. The partition is assigned by the platform (never by the service itself for the security-relevant part) and validated by the gateway against the authenticated participant. Because the same partition is required on both subscribe and send, an app can only ever resolve to addresses inside its own partition — cross-app has no shared address to land on.

```text
srv://[scope@]<partition>.<name>[/method]#version

  scope     = node-instance targeting (unchanged, orthogonal — see ServiceIdentifier TODO about relocating it)
  partition = <kind>[.<kind-validated labels>][.<free namespace>]   (client-supplied, gateway-validates the prefix)
  name      = the final label (no dots)
  version   = semantic version
```

Three partition kinds:

```text
app     → app.<orgId>.<appId>[.<free namespace>]   one application; org/app validated against the participant
api     → api.<namespace>                          shared platform services apps may CALL (not host)
system  → system.<namespace>                       internal-only services; apps may neither call nor host
```

Examples:

```text
Java platform (namespace retained after the kind — collision-safe AND log-legible):
  srv://api.org.kinotic.persistence.api.services.EntityDefinitionService#1.0.0
  srv://system.org.kinotic.orchestrator.WorkloadOrchestrationService#0.1.0

JS app (no package; kinotic assigns app.<org>.<app>, dev may sub-namespace freely):
  srv://app.acme-org.orders-app.OrderService#1.0.0
  srv://app.acme-org.orders-app.billing.InvoiceService#1.0.0

Node targeting still orthogonal, via scope:
  srv://node1@system.org.kinotic.os.LogManager/nodeId#0.1.0
```

### Parsing rule

`name` is always the final label; `partition` is everything before the last dot. The partition's internal shape is decoded by a small **kind grammar** — the leading label decides how many following labels are security-validated:

```text
partition = resourceName up to the last dot;  name = final label
kind = first label:
  app    → next 2 labels = (orgId, appId), validated == participant; remainder-before-name = free namespace (opaque)
  api    → 0 validated labels;  remainder-before-name = namespace (opaque)
  system → 0 validated labels;  system-only policy
```

Constraints formalized at registration:
- `name` = final label, **no dots**.
- `app` partition's `orgId`/`appId` = single dot-free labels (already true for slugified ids).
- Everything between the validated prefix and the name may contain dots (free namespace).

---

## 3. CRI / ServiceIdentifier contract

The wire stays a flat, flexible string; the **contract** exposes typed, purpose-named parts so code never string-slices and each part's provenance is explicit.

```text
ServiceIdentifier {
  PartitionKind kind;    // APP | API | SYSTEM      ← first label
  String orgId, appId;   // present iff kind==APP    ← validated against the participant
  String namespace;      // opaque free namespace    ← service-declared (Java: package; JS: optional)
  String name;           // final label
  String version;
  CRI    cri;            // srv://[scope@]<partition>.<name>#version
}
```

**Provenance split is the security property:**

```text
kind + orgId + appId  ← externally assigned (config/session), gateway-VALIDATED   ("where it is isolated / who authorized it")
namespace + name      ← service-declared                                          ("what it is")
```

The security-relevant prefix must come from something other than the service's own `@Publish`, so a service cannot name its way into another partition. `ServiceIdentifier` currently flags `scope` as awkwardly placed (`// TODO: consider moving this somewhere else`); this design leaves `scope` for node targeting and adds `partition` as the isolation boundary, resolving that overload rather than adding to it.

Diagnostics render the decomposed form (more legible than the flat string):

```text
cri=srv://api.org.kinotic.persistence.api.services.EntityDefinitionService/create#1.0.0
kind=API ns=org.kinotic.persistence.api.services name=EntityDefinitionService
impl=org.kinotic.persistence.internal.api.services.DefaultEntityDefinitionService
```

---

## 4. Gateway policy: client supplies, gateway validates (never forces, never injects)

The client always builds the full partitioned CRI. The gateway is a pure validator — it rejects a disallowed partition, it does **not** guess whether to add one. This deletes the "when do I auto-add a partition?" ambiguity from the gateway: the decision of which partition a call belongs to lives on the client, where it is a structural fact (a local service vs. a platform client lib), not an inference.

```text
verb        participant          allowed partitions (else AuthorizationException)
SUBSCRIBE   APPLICATION(O,A)     app.O.A.*                     (host own services only)
SUBSCRIBE   SYSTEM               api.*, system.*
SEND        APPLICATION(O,A)     api.*, app.O.A.*              (own services + shared platform api)
SEND        SYSTEM               api.*, system.*, app.*
SEND        ORGANIZATION         api.*
```

Validation is **prefix**-based, so the free-namespace tail is opaque to the gateway and never weakens the check:

```text
app hosts app.acme-org.orders-app.billing.*   → app.<O>.<A> prefix == participant → ok
app sends app.other-org.x.Order               → prefix ≠ participant              → REJECT
app sends api.org.kinotic.persistence.…       → kind=api ∈ app send policy        → ok
app sends system.…orchestrator                → system ∉ app send policy          → REJECT
```

Getting the partition wrong on the client is **fail-safe**: a mismatched or omitted partition routes to an address with no handler in that partition (or is rejected), never to another app's service.

Apply the same policy to the `stream://` scheme. `reply://` remains scoped by the server-generated `replyToId` as today.

---

## 5. Registration trust model

Who may *claim* which partition is enforced at the two boundaries we already have:

```text
in-process registration (ServiceRegistrationBeanPostProcessor, trusted JVM)
    → may register under any partition (api / system / …)
over-the-wire registration (app workload via gateway SUBSCRIBE)
    → the app.<O>.<A> prefix is validated against the authenticated participant;
      a client that claims api/system, or another app's O/A, is REJECTED
```

Because an app can never *host* under `api`/`system`, those partitions contain only trusted, in-process platform services. Therefore sending to any `api.*` address can only reach a platform service — never another app — which is what makes the "bare/api = platform, safe to call" guarantee hold.

---

## 6. api / system reclassification (structurally fixes an audit finding)

Placing internal services in `system` fixes the orchestration exposure (see §9) **through the mechanism**, with no per-service `SecurityContext` code:

```text
system  ← VmNodeOrchestrationService, WorkloadOrchestrationService (node/workload control; internal only)
api     ← persistence external API (EntityDefinitionService, JsonEntitiesRepository, NamedQueries*, …), app-callable IAM/app-mgmt
```

Any service currently reachable by apps that should not be gets moved to `system`; apps can no longer route there at all.

---

## 7. kinotic-js changes

Layering keeps org/app out of the generic core:

```text
core        : partition is an OPTIONAL, opaque leading segment. Absent ⇒ today's behavior (generic, non-kinotic users).
              Exposes a partition-resolver hook; never knows org/app. @Publish no longer REQUIRES a namespace
              (a Java-ism with no TS analog) — @Publish(name?) defaults to the class name.
os-api      : installs the resolver → stamps app.<O>.<A> on locally @Publish'd services.
persistence : its proxies (IEntityRepository, …) declare the `api` partition.
CLI/build   : KinoticProjectConfig (.config/kinotic.config.ts) declares organization/application + drives codegen (unchanged).
```

### Source of org/app for the partition: static config, not `ConnectedInfo`

The partition value comes from **static configuration available at wiring time**, not from the post-connect `ConnectedInfo`. Rationale:

- The client must put the partition on the **subscribe**, so it must know it before any connection exists.
- Deriving from `ConnectedInfo` would force "connect before you register services," breaking the current contract where wiring up and instantiating services in `main()` is all that's required.
- The partition value is **not** security-critical — the gateway validates it. A wrong/stale value is rejected (a loud startup error), never a breach. So there is no reason to pay the ergonomic cost of the authoritative-but-late `ConnectedInfo`.

The CRI is already built lazily, so the partition is injected at `register()`/`supervisor.start()`, after config is known but with no dependency on a live connection. `ConnectedInfo` may be used, at most, for an optional post-connect sanity warning if the authenticated `(org, app)` disagrees with the configured one.

The receiving side is unaffected: the js `ServiceInvocationSupervisor` dispatches purely on `cri.path()` (the method name) and ignores `resourceName`/`namespace`/`scope`, so changing the partition portion cannot disturb method routing.

### Chain of custody

```text
KinoticProjectConfig.organization/application   (declarative, project author)
   → orchestrator launches workload with those as login creds / env
   → gateway authenticates → ApplicationParticipant(org, app)
   → client static config supplies partition app.O.A on subscribe/send
   → gateway validates supplied partition == authenticated participant
```

---

## 8. Java-side notes

- The namespace is **demoted**, not deleted: it moves from a required, gateway-parsed field to opaque routing text after the validated prefix. Java services keep their package in the address (via the `api`/`system` namespace tail), so logs/traces stay legible; the invoker can additionally emit the resolved handler class (`HandlerMethod.getBean().getClass()`) to logs and OTel span attributes.
- `ServiceRegistrationBeanPostProcessor` assigns the partition for in-process services (`api`/`system`) and continues to default the namespace to the package.

---

## 9. Authorization audit of current `@Publish` services (motivation + follow-ups)

Bare/shared services must self-enforce against the attested participant. Audit of the 20 `@Publish` services:

| Service | Module | Verdict |
|---|---|---|
| EntityDefinitionService, NamedQueriesDefinitionService, NamedQueriesService | persistence | self-enforces (org/app/project) |
| ApplicationService, MemberService, ProjectService, InviteEmailTemplateService, DeviceApprovalService | os | self-enforces (org-admin sibling-type gate / per-user) |
| GitHubAppInstallationService | github | self-enforces (org) |
| **JsonEntitiesRepository**, **AdminJsonEntitiesRepository** | persistence | **org only, NOT app** — cross-app data access within an org |
| **MigrationService** | persistence | **caller-supplied projectId, unvalidated** |
| **VmNodeOrchestrationService**, **WorkloadOrchestrationService** | orchestrator | **no SecurityContext at all** — any app can control workloads/nodes cluster-wide |
| KinoticClusterInfoService, LogManager | os | not published (`@Publish` commented; LogManager has `FIXME: add RBAC`) |
| LocalAuthenticationService, GitHubProjectRepoService, GitHubWebhookEventService | domain/github | not published (in-process only) |
| DataInsightsService | persistence | disabled (body commented out) |

Two workstreams follow:

- **Partitioning (this doc)** solves cross-app service calls/hosting and, by moving orchestration to `system`, the orchestration exposure — structurally.
- **Per-row data scoping (separate, tracked follow-up)** — partitioning does **not** isolate data *within* a deliberately shared `api` service. `EntityServiceCache` is keyed `(organizationId, entityDefinitionId)` and `findById(entityDefinitionId, organizationId)` never consults `applicationId`, so a same-org/different-app caller can reach another app's entity data by supplying its `entityDefinitionId`. Fix: thread `applicationId` into the cache key and the `EntityDefinition` lookup, and validate the definition's `applicationId` against the participant. `AdminJsonEntitiesRepository` also accepts a caller-supplied `tenantSelection` that must be validated. This is independent of the gateway change and is arguably the highest-exposure hole (it is the primary data path), so it should be scheduled alongside or ahead of partitioning.

---

## 10. Verify before implementation

- **Workload login creds == `KinoticProjectConfig`.** The chain in §7 holds only if the workload authenticates with the same `(org, app)` the project declares. Confirm the orchestrator sources the workload's login credentials from the project's `KinoticProjectConfig` / `Application` record so config and runtime identity cannot disagree.
- **Intended callers of the orchestration services** — confirm they are internal-only (→ `system`) vs. legitimately app-facing before reclassifying.
- **`@Scope` composition** — confirm `scope:sub-scope` still round-trips through `DefaultCRI` alongside a partitioned `resourceName`.
- **Outbound frame destination** to hosting workloads is driven by the STOMP subscription id, not the partitioned routing address (expected; confirm during impl).

---

## 11. Breaking changes (acceptable for the major release)

- CRI shape changes for all services (partition-in-resourceName).
- `@Publish` no longer requires a namespace on the TS side.
- Cross-app calls that previously "worked" stop working (intended).
- Every existing service needs a partition assignment (`api`/`system` for platform, `app` for application services).

---

## 12. Non-goals

- Per-row data authorization inside shared `api` services (tracked separately, §9).
- Secrecy of addresses — addresses are guessable; all isolation is gateway enforcement on subscribe and send.
- Changes to the `reply://` scoping model (already sound via `replyToId`).
