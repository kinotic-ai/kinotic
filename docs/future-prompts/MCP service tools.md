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
   must add zero runtime weight there: core defines the API, the entry/definition
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
   - **Event stream** (fast, usually right): deltas from `monitorServiceListenerEvents()`.
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
     `StompAuthorizerFactory.java:66-90`) ∩ (`mcpExposed`) ∩ (`online`). Exactly the
     send rules, nothing more: system → all zones; org participant → `os-api` +
     `app-api` zones (never `app.<org>.<app>` zones); app participant → own
     `app.<org>.<app>` + `app-api` zones (never `os-api`). An LLM can only call what
     is listed — so OS tools meant for app users must be published in the `app-api`
     zone, and OS tools meant for org users in `os-api`.
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
9. **Transform once, store, serve.** `McpToolDefinition`s (including JSON Schemas AND
   the sanitized tool names) are built at WRITE time by the capture path and stored on
   the entry; `tools/list` is a pure query with zero conversion on the read path.
   Regenerated on every upsert.
   Naming note (Definition vs Descriptor): `*Definition` types (`ServiceDefinition`,
   `FunctionDefinition`, `McpToolDefinition`) are declarative, serializable contract
   data; `*Descriptor` types (`ServiceDescriptor`, `FunctionDescriptor` — the latter
   already renamed from `ServiceFunction` on this branch) are the node-local invocable
   view holding live `Method` handles. Keep new types on the correct side of that line.
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

~10–12 files. Verify: `CLAUDE_CLOUD_COMPILE=true /opt/gradle/bin/gradle :kinotic-idl:test`
(with the JDK 25 flags from CLAUDE.md; wrapper workaround per the note at the top).
Commit. **STOP for approval.**

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
    private String namespace, name, version, zone;   // from ServiceIdentifier; zone drives tools-query visibility (decision #7)
    private String description;
    private ServiceDefinition serviceDefinition; // the C3 contract, decorators included
    private String sourceVersion;    // kinotic release (runtime capture) or commit SHA (future sync)
    private boolean published;
    private boolean mcpExposed;      // denormalized: any function carries McpToolC3Decorator
    private List<McpToolDefinition> mcpTools;  // ready-to-serve, built at capture (decision #9)
    private boolean online;
    private Instant lastStatusChange;
    ```

  - `McpToolDefinition` — dumb DTO: `toolName`, `description`, `inputSchema` (JSON
    string from `McpJsonSchemaGenerator`), `cri` (string), `functionName`, the three
    hints. No parameter metadata: argument binding happens on the service node via the
    named-arguments content type (Phase 4), never from stored state.
  - Tool naming lives HERE (core, where names are minted at capture — not the gateway):
    many MCP hosts enforce `^[a-zA-Z0-9_-]{1,128}$`, so dots/slashes must be encoded
    (e.g. `srv://com.acme.CatalogService/search` → `com_acme_CatalogService-search`).
    Deterministic, collision-checked within one service at capture (fail loudly). No
    parsing names back apart — resolution uses the stored `cri`/`functionName`.
  - `ServiceDirectory` reshaped: `register(ServiceIdentifier, Class<?>)` /
    `unregister(ServiceIdentifier, Class<?>)` (what is captured, stored, and when is
    the IMPLEMENTATION's decision; entries are never deleted — known-but-offline is a
    feature), scope listing, a tools query for MCP (`mcpExposed` + `online` + zone
    visibility per decision #7), `reportUnreachable(String cri)`. Exact signatures
    follow existing core async style (`CompletableFuture`, `Page`/`Pageable` from
    `api/crud`).
  - `AbstractServiceDirectory` (same package) — the common capture logic for ALL
    implementations, NOT a bean: `register`/`unregister` delegate to abstract
    `registerService`/`unregisterService`; protected `buildEntry` performs the
    expensive work and is invoked ONLY when the implementation decides the entry must
    be stored — never eagerly. **EVERY published service gets an entry** (the complete
    directory serves health, project views, and introspection — not only MCP):
    contract via idl's `SchemaFactory` for all functions; for functions carrying
    `@McpTool` additionally `McpToolC3Decorator` + function `metadata` descriptions,
    streaming-return REJECTION per decision #10, and `McpToolDefinition`s with
    inputSchema via `McpJsonSchemaGenerator` and toolName via the naming rule above.
    `mcpExposed` = whether any tool exists, derived, never input. SYSTEM scope:
    org/app null; `sourceVersion` = kinotic version.
- The registration path (`ServiceRegistrationBeanPostProcessor`) resolves
  `ServiceDirectory` OPTIONALLY (`ObjectProvider`) and calls
  `register(serviceIdentifier, interface)` / `unregister(...)` directly — **with no
  impl bean present, literally nothing happens: no beans, no dependencies, no schema
  work** (decision #3: standalone-core deployments must not pay for this). Core ships
  NO `ServiceDirectory` implementation and NO directory-related `@Component`.
- One-line Javadoc contrast while touching these files (decision #9 naming note):
  `ServiceDescriptor`/`FunctionDescriptor` = node-local invocable view;
  `ServiceDefinition` (and the directory) = declarative, serializable contract view.
  Preserve all existing authorship comments verbatim.
- Liveness read primitives on `EventBusService` (implemented in
  `DefaultEventBusService` on top of `KinoticIgniteClusterManager`, consumed by
  Phase 3 — build nothing beyond these):
  - `monitorServiceListenerEvents()` → `Flux<ServiceListenerEvent>` (sealed interface in
    `api/event`, one file per type: `ServiceListenerChange` — address + `ListenerStatus`
    — and `ServiceListenerContinuityLost`): a hot `srv://` monitor on
    `KinoticIgniteClusterManager`, fed by the same `RegistrationListener` updates vertx
    uses for message routing. Each change carries the ADDRESS-LEVEL status aggregated
    from the event's full registration list. The stream never terminates: a gap in
    registration-update continuity is delivered in-band as
    `ServiceListenerContinuityLost`, telling subscribers to rebuild their baseline.
  - `isAnybodyListening(CRI)` (pre-existing) → one-shot
    `clusterManager.getRegistrations` read; the point-in-time verification primitive.
  - `activeServiceAddresses()` → `Future<Set<String>>`: subscription snapshot via
    `KinoticIgniteClusterManager.registeredAddresses(prefix)` (reconciliation baseline
    — set-diff beats per-entry lookups at 100k entries).

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

- Storage is a classic GoF Strategy with the three data-access layers intact: core owns
  the one concrete `DefaultServiceDirectory` context (capture with `sourceVersion` skip,
  verified liveness refresh on register/unregister, `reportUnreachable` debounce+verify,
  query delegation), a `@Component` in `core/internal/api/directory` gated by
  `@ConditionalOnBean(ServiceDirectoryStrategy.class)` — with no strategy bean there is
  no directory bean at all, preserving the standalone-core invariant. The condition
  evaluates at scan time, so `KinoticDomainAutoConfiguration` declares
  `@AutoConfiguration(before = KinoticCoreAutoConfiguration.class)` to register the
  strategy definition first. This module contributes ONLY
  `internal/api/ElasticServiceDirectoryStrategy` (a `@Component` implementing the
  strategy) which delegates to its own module-private `ServiceDirectoryEntryRepository`
  DAO — the repository implements nothing from core, and no config class is needed: the
  strategy bean's presence activates everything.
  Contract upserts never touch `online`/`lastStatusChange` (single-writer: the liveness
  machinery owns those).
- `internal/api/repositories/ServiceDirectoryEntryRepository` — ES index
  `kinotic_service_directory`. NOTE: the org/project-scoped repository bases route by
  `organizationId` and assume it non-null — system entries break that; route system
  entries by a constant or build on `CrudServiceTemplate` directly (read the base class
  and pick). Queries: scope listing — `findEntriesScopedTo`, tools —
  `findMcpToolsCallableBy` (`mcpExposed && published && online` + the decision #7
  zone matrix: system → all; org scope → `os-api` + `app-api` zones; app scope → own
  `app.<org>.<app>` + `app-api` zones — keep this filter in ONE place with a comment
  pointing at `StompAuthorizerFactory`; `sendAllowed` remains the call-time
  enforcement, this is only the listing view). `findMcpToolsCallableBy` returns
  `Page<McpToolDefinition>` flattened from the matching entries: the ES query MUST
  `_source`-filter to the `mcpTools` field so contracts never leave Elasticsearch on
  the tools path.
- `reportUnreachable(cri)` — debounced per CRI (seconds; Caffeine). Re-checks
  `eventBusService.isAnybodyListening(cri)` and partial-updates the liveness fields to the
  VERIFIED state — never a blind offline write. Safe for any authenticated caller whose
  zone rules allow sending to that CRI (it can only trigger a verification).
- Liveness maintenance lives in CORE (`core/internal/api/directory/` —
  `ServiceLivenessUpdater`, an Ignite `Service` deployed as the HA cluster singleton by
  `DefaultServiceDirectory.deployLivenessSingleton` on `ApplicationReadyEvent`; no
  separate deployer class, and nothing deploys without a strategy bean). It writes
  through `ServiceDirectory.verifyLiveness`/`reconcileLiveness` — the
  `ServiceDirectoryStrategy` stays a hidden detail of the context, with no consumer
  outside it. Updater lifecycle (architecture decision #5): subscribe to
  `monitorServiceListenerEvents()` FIRST, then reconcile (`activeServiceAddresses()`
  snapshot) so no change falls between snapshot and subscription; on each
  `ServiceListenerChange`, re-verify via `isAnybodyListening` (debounced per address)
  and write the VERIFIED state; on `ServiceListenerContinuityLost`, reconcile. All
  liveness layers are uniform: signal → verify → write; no path writes an unverified
  value. Periodic re-reconcile (~10 min). Addresses matching no entry are ignored.

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
(**read these before writing the named-arguments resolver — it joins the existing
composite dispatch; the established `application/json` encoding is a positional array
and stays untouched, return values are unchanged**), `EventConstants`, and the MCP specification's stateless
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
  edges) and wraps the stored `McpToolDefinition`s — including the hints as MCP tool
  `annotations` — in the JSON-RPC result. Definitions are pre-converted and pre-named
  at write time and pre-filtered to online + caller-visible: the gateway does NO
  conversion, NO naming, NO liveness work, holds NO catalog state. No cache in v1 —
  `tools/list` frequency is low; add one later only if profiling demands (YAGNI).
- Named-arguments wire contract (in `kinotic-core`, beside `JacksonArgumentResolver`):
  a new `EventConstants` content type (e.g. `application/x-kinotic-named-json`) whose
  body is a single JSON object keyed by parameter name, and a
  `NamedJsonArgumentResolver` in the `ArgumentResolverComposite` that binds it to the
  invoked method's parameters by name — names discovered from the live `Method` (the
  same source `SchemaFactory` builds the C3 schema from, so schema and binding cannot
  drift). Missing name → null; unknown name → invocation error. This content type is a
  cross-runtime RPC contract: document its exact semantics (the TS service runtime
  implements the same binding from its CLI-generated C3 contract when TS contract sync
  lands — see out of scope).
- `McpToolInvoker` — `tools/call`: resolve the `McpToolDefinition` by `toolName` from
  the caller-visible tools query (names were minted and collision-checked at capture —
  Phase 2; never parse a tool name apart, use the stored `cri`/`functionName`; verify
  the caller may send to that CRI — same zone rules as STOMP); unknown tool → JSON-RPC
  error per spec; forward the MCP `arguments` object VERBATIM as the body with the
  named-arguments content type — the gateway does no argument mapping; mint a unique
  `reply://` CRI (mirror `EndpointConnectionHandler`'s replyToId approach), `listen` on
  it, build the `Event` with sender participant + reply-to + content-type headers, send
  to the `srv://` CRI, await the single reply with a timeout constant (~30s), dispose
  the listener. Outcomes: reply event → MCP text content (JSON payload as-is);
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
- Scope matrix (the security invariants, per decision #7): app participant sees own-app
  + `app-api` tools, never `os-api` tools, never another app's; org participant sees
  `os-api` + `app-api` tools, never any app-zone tools; ownership queries never return
  OS entries; a `@McpTool` method with a streaming return type fails registration.
The boot is the expensive part and happens once; each assertion is cheap.

~10–11 files. Verify: `:kinotic-api-gateway:test` + the e2e. Commit.
**STOP for approval.**

---

## Phase 5 — Introspection tool + OS tool exposure

- Expose a service-listing function as an MCP tool (the directory eating its own dog
  food): a `@Publish`ed service (home: `kinotic-os-api` or `kinotic-domain`, wherever
  fits the module's existing service layout) delegating to core's
  `findEntriesScopedTo`, annotated
  `@McpTool(description = "Lists the services this application provides, with their functions and schemas", readOnlyHint = true)`.
  Publish it in the **`app-api` zone** — per the decision #7 matrix that is the only
  OS-provided zone application participants may call (and org participants may call it
  too). An app user's LLM introspects its own app's services through the same tool
  path, scope-filtered automatically by participant.
- Extend the Phase 4 e2e test class (do not add a new harness): an
  `ApplicationParticipant` MCP session lists the introspection tool, calls it, and
  receives only its own app's entries (never OS entries), while other `app-api` tools
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
  kinotic-github work). No runtime contract hand-off from customer VMs, ever. That work
  also carries the TS side of the named-arguments content type: the TS service runtime
  binds the named-JSON body to parameters using its CLI-generated C3 contract (runtime
  reflection is unavailable under minification). Until then only Java services are
  MCP-callable, so nothing blocks.
- **Prompts / resources** — later (`McpPromptDefinition` as an application-scoped
  entity).
- **Entity data tools with projection** — later, when `JsonEntitiesRepository` methods
  get `@McpTool`: add an `includedFields` parameter to the read methods, flowing into
  the existing (currently unreachable-via-RPC) projection seam
  `EntityContext.getIncludedFieldsFilter()` → ES `_source` filtering. Token-metered LLM
  callers benefit twice: smaller reads, cheaper context.
- **HITL / elicitation** — deferred until the 2026-07-28 stateless spec
  (`IncompleteResult`) finalizes; implemented directly in `McpJsonRpcHandler` then.
- **Stateful streamable-HTTP / SSE transports, `tools/list_changed` notifications.**
- **Telemetry span scoping** — separate work stream.
- **The `vertx-mcp` repository** — untouched (targets Vert.x 4.5.x + MCP SDK 0.11.1,
  both two majors stale).
- **The legacy GraphQL/OpenAPI code in `kinotic-persistence`** — read for reference,
  ported from, never depended on, never modified.
