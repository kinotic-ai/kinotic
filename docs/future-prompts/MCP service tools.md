# MCP Service Tools — Implementation Plan

Expose Kinotic published services as MCP (Model Context Protocol) tools so LLMs can call
them through the API gateway. Built in 5 phases; **STOP after each phase and wait for
explicit approval before starting the next.** Each phase compiles, passes its tests, and
is committed on branch `claude/vertx-mcp-kinotic-gateway-udkpr7` before pausing.

Read the repository `CLAUDE.md` first — every convention in it binds (package layout,
Lombok, comments, code smells, dependency versions, docs-in-sync). Build commands for
this cloud environment are in `CLAUDE.md` (JDK 25 download + `CLAUDE_CLOUD_COMPILE=true`).
Known environment issue: the Gradle wrapper's 9.1.0 download 403s through the egress
proxy (GitHub release asset) — use the pre-installed `/opt/gradle/bin/gradle` with the
same flags instead of `./gradlew`.

File paths below carry line numbers that were accurate when this plan was written —
re-verify before editing; code may have drifted. When this plan and the actual code
disagree, the code wins: read the referenced source before implementing against it.

---

## Architecture (decisions already made — do not relitigate)

1. **Tool unit = a published service function.** A service method opted in via a new
   `@McpTool` annotation becomes an MCP tool. Entity data is NOT special-cased; entities
   are reachable through already-published services like `JsonEntitiesRepository`.
2. **The gateway never reflects on service classes.** Services live on other nodes.
   Tool contracts are **data**: C3 `ServiceDefinition`s captured where the service
   registers (reflection is possible there) and stored in a **ServiceDirectory**.
3. **The ServiceDirectory API lives in `kinotic-core`; the Elasticsearch impl lives in
   `kinotic-domain`; core ships NO implementation.** `kinotic-core` is used completely
   standalone by at least one customer (no other kinotic modules), so the directory
   must add zero runtime weight there: core defines the API, the entry/descriptor
   model, and the capture logic — and capture resolves `ServiceDirectory` OPTIONALLY
   (`ObjectProvider`/optional injection); when no impl bean exists, capture is skipped
   entirely (no beans, no schema work, no state). No in-memory default impl — YAGNI.
   `kinotic-domain` (which already has the ES client, Ignite, Caffeine, and the
   `AbstractRepository` family) ships the OS-grade impl. This requires a new module
   dependency `kinotic-core → kinotic-idl`, declared **`api` scope** (idl types like
   `ServiceDefinition` appear in core's public directory API, and domain then sees them
   transitively) — acyclic (idl depends on no kinotic modules) and intended: core's
   dormant `ServiceDirectory` (`api/ServiceDirectory.java`, 2019) and idl's
   `SchemaFactory` (`api/directory/SchemaFactory.java`) are two halves of the same
   feature. Standalone-core customers pay one small compile-time jar, nothing at
   runtime.
4. **The C3→JSON Schema converter is a PORT of the OpenAPI converters, living in
   `kinotic-idl`.** The GraphQL/OpenAPI code in `kinotic-persistence` is legacy, kept
   for reference only, and will eventually be deleted — MCP must not be load-bearing on
   it. Port the conversion logic (property iteration, required handling, `oneOf`,
   ref/cycle mechanics) into an idl converter strategy emitting `tools.jackson` nodes
   (no swagger dependency); leave the originals untouched.
5. **Liveness is an `online` flag on the directory entry, maintained per implementation
   — never by per-service gateway monitoring.** Gateways serve every customer (100k+
   services possible), so per-CRI standing monitors on gateways are forbidden
   (`monitorListenerStatus` stays what it is: the per-active-stream tool
   `ServiceInvocationSupervisor` uses, `ServiceInvocationSupervisor.java:323`). The ES
   impl maintains the flag with ONE HA cluster singleton (Ignite service grid,
   `ignite.services().deployClusterSingleton(...)` — check for an existing kinotic
   singleton idiom first) consuming one bus-wide listener-change stream and applying
   partial updates; connect/disconnect events are rare, so write volume is trivial.
   Three correction layers, each with a distinct job:
   - **Event stream** (fast, usually right): deltas from `monitorListenerChanges()`.
   - **Reconciliation** (slow, complete): on singleton start and a periodic timer
     (~10 min), snapshot `activeServiceAddresses()`, set-diff against entries,
     bulk-correct — without this, one event missed during failover lies forever.
   - **Demand-driven repair** (instant, where it matters): gateways report `NO_HANDLERS`
     hits via `reportUnreachable(cri)`; the impl re-checks current registrations and
     writes the *verified* truth — an invalidation trigger, never a blind offline write
     (keeps single-writer, race-proof against a reconnect mid-report).
   Call-time `NO_HANDLERS` → "service offline" tool error remains the authoritative
   guard in every window. The flag also serves the admin UI and health views.
6. **Structural scope, not a scope enum.** `ServiceDirectoryEntry` has nullable
   `organizationId`/`applicationId`/`projectId` — both null = SYSTEM (OS services),
   exactly like the participant model (`kinotic-domain/.../api/security/`, validation
   precedent in `KinoticSecurityService`: `applicationId` without `organizationId` is
   rejected). Non-system *ownership* queries always filter
   `organizationId == participant's`, so OS entries are structurally unreachable there.
7. **Three visibility questions, three filters** (never conflate):
   - *What can I call?* → MCP `tools/list` = (zone send rules mirroring
     `StompAuthorizerFactory`) ∩ (`mcpExposed`) ∩ (`online`). Org/app participants DO
     see MCP-exposed OS (`os-api` zone) tools here — an LLM can only call what is
     listed.
   - *What belongs to my app?* → ownership queries, participant-org filtered; OS
     entries never appear.
   - *Telemetry* → out of scope for this plan (spans carry producing-service scope; a
     later work stream).
8. **Hand-rolled MCP server — the official Java SDK is NOT a runtime dependency.** The
   SDK's server model owns one in-memory tool list per server instance, but our tool
   list is per-caller and lives in the directory. The gateway implements the stateless
   streamable-HTTP subset directly (plain Vert.x + `tools.jackson`): JSON-RPC 2.0 over
   `POST /mcp`, methods `initialize` (capabilities `tools` with `listChanged:false`,
   protocol version negotiation), `notifications/initialized`, `ping`, `tools/list`,
   `tools/call`. No sessions, no SSE, no keep-alive — every POST independently
   authenticated; GET/DELETE → 405; JSON-RPC batches rejected (removed from the spec).
   Implement against the MCP specification revision current at build time. The official
   SDK IS used as a **test-only** dependency: the e2e drives the server with the
   reference client — that is the interop guarantee.
9. **Transform once, store, serve.** MCP tool descriptors (including JSON Schemas) are
   built at WRITE time by the capture path and stored on the entry; `tools/list` is a
   pure query with zero conversion on the read path. Regenerated on every upsert.
10. **Streaming rejection at "compile time".** Capture REJECTS `@McpTool` on any
    function whose return type is multi-response/streaming (inspect how `SchemaFactory`
    models `Flux`/`Publisher` vs single-value `Mono`/`CompletableFuture`), failing the
    registration with an error naming the function. Never checked at call time.
11. **`tools/call` dispatches through the existing RPC path** — build an `Event`, set
    the sender participant, send to the `srv://` CRI over the event bus — never a
    reflective side-door. Mirrors `EndpointConnectionHandler.send()`
    (`kinotic-api-gateway/.../stomp/EndpointConnectionHandler.java:127`).
12. **Two contract writers, both trusted:** Java services self-capture at registration
    (this plan); customer TS services arrive later via the CLI codegen → sync pipeline
    (out of scope — do NOT build a runtime contract hand-off from customer VMs).

**Testing policy (user-set):** ONE e2e test with real infrastructure (Phase 4) verifies
Phases 2–4 end to end, extended in Phase 5; ONE unit test file for the pure schema
transform (Phase 1). Phases 2–3 are compile + code review only. Do not add more tests.

---

## Phase 1 — kinotic-idl: JSON Schema converter (ported) + MCP decorator

**Read first:** the OpenAPI converters being ported —
`kinotic-persistence/.../internal/converters/openapi/OpenApiConverterStrategy.java`
(primitive/enum mapping table), `ObjectC3TypeToOpenApi.java` (property iteration,
required handling, `$ref` emission ~line 55, and the TODO about same-name collisions),
`UnionC3TypeToOpenApi.java` (`oneOf` + discriminator), `ArrayC3TypeTpOpenApi.java`,
`OpenApiConversionState.java` (`referencedSchemas`). Also
`kinotic-idl/.../api/converter/` (`IdlConverterStrategy`, `C3TypeConverter`,
`C3ConversionContext`, `IdlConverterFactory`), the C3Type subtypes in `api/schema/`,
and `api/schema/decorators/NotNullC3Decorator.java` as the decorator template.

**Create (all in `kinotic-idl`; the persistence originals stay untouched — they are
legacy kept for reference and slated for deletion):**

- `api/schema/decorators/McpToolC3Decorator.java` — follows `NotNullC3Decorator`
  (`type = "McpTool"`, targets `FUNCTION`; add the enum constant to `DecoratorTarget`
  if absent). Fields: `String description`, `boolean readOnlyHint`,
  `boolean destructiveHint`, `boolean idempotentHint`.
- A JSON Schema converter strategy under `api/converter/jsonschema/` implementing
  `IdlConverterStrategy<ObjectNode, JsonSchemaConversionState>` where `ObjectNode` is
  **`tools.jackson.databind.node.ObjectNode`** (what idl already uses — NO swagger
  dependency). Port the openapi converters' logic, preserving their
  `Created by Navíd Mitchell 🤪` attribution comments on ported types:
  - primitive/enum/date mapping table from `OpenApiConverterStrategy` (byte/short
    min-max bounds, `int32`/`int64`/`float`/`double` formats, `date-time`, 1-char
    strings, string enums);
  - object conversion from `ObjectC3TypeToOpenApi`: property iteration, required from
    the NotNull decorator, complex fields registered in the state's `referencedSchemas`
    and emitted as `$ref: "#/$defs/<Name>"` (the ref mechanism must survive — cyclic
    types require refs; never fully inline); fail loudly on same-name `$defs`
    collisions within one contract (the ported TODO, smaller blast radius here);
  - union conversion from `UnionC3TypeToOpenApi`: `oneOf` of refs, NO `discriminator`
    (OpenAPI vocabulary — the `oneOf` is the portable part);
  - array from `ArrayC3TypeTpOpenApi`; `MapC3Type` →
    `{"type":"object","additionalProperties":<schema>}` and `ReferenceC3Type` ONLY if
    `SchemaFactory`-derived contracts actually produce them (check
    `GenericTypeConverter`/`PojoTypeConverter` in `internal/directory/` first; YAGNI
    otherwise);
  - emit `metadata` `"description"` values as JSON Schema `description`.
  Do NOT resurrect the dead `internal/support/jsonSchema` model package.
- `McpJsonSchemaGenerator` (same package): for a `FunctionDefinition`, converts each
  parameter through the strategy and assembles the self-contained inputSchema —
  `{type:"object", properties:{<param name>: <schema>}, required:[...],
  $defs:{<referencedSchemas>}}` — serialized to a JSON string. Fresh conversion state
  per function/service — note `OpenApiConverterStrategy.initialState()` returns a
  shared instance against the interface's documented contract; do not port that quirk,
  or one service's `$defs` leak into another's schema.

**Tests:** ONE unit test file for `McpJsonSchemaGenerator`: hand-built
`FunctionDefinition`s, asserting emitted JSON strings — nesting via `$defs`, required,
union → `oneOf` with no `discriminator`, a reference cycle, parameter descriptions.
Pure transform, no collaborators (the CLAUDE.md unit-test exception); schema bugs are
painful to localize from the Phase 4 e2e. No per-converter test files.

~10–12 files. Verify: `CLAUDE_CLOUD_COMPILE=true ./gradlew :kinotic-idl:test` (with the
JDK 25 flags from CLAUDE.md). Commit. **STOP for approval.**

---

## Phase 2 — kinotic-core: directory API, @McpTool, capture, liveness primitives

**Read first:** `kinotic-core/.../api/ServiceDirectory.java` (the dormant 2019
interface being reshaped — preserve its `Created by navid on 2019-06-11` attribution),
`api/annotations/Publish.java`, `internal/ServiceRegistrationBeanPostProcessor.java`
(registration flow, ~lines 42–116), `api/ServiceRegistry.java`,
`api/service/ServiceIdentifier.java`, `api/event/EventBusService.java` and
`internal/api/event/DefaultEventBusService.java:95-160` (`listen`,
`monitorListenerStatus` — the Ignite subscription-cache machinery to build on),
`kinotic-idl/.../api/directory/SchemaFactory.java`.

**Gradle:** `kinotic-core/build.gradle` gains `api project(':kinotic-idl')` — api scope
because idl types are part of core's public directory API (architecture decision #3 —
verify no cycle: idl must not depend on core; verify the java-library conventions plugin
exposes the `api` configuration).

**Create / edit:**

- `api/annotations/McpTool.java`:

  ```java
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  public @interface McpTool {
      String description();                    // LLM-facing contract — required
      boolean readOnlyHint() default false;
      boolean destructiveHint() default false;
      boolean idempotentHint() default false;
  }
  ```

  Method-level only, on the `@Publish` interface's methods. A service is MCP-exposed
  iff at least one method is annotated.
- Directory model, own files in `api/directory/` (move `ServiceDirectory` there if a
  second/third file justifies the package per CLAUDE.md — it will):
  - `ServiceDirectoryEntry` — structural scope, plain strings (core does not depend on
    kinotic-domain):

    ```java
    private String id;               // non-null scope parts + namespace + name, dot-joined, lowercased
    private String organizationId;   // null for OS/system services
    private String applicationId;    // null for OS/org-level; never set without organizationId
    private String projectId;        // customer provenance only
    private String namespace, name, version;   // from ServiceIdentifier
    private String description;
    private ServiceDefinition contract;        // the C3 contract, decorators included
    private String sourceVersion;    // kinotic release (runtime capture) or commit SHA (future sync)
    private boolean published;
    private boolean mcpExposed;      // denormalized: any function carries McpToolC3Decorator
    private List<McpToolDefinition> mcpTools;  // ready-to-serve, built at capture (decision #9)
    private boolean online;
    private Instant lastStatusChange;
    ```

  - `McpToolDefinition` — dumb DTO: `toolName` (sanitized, Phase 4 naming), `description`,
    `inputSchema` (JSON string from `McpJsonSchemaGenerator`), `cri` (string),
    `functionName`, `List<String> parameterNames` (declared order), the three hints.
  - `ServiceDirectory` reshaped: `register(ServiceDirectoryEntry)` (upsert of contract
    fields — implementations must NOT let it clobber liveness fields they maintain),
    `unregister(String entryId)` (marks offline; entries are never deleted —
    known-but-offline is a feature), ownership query (participant-scoped), a
    tools query for MCP (`mcpExposed` + `online` + caller visibility per decision #7),
    `reportUnreachable(String cri)`. Exact signatures follow existing core async style
    (`CompletableFuture`, `Page`/`Pageable` from `api/crud`).
- Capture in the registration path (`ServiceRegistrationBeanPostProcessor` /
  `DefaultServiceRegistry` — pick the seam that has the interface `Class` and
  `ServiceIdentifier` in hand): resolve `ServiceDirectory` OPTIONALLY
  (`ObjectProvider` / `@Autowired(required=false)`); **when no impl bean exists, skip
  capture entirely** — no schema work, no state, nothing (decision #3: standalone-core
  deployments must not pay for this). When present and the interface has any `@McpTool`
  method: build the `ServiceDefinition` via idl's `SchemaFactory`, attach
  `McpToolC3Decorator` + function `metadata` descriptions from the annotations, REJECT
  streaming-return functions (decision #10), build descriptors via
  `McpJsonSchemaGenerator`, and call `serviceDirectory.register(entry)` (SYSTEM scope:
  org/app null; `sourceVersion` = kinotic version). Unregistration calls
  `serviceDirectory.unregister(id)`. Core ships NO `ServiceDirectory` implementation.
- Liveness read primitives on `EventBusService` (implemented in
  `DefaultEventBusService`, consumed by Phase 3 — build nothing beyond these):
  - `monitorListenerChanges()` → `Flux<ListenerChange>` (`ListenerChange` record —
    address + `ListenerStatus` — own file in `api/event`): ONE Ignite cache-entry
    listener over the vertx subscription cache filtered to `srv://` addresses (reuse
    the machinery at `DefaultEventBusService.java:107-160`, minus the per-address
    filter).
  - `hasListeners(CRI)` → `CompletableFuture<Boolean>`: one-shot
    `clusterManager.getRegistrations` read (the initial check inside
    `monitorListenerStatus`, ~:142), no listener registration.
  - `activeServiceAddresses()` → `CompletableFuture<Set<String>>`: one scan of the
    subscription cache for `srv://` addresses (reconciliation snapshot — set-diff
    beats per-entry lookups at 100k entries).

**Tests:** none (policy above) — compile + code review at the pause; behavior lands in
the Phase 4 e2e.

~10–12 files. Verify: `:kinotic-core:compileJava`. Commit. **STOP for approval.**

---

## Phase 3 — kinotic-domain: Elasticsearch ServiceDirectory + liveness singleton

`kinotic-domain` is the impl's home: it already has the ES client, Ignite, Caffeine,
and the `AbstractRepository` family in-module (and the gateway already depends on it).

**Read first:** the repository base classes in
`kinotic-domain/.../internal/api/repositories/` (`AbstractRepository`,
`AbstractOrganizationScopedRepository`, `AbstractProjectScopedRepository`) and
`CrudServiceTemplate`; `kinotic-persistence/.../api/model/EntityDefinition.java` +
`EntityDefinitionRepository.java` as the ES mapping/index reference ONLY (`kinotic_`
prefix — do not depend on persistence); `KinoticSecurityService` (scope validation),
participants in `kinotic-domain/.../api/security/`, and
`kinotic-api-gateway/.../stomp/StompAuthorizerFactory.java:42-124` (zone rules the
tools-query visibility must mirror; `DomainUtil` zone constants).

**Create (in `kinotic-domain`, implementing core's `ServiceDirectory`):**

- `internal/api/ElasticServiceDirectory` — the sole `ServiceDirectory` impl bean (plain
  `@Component`; core has none — its presence is what activates core's capture).
  Enforces the write-path scope invariant (`applicationId` without `organizationId`
  rejected, same rule as `KinoticSecurityService`); computes `mcpExposed` at write,
  never accepts it from input; `register` upserts contract fields WITHOUT touching
  `online`/`lastStatusChange` (single-writer: the singleton owns those).
- `internal/api/repositories/ServiceDirectoryEntryRepository` — ES index
  `kinotic_service_directory`. NOTE: the org/project-scoped repository bases route by
  `organizationId` and assume it non-null — system entries break that; route system
  entries by a constant or build on `CrudServiceTemplate` directly (read the base class
  and pick). Queries: ownership (scope-filtered), tools
  (`mcpExposed && published && online` + caller visibility: system → all; org
  participant → `os-api`-zone entries; app participant → own-app + `os-api` entries —
  keep this filter in ONE place with a comment pointing at `StompAuthorizerFactory`;
  `sendAllowed` remains the call-time enforcement, this is only the listing view).
- `reportUnreachable(cri)` — debounced per CRI (seconds; Caffeine). Re-checks
  `eventBusService.hasListeners(cri)` and partial-updates the liveness fields to the
  VERIFIED state — never a blind offline write. Safe for any authenticated caller whose
  zone rules allow sending to that CRI (it can only trigger a verification).
- `internal/api/services/ServiceLivenessUpdater` — the HA cluster singleton
  (architecture decision #5). Lifecycle: on start, reconcile
  (`activeServiceAddresses()` snapshot, set-diff, bulk partial-update `online` +
  `lastStatusChange`), then consume `monitorListenerChanges()` deltas as ES partial
  updates touching ONLY the two liveness fields. Periodic re-reconcile (~10 min).
  Addresses matching no entry are ignored.

**Tests:** none (policy above) — the capture path, scope invariants, and liveness are
asserted in the Phase 4 e2e.

~8–10 files. Verify: `:kinotic-domain:compileJava`. Commit. **STOP for approval.**

---

## Phase 4 — kinotic-api-gateway: hand-rolled stateless MCP endpoint

**Read first:** `kinotic-api-gateway/.../internal/endpoints/ApiGatewayVertcleFactory.java`
(route mounting, global handlers), `kinotic-domain/.../api/rest/SuppliesGatewayRoutes.java`
(the mount seam — this phase is its next consumer), `EndpointConnectionHandler.java`
`send()`/`handshake()` (participant resolution, sender header, reply-to
minting/validation, ~lines 55–150 and 335),
`kinotic-core/.../api/security/SecurityService.java:22`
(`authenticate(Map<String,String>)`), `kinotic-core`
`internal/api/service/invoker/` `ArgumentResolver`/`ReturnValueConverter` composites
(**match the wire encoding of arguments and return values exactly — read these before
writing the invoker; expect JSON with args in declared order, content-type
`application/json`**), `EventConstants`, and the MCP specification's stateless
streamable-HTTP + tools pages (lifecycle, `tools/list`, `tools/call`, JSON-RPC error
codes).

**Gradle:** the official SDK is TEST-ONLY (e2e client):
`testImplementation 'io.modelcontextprotocol.sdk:mcp'` in the gateway `build.gradle`,
`mcpSdkVersion` in `gradle.properties` (alphabetical), pin in the `dependencyManagement`
block of `buildSrc/src/main/groovy/org.kinotic.java-common-conventions.gradle`
(CLAUDE.md rule). NO runtime MCP dependency.

**Create (all in `kinotic-api-gateway/.../internal/mcp/`):**

- `McpGatewayRoutes implements SuppliesGatewayRoutes` — mounts `POST /mcp` (constant,
  not a property) with a `BodyHandler` sized for JSON-RPC bodies; GET/DELETE → 405. Per
  request: `securityService.authenticate(headers)` → `Participant`; 401 on failure. No
  session reads — every request authenticates independently.
- `McpJsonRpcHandler` — the hand-rolled server core (`tools.jackson` only): parse the
  JSON-RPC 2.0 request (single messages only — reject batch arrays with -32600),
  dispatch by method: `initialize` → static capabilities (`tools`,
  `listChanged:false`), negotiated `protocolVersion`, `serverInfo`;
  `notifications/initialized` → accept; `ping` → `{}`; `tools/list` / `tools/call` →
  below; unknown method → -32601; malformed JSON → -32700; bad params → -32602.
  Responses are plain `application/json` (no SSE upgrade).
- `tools/list` — calls the autowired core `ServiceDirectory` tools query (the ES impl
  comes from `kinotic-domain`, which the gateway already depends on — no new module
  edges) and wraps the stored descriptors — including
  the hints as MCP tool `annotations` — in the JSON-RPC result. Descriptors are
  pre-converted at write time and pre-filtered to online + caller-visible: the gateway
  does NO conversion, NO liveness work, holds NO catalog state. No cache in v1 —
  `tools/list` frequency is low; add one later only if profiling demands (YAGNI).
- `McpToolNames` — reversible mapping between tool name and (CRI, function): many MCP
  hosts enforce `^[a-zA-Z0-9_-]{1,128}$`, so dots/slashes must be encoded
  (e.g. `srv://com.acme.CatalogService/search` ⇄ `com_acme_CatalogService-search`);
  collision-check within a listing; keep the mapping data on the descriptor rather than
  parsing names apart.
- `McpToolInvoker` — `tools/call`: resolve descriptor (verify the caller may send to
  its CRI — same zone rules as STOMP); map MCP named arguments to declared positional
  order via `parameterNames` (missing optional → null; unknown name → tool error);
  encode the body exactly as `ArgumentResolver` expects; mint a unique `reply://` CRI
  (mirror `EndpointConnectionHandler`'s replyToId approach), `listen` on it, build the
  `Event` with sender participant + reply-to + content-type headers, send to the
  `srv://` CRI, await the single reply with a timeout constant (~30s), dispose the
  listener. Outcomes: reply event → MCP text content (JSON payload as-is);
  `NO_HANDLERS` → tool error "service offline" + notify `ServiceUnreachableReporter`;
  error headers per `EventConstants` → tool error with the service's message; timeout →
  tool error. No streaming concerns — multi-response functions were rejected at capture
  (decision #10).
- `ServiceUnreachableReporter` — small shared component: fire-and-forget
  `serviceDirectory.reportUnreachable(cri)` (never blocks or fails the caller's request
  path), debounced per CRI. Two consumers justify it now: `McpToolInvoker` and
  `EndpointConnectionHandler.send()`'s existing `NO_HANDLERS` branch
  (`EndpointConnectionHandler.java:149`, where `RpcMissingServiceException` is raised) —
  wiring it there makes every gateway RPC a liveness probe, so the directory self-heals
  from ordinary STOMP traffic too. Failure path only; no hot-path cost.

**Property:** `kinotic.disableMcp` via `@ConditionalOnProperty` matching the established
`kinotic.disable*` idiom (CLAUDE.md properties rule — a deployment-shape flag).

**Docs (same change, CLAUDE.md rule):** add an MCP page under `website/content/**`:
the `/mcp` endpoint, auth expectations, `@McpTool` usage on a published service, tool
naming, stateless behavior (no sessions). Grep `website/content` for gateway route docs
and match their structure.

**Tests:** ONE e2e test class — the verification for Phases 2–4 (testing policy above).
Boot the server harness (see kinotic-test), register a test service with two `@McpTool`
methods, connect the MCP SDK **client** over HTTP to `/mcp`, and assert, authenticating
as each participant type where relevant:
- `initialize` → `tools/list`: names, schemas (typed properties/required — proving the
  Phase 1 emitter and Phase 2 capture), hints; a registered-then-unregistered service's
  tool absent from the listing (proving the liveness flag).
- `tools/call`: happy path, unknown-argument error, offline-service error.
- Scope matrix (the security invariants): app participant sees own-app + `os-api` tools
  and never another app's; org participant sees `os-api` tools; ownership queries never
  return OS entries; a `@McpTool` method with a streaming return type fails
  registration.
The boot is the expensive part and happens once; each assertion is cheap.

~12 files. Verify: `:kinotic-api-gateway:test` + the e2e. Commit. **STOP for approval.**

---

## Phase 5 — Introspection tool + OS tool exposure

- Expose a service-listing function as an MCP tool (the directory eating its own dog
  food): a `@Publish`ed service (home: `kinotic-os-api` or `kinotic-domain`, wherever
  fits the module's existing service layout) delegating to core's
  `ServiceDirectory` ownership query, annotated
  `@McpTool(description = "Lists the services this application provides, with their functions and schemas", readOnlyHint = true)`.
  An org user's LLM introspects its own app's services through the same tool path,
  scope-filtered automatically by participant.
- Extend the Phase 4 e2e test class (do not add a new harness): an
  `ApplicationParticipant` MCP session lists the introspection tool, calls it, and
  receives only its own app's entries (never OS entries), while OS `os-api` tools
  remain callable.
- Decide-with-user during approval: which other OS services get `@McpTool` in this pass
  (candidates: `ApplicationService` creation/listing). Default: only the introspection
  service.
- Docs: extend the MCP page with the introspection tool and an "exposing your own
  services" walkthrough.

~5–8 files. Verify, commit, **STOP** — the user returns to the original session for
review.

---

## Explicitly out of scope (do not build)

- **Customer TS service contracts** — arrive via CLI codegen + sync pipeline (in-flight
  kinotic-github work). No runtime contract hand-off from customer VMs, ever.
- **Prompts / resources** — later (`McpPromptDefinition` as an application-scoped
  entity).
- **HITL / elicitation** — deferred until the 2026-07-28 stateless spec
  (`IncompleteResult`) finalizes; implemented directly in `McpJsonRpcHandler` then.
- **Stateful streamable-HTTP / SSE transports, `tools/list_changed` notifications.**
- **Telemetry span scoping** — separate work stream.
- **The `vertx-mcp` repository** — untouched (targets Vert.x 4.5.x + MCP SDK 0.11.1,
  both two majors stale).
- **The legacy GraphQL/OpenAPI code in `kinotic-persistence`** — read for reference,
  ported from, never depended on, never modified.
