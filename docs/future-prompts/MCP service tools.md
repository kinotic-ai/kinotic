# MCP Service Tools — Implementation Plan

Expose Kinotic published services as MCP (Model Context Protocol) tools so LLMs can call
them through the API gateway. Built in 5 phases; **STOP after each phase and wait for
explicit approval before starting the next.** Each phase compiles, passes its tests, and
is committed on branch `claude/vertx-mcp-kinotic-gateway-udkpr7` before pausing.

Read the repository `CLAUDE.md` first — every convention in it binds (package layout,
Lombok, comments, code smells, dependency versions, docs-in-sync). Build commands for
this cloud environment are in `CLAUDE.md` (JDK 25 download + `CLAUDE_CLOUD_COMPILE=true`).

File paths below carry line numbers that were accurate when this plan was written —
re-verify before editing; code may have drifted. When this plan and the actual code
disagree, the code wins: read the referenced source before implementing against it.

---

## Architecture (decisions already made — do not relitigate)

The design was worked out in detail beforehand. Summary of the decisions and their
one-line rationales:

1. **Tool unit = a published service function.** A service method opted in via a new
   `@McpTool` annotation becomes an MCP tool. Entity data is NOT special-cased; entities
   are reachable through already-published services like `JsonEntitiesRepository`.
2. **The gateway never reflects on service classes.** Services live on other nodes
   (kinotic servers, worker nodes, customer microVMs). Tool contracts are **data**:
   C3 `ServiceDefinition`s (from `kinotic-idl`) captured where the service registers and
   stored in a durable **ServiceDirectory** (Elasticsearch), modeled on `EntityDefinition`.
3. **Liveness is an `online` flag on the directory entry in ES, maintained by ONE
   cluster-wide updater — never by per-service or per-gateway monitoring.** Gateways
   serve every customer; total services may reach 100k+, so per-CRI
   `monitorListenerStatus` monitors on gateways are forbidden (that primitive stays what
   it is: the per-active-stream tool `ServiceInvocationSupervisor` uses). Instead a
   single **HA cluster singleton** (Ignite service grid,
   `ignite.services().deployClusterSingleton(...)` — `ignite-core` is already a
   dependency; check for an existing kinotic singleton idiom first) consumes one
   bus-wide listener-change stream and applies partial updates to the `online` field in
   ES. Service connect/disconnect events are rare (deploys, restarts, VM lifecycle), so
   write volume is trivial; mass events (a node death) are handled as bulk updates.
   Non-negotiables: **reconciliation on singleton start and on a periodic timer** (scan
   the subs registrations, bulk-correct ES, then apply deltas) — without it, one missed
   event during failover lies forever; and call-time `NO_HANDLERS` → "service offline"
   tool error remains the authoritative guard for the window between a death and the
   flag update. The persisted flag also serves the admin UI and health views — one
   liveness source for every consumer, not an MCP-only mechanism.
4. **Structural scope, not a scope enum.** `ServiceDirectoryEntry` has nullable
   `organizationId`/`applicationId`/`projectId` — both null = SYSTEM (OS services), exactly
   like the participant model (`kinotic-domain/.../api/security/`, validation precedent in
   `KinoticSecurityService`). Non-system queries always filter
   `organizationId == participant's`, so OS entries are structurally unreachable from
   customer *ownership* queries.
5. **Three visibility questions, three filters** (never conflate):
   - *What can I call?* → MCP `tools/list` = (zone send rules from
     `StompAuthorizerFactory`) ∩ (has `@McpTool`). Org/app participants DO see
     MCP-exposed OS (`os-api` zone) tools here — an LLM can only call what is listed.
   - *What belongs to my app?* → directory ownership queries, participant-org filtered;
     OS entries never appear.
   - *Telemetry* → out of scope for this plan (spans carry producing-service scope; a
     later work stream).
6. **MCP protocol via the official Java SDK 2.x** (`io.modelcontextprotocol.sdk:mcp`),
   **stateless streamable-HTTP only**: no sessions, no SSE, no keep-alive — every POST is
   independently authenticated. Fits the clustered gateway (no sticky sessions). Elicitation
   / HITL is deferred until the SDK ships the 2026-07-28 stateless-elicitation spec.
7. **The transport lives in `kinotic-api-gateway/internal`** — NOT the separate
   `vertx-mcp` repo (it targets Vert.x 4.5.x + MCP SDK 0.11.1, both two majors stale;
   do not touch that repo). Extract to a library later if a second consumer appears.
8. **`tools/call` dispatches through the existing RPC path** — build an `Event`, set the
   sender participant, send to the `srv://` CRI over the event bus — never a reflective
   side-door. Auth, arg resolution, and return conversion are reused, mirroring
   `EndpointConnectionHandler.send()` (`kinotic-api-gateway/.../stomp/EndpointConnectionHandler.java:127`).
9. **Two contract writers, both trusted:** OS Java services self-capture at registration
   (this plan); customer TS services arrive later via the CLI codegen → sync pipeline
   (out of scope here — do NOT build a runtime hand-off from customer VMs).
10. **Module dependency constraints (verified):** `kinotic-core` does NOT depend on
    `kinotic-idl`; the gateway depends only on core/domain/os-api/github. Therefore:
    annotation + registration events in core (dependency-free), schema capture + storage
    in `kinotic-persistence` (has core + idl + ES), gateway consumes the directory via a
    core RPC proxy (no new module dependencies anywhere).

---

## Phase 1 — kinotic-idl: C3Type → JSON Schema converter + MCP decorator

Everything else consumes this. Pure transforms → unit tests (per CLAUDE.md test policy).

**Read first:** `kinotic-idl/.../api/converter/IdlConverterStrategy.java`,
`C3TypeConverter.java`, `C3ConversionContext.java`, `IdlConverterFactory.java`,
`internal/api/converter/DefaultIdlConverter.java`, the C3Type subtypes in `api/schema/`
(especially `ObjectC3Type`, `UnionC3Type`, `ReferenceC3Type`, `EnumC3Type`), and
`api/schema/decorators/NotNullC3Decorator.java` as the decorator template. Also study one
existing full strategy for structure: the GraphQL converters in
`kinotic-persistence/.../internal/converters/graphql/` (e.g. `ObjectC3TypeToGql`).

**Create:**

- `kinotic-idl/src/main/java/org/kinotic/idl/api/schema/decorators/McpToolC3Decorator.java`
  — follows `NotNullC3Decorator` exactly (`type = "McpTool"`, targets `FUNCTION`; add the
  enum constant to `DecoratorTarget` if absent). Fields: `String description`,
  `boolean readOnlyHint`, `boolean destructiveHint`, `boolean idempotentHint`.
- `org.kinotic.idl.api.converter.jsonschema.JsonSchemaConverterStrategy`
  implementing `IdlConverterStrategy<ObjectNode, ...>` where `ObjectNode` is
  **`tools.jackson.databind.node.ObjectNode`** (Jackson 3 — what kinotic-idl already uses).
  Do NOT resurrect the dead `internal/support/jsonSchema` model package; emit nodes directly.
- One `C3TypeConverter` per family, own file each (CLAUDE.md: no nested types):
  primitives (string/int/long/short/byte/float/double/boolean/char → `{"type": ...}`),
  `DateC3Type` → `{"type":"string","format":"date-time"}`, `EnumC3Type` → `{"enum":[...]}`,
  `ArrayC3Type` → `{"type":"array","items":...}`, `MapC3Type` →
  `{"type":"object","additionalProperties":...}`, `ObjectC3Type` → `{"type":"object",
  "properties":{...},"required":[...]}` (required = properties carrying
  `NotNullC3Decorator`), `UnionC3Type` → `oneOf`, `VoidC3Type` → `{"type":"null"}`,
  `ReferenceC3Type` → resolve and inline (conversion-context state guards cycles —
  see how the GQL converters use `C3ConversionContext.state()`).
- Property/parameter `metadata` descriptions: if an `AbstractDefinition.metadata` map
  contains a `"description"` key, emit it as JSON Schema `description`.

**Tests:** ONE unit test file converting hand-built C3 trees and asserting emitted JSON
(nesting, required, unions, a reference cycle). This is the plan's only unit test — a
pure transform with no collaborators (the CLAUDE.md exception), and schema emission bugs
are painful to localize from the Phase 4 e2e (they surface as the MCP SDK rejecting a
tool call three layers away). Do not add per-converter test files.

~10–12 files. Verify: `CLAUDE_CLOUD_COMPILE=true ./gradlew :kinotic-idl:test` (with the
JDK 25 flags from CLAUDE.md). Commit. **STOP for approval.**

---

## Phase 2 — kinotic-core: @McpTool annotation + registration events

Core stays idl-free: it only marks and announces; capture happens in Phase 3.

**Read first:** `kinotic-core/.../api/annotations/Publish.java`,
`internal/ServiceRegistrationBeanPostProcessor.java` (registration flow, lines ~42–116),
`api/ServiceRegistry.java`, `api/service/ServiceIdentifier.java`.

**Create / edit:**

- `kinotic-core/src/main/java/org/kinotic/core/api/annotations/McpTool.java`:

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

  Method-level only, on the `@Publish` interface's methods. A service is MCP-exposed iff
  at least one method is annotated. No type-level form (avoids "what does a type-level
  description mean per tool" ambiguity).
- `org.kinotic.core.api.event.ServiceRegisteredEvent` and `ServiceUnregisteredEvent` —
  plain Spring `ApplicationEvent`s carrying the `ServiceIdentifier` and the service
  **interface** `Class<?>`. Published by `ServiceRegistrationBeanPostProcessor` after a
  successful register/unregister (it already has both values in hand). This is the seam
  Phase 3 listens on; Spring events keep core decoupled from whoever consumes them and
  cost nothing when no listener exists.
- `EventBusService.monitorListenerChanges()` returning `Flux<ListenerChange>` (a new
  `ListenerChange` record — address + `ListenerStatus` — own file in `api/event`): the
  bus-wide sibling of `monitorListenerStatus(cri)`. Implemented in
  `DefaultEventBusService` with ONE Ignite cache-entry listener over the vertx
  subscription cache filtered to `srv://` addresses (reuse the machinery at
  `DefaultEventBusService.java:107-160`, minus the per-address filter). Consumed by the
  Phase 3 liveness singleton — this keeps persistence out of core's internal Ignite
  constants.

**Tests:** none in this phase — the event → capture → catalog chain is exercised end to
end by the Phase 4 e2e. Verification here is compile + code review at the approval pause
(be explicit when pausing that this phase's behavior is unverified until Phase 4).

~5–6 files. Verify: `:kinotic-core:compileJava`. Commit. **STOP for approval.**

---

## Phase 3 — kinotic-persistence: ServiceDirectory storage + OS contract capture

**Read first:** `kinotic-persistence/.../api/model/EntityDefinition.java` (the template),
`internal/api/repositories/EntityDefinitionRepository.java` and its base
`AbstractProjectScopedRepository` (in kinotic-domain), `api/services/EntityDefinitionService.java`,
`kinotic-idl/.../api/directory/SchemaFactory.java` (public API — capture entry point),
`kinotic-domain/.../api/security/` participants, `KinoticSecurityService` (scope
validation rules), and `kinotic-api-gateway/.../stomp/StompAuthorizerFactory.java:42-124`
(zone rules the tool-visibility query must mirror; `DomainUtil` zone constants).

**Create (in `kinotic-persistence`, mirroring the EntityDefinition layout):**

- `api/model/ServiceDirectoryEntry.java` — `Identifiable<String>`, structural scope:

  ```java
  private String id;               // non-null scope parts + namespace + name, dot-joined, lowercased
  private String organizationId;   // null for OS services
  private String applicationId;    // null for OS/org-level; never set without organizationId
  private String projectId;        // customer provenance only (null for OS)
  private String namespace, name, version;   // from ServiceIdentifier
  private String description;
  private ServiceDefinition contract;        // the C3 contract, decorators included
  private String sourceVersion;    // commit SHA (synced) or kinotic release (OS)
  private boolean published;
  private boolean mcpExposed;      // denormalized: any function carries McpToolC3Decorator
  private boolean online;          // maintained ONLY by ServiceLivenessUpdater (partial updates)
  private Instant lastStatusChange;
  ```

  Write-path invariant (same rule as `KinoticSecurityService`): `applicationId` without
  `organizationId` is rejected. `mcpExposed` is computed at write, never accepted from input.
- `internal/api/repositories/ServiceDirectoryEntryRepository.java` — ES index
  `kinotic_service_directory`. NOTE: `AbstractProjectScopedRepository` routes by
  `organizationId` and assumes it non-null — OS entries break that assumption. Either
  route system entries by a constant (e.g. `"system"`) or build this repository on
  `CrudServiceTemplate` directly; read the base class and pick what fits. Query methods:
  by-scope ownership lookups and `findMcpToolsVisibleTo(...)` support (below).
- `api/services/ServiceDirectoryService.java` — `@Publish`ed (pick zone consistent with
  other OS services; see `EntityDefinitionService`). Operations:
  - `upsert` / `unpublish` — SYSTEM-participant only.
  - `findForApplication(pageable)` — ownership listing, participant-scoped
    (`ApplicationParticipant` → own org+app; org → own org; system → all).
  - `findMcpTools()` — returns `List<McpToolDescriptor>` for entries that are
    `mcpExposed`, `published`, **and `online`**, **visible to the calling participant**
    per the zone send rules: system → all; org participant → `os-api`-zone entries; app
    participant → own-app entries + `os-api` entries. Keep this filter logic in ONE
    place with a comment pointing at `StompAuthorizerFactory` — `sendAllowed` remains
    the enforcement at call time, this is only the listing view.
- `api/services/McpToolDescriptor.java` (own file, dumb DTO) — everything the gateway
  needs without an idl dependency: `toolName` (sanitized, see Phase 4), `description`,
  `inputSchema` (JSON **string**, produced here with the Phase 1 strategy via
  `IdlConverterFactory`), `cri` (string), `functionName`, `List<String> parameterNames`
  (declared order — needed to map MCP named args to positional), the three hints, and
  `online` left unset (the gateway joins liveness).
- `internal/api/services/OsServiceDirectoryWriter.java` — listens for
  `ServiceRegisteredEvent`; if the interface has any `@McpTool` method: build the
  `ServiceDefinition` via the public `SchemaFactory`, attach `McpToolC3Decorator` to each
  annotated function (mapping the annotation fields), attach the method's `@McpTool`
  description into the function's `metadata` (`"description"`), and upsert a SYSTEM-scoped
  entry (`sourceVersion` = kinotic version). Unregister does NOT delete — the entry stays
  (known-but-offline is a feature); the liveness updater handles availability.
- `internal/api/services/ServiceLivenessUpdater.java` — the HA cluster singleton from
  architecture decision #3 (Ignite service grid; verify how kinotic wires Ignite before
  choosing the exact deployment call). Lifecycle: on start, **reconcile** (page through
  directory entries, check current registrations, bulk partial-update `online` +
  `lastStatusChange`), then consume `eventBusService.monitorListenerChanges()` and apply
  deltas as ES partial updates (update API touching ONLY the two liveness fields — never
  the contract, so it can't clobber a concurrent contract upsert). Re-run reconciliation
  on a slow periodic timer (e.g. 10 min) as the missed-event safety net. Addresses that
  match no directory entry are ignored (services without contracts are simply not
  directory-known).

**Tests:** none in this phase — the capture path, descriptor content, and the
scope/visibility invariants are all asserted in the Phase 4 e2e (which boots real ES and
authenticates as all three participant types). Verification here is compile + code review
at the approval pause.

~9–10 files. Verify: `:kinotic-persistence:compileJava`. Commit. **STOP for approval.**

---

## Phase 4 — kinotic-api-gateway: stateless MCP endpoint

**Read first:** `kinotic-api-gateway/.../internal/endpoints/ApiGatewayVertcleFactory.java`
(route mounting, global handlers), `kinotic-domain/.../api/rest/SuppliesGatewayRoutes.java`
(the mount seam — this phase is its next consumer),
`EndpointConnectionHandler.java` `send()`/`handshake()` (participant resolution, sender
header, reply-to minting/validation at lines ~55–150 and ~335),
`kinotic-core/.../api/security/SecurityService.java:22` (`authenticate(Map<String,String>)`),
`kinotic-core` `internal/api/service/invoker/` `ArgumentResolver`/`ReturnValueConverter`
composites (**match the wire encoding of arguments and return values exactly — read these
before writing the invoker; expect JSON with args in declared order, content-type
`application/json`**), `EventConstants`, and MCP SDK 2.x docs for
`McpStatelessServerTransport` / stateless server builder (SDK is on the 2025-11-25 spec;
tool input validation is on by default).

**Gradle:** add `mcpSdkVersion` to `gradle.properties` (alphabetical) and pin
`io.modelcontextprotocol.sdk:mcp` in the `dependencyManagement` block of
`buildSrc/src/main/groovy/org.kinotic.java-common-conventions.gradle`; the gateway
`build.gradle` declares it versionless (CLAUDE.md rule). Verify with `dependencyInsight`.
The SDK brings Jackson 2 + Reactor transitively; both coexist fine with the gateway's
Jackson 3 (`tools.jackson`) — convert at the boundary via JSON strings.

**Create (all in `kinotic-api-gateway/.../internal/mcp/`):**

- `McpGatewayRoutes implements SuppliesGatewayRoutes` — mounts `POST /mcp` (constant, not
  a property) with a `BodyHandler` sized for JSON-RPC bodies; GET/DELETE on `/mcp` → 405
  (stateless transport has no SSE stream or session to delete). Per request:
  `securityService.authenticate(headers)` → `Participant`; 401 on failure. No session
  reads — every request authenticates independently.
- `VertxStatelessMcpTransport` — the bridge between the SDK's stateless server transport
  SPI and a Vert.x `RoutingContext`: parse the JSON-RPC message from the body, hand it to
  the SDK server, write the JSON response. Small by design (~100–200 lines); if the SDK
  SPI fights Vert.x here, stop and surface the friction rather than forcing it.
- `McpToolCatalog` — obtains descriptors via an RPC proxy of `ServiceDirectoryService`
  (`@Proxy` mechanism, see `RpcServiceProxyBeanFactory` usage elsewhere; the gateway does
  NOT gain a persistence dependency), keyed per participant scope in a Caffeine
  `AsyncLoadingCache` with a short TTL (~30–60s). Liveness costs the gateway nothing:
  `findMcpTools()` already returns only `online:true` entries (the flag is maintained
  cluster-wide by Phase 3's `ServiceLivenessUpdater`), so offline tools are simply
  absent from `tools/list` (kinder to LLMs than listed-but-failing). The gateway holds
  NO liveness state and performs NO subs-map reads. A service dying inside the
  TTL/flag-update window is caught at call time (`NO_HANDLERS` → "service offline" tool
  error).
- `McpToolNames` — reversible mapping between tool name and (CRI, function): many MCP
  hosts enforce `^[a-zA-Z0-9_-]{1,128}$`, so dots/slashes must be encoded
  (e.g. `srv://com.acme.CatalogService/search` ⇄ `com_acme_CatalogService-search`);
  collision-check within a catalog and keep the mapping in the descriptor rather than
  parsing names back apart.
- `McpToolInvoker` — `tools/call`: resolve descriptor; map MCP named arguments to the
  declared positional order via `parameterNames` (missing optional → null; unknown name →
  tool error); encode the body exactly as `ArgumentResolver` expects; mint a unique
  `reply://` CRI (mirror `EndpointConnectionHandler`'s replyToId approach), `listen` on
  it, build the `Event` with sender participant + reply-to + content-type headers, send to
  the tool's `srv://` CRI, await the single reply with a timeout constant (~30s), dispose
  the listener. Map outcomes: reply event → MCP text content (JSON payload as-is);
  `NO_HANDLERS` → tool error "service offline"; error headers per
  `EventConstants` → tool error with the service's message; timeout → tool error.
  Streaming-return functions: excluded at capture time is NOT done in v1 — instead the
  invoker rejects at call time if the reply indicates a stream (check how
  `ServiceInvocationSupervisor` marks streaming replies); note this in the descriptor
  Javadoc.
- `McpServerFactory` (internal/config if it's pure wiring) — builds the SDK stateless
  server per request-scope catalog: `tools/list` from `McpToolCatalog`, `tools/call` via
  `McpToolInvoker`, server name/version constants, hints mapped to SDK `ToolAnnotations`.

**Property:** `kinotic.disableMcp` via `@ConditionalOnProperty` matching the established
`kinotic.disable*` idiom (CLAUDE.md properties rule — this is a deployment-shape flag).

**Docs (same change, CLAUDE.md rule):** add an MCP page under `website/content/**`
covering: the `/mcp` endpoint, auth expectations, `@McpTool` usage on a published service,
tool naming, and the stateless behavior (no sessions). Grep `website/content` for gateway
route docs and match their structure.

**Tests:** ONE e2e test class — this is the verification for Phases 2–4 (the plan's
testing policy: one behavioral test with real infrastructure over per-phase suites; only
Phase 1's pure transform gets a unit test). Boot the server harness (see kinotic-test),
register a test service with two `@McpTool` methods, connect the MCP SDK **client** over
HTTP to `/mcp`, and assert, authenticating as each participant type where relevant:
- `initialize` → `tools/list`: names, schemas (typed properties/required — proving the
  Phase 1 emitter and Phase 3 capture), hints; a registered-but-offline service's tool
  absent from the listing.
- `tools/call`: happy path, unknown-argument error, offline-service error.
- Scope matrix (the Phase 3 security invariants): app participant sees own-app + `os-api`
  tools and never another app's; org participant sees `os-api` tools; ownership queries
  (`findForApplication`) never return OS entries; `upsert` rejected for non-system
  participants and for `applicationId` without `organizationId`.
The boot is the expensive part and happens once; each additional assertion is cheap.

~12 files. Verify: `:kinotic-api-gateway:test` + the e2e. Commit. **STOP for approval.**

---

## Phase 5 — Introspection tool + OS tool exposure

**Create / edit:**

- Annotate `ServiceDirectoryService.findForApplication` with
  `@McpTool(description = "Lists the services this application provides, with their functions and schemas", readOnlyHint = true)`
  — the directory eats its own dog food: an org user's LLM can now introspect their app's
  services through the same tool path, scope-filtered automatically by participant.
- Extend the Phase 4 e2e test class (do not add a new harness): an `ApplicationParticipant`
  MCP session lists the introspection tool, calls it, and receives only its own app's
  entries (never OS entries), while OS `os-api` tools remain callable.
- Decide-with-user during approval: which other OS services get `@McpTool` in this pass
  (candidates: `ApplicationService` creation/listing). Default: only the directory service.
- Docs: extend the MCP page with the introspection tool and an "exposing your own
  services" walkthrough.

~5–8 files. Verify, commit, **STOP** — the user returns to the original session for review.

---

## Explicitly out of scope (do not build)

- **Customer TS service contracts** — arrive via CLI codegen + sync pipeline (in-flight
  kinotic-github work). No runtime contract hand-off from customer VMs, ever.
- **Prompts / resources** — later (`McpPromptDefinition` as an application-scoped entity).
- **HITL / elicitation** — blocked on SDK support for the 2026-07-28 stateless spec.
- **Stateful streamable-HTTP / SSE transports, `tools/list_changed` notifications.**
- **Telemetry span scoping** — separate work stream.
- **The `vertx-mcp` repository** — untouched.
- **`kinotic-core`'s dormant `ServiceDirectory` interface** (`api/ServiceDirectory.java`,
  no impl, no callers) — leave as-is; typed around reflective `ServiceDescriptor`, so
  repurposing it would drag idl types into core. Flag for cleanup, don't do it here.
