# Multi-environment support for Kinotic Applications

This is a phased implementation plan. Each phase compiles and passes tests on its own and is a
reasonable PR boundary. Current-state claims below were verified against the tree at the time of
writing — re-verify with fresh inspection before acting on any of them (files move).

**STOP AT EVERY PHASE BOUNDARY.** When a phase is complete (implemented, tested, committed,
pushed), report what was done and wait for Navid's explicit approval before starting the next
phase. Do not begin any work belonging to a later phase while waiting — no "preparatory"
refactors, no scaffolding. This applies between every pair of consecutive phases, 1 through 8.

## Goal

Support multiple runtime environments (e.g. `development`, `production`) for customer
applications:

1. **One "OS" Elasticsearch cluster** holds every domain object the Kinotic OS manages
   (organizations, applications, projects, IAM, entity *definitions*, workloads, …).
2. **One Elasticsearch cluster per environment** holds customer entity *data* only.
3. **One application-gateway deployment per environment** — the *same* `kinotic-server` binary
   running with `kinotic.role: APPLICATION_GATEWAY` (a Spring profile per role; **no new Gradle
   module**). It is the edge for customer applications: UI ↔ backend-microservice RPC, and
   entity-data reads/writes for that environment.
4. **One `kinotic-server` cluster in the `OS_SERVER` role** remains the single edge for
   `kinotic-frontend` — all OS configuration (orgs, apps, projects, members, entity definitions,
   environments) goes there.

## Target topology

```
                        ┌────────────────────────────────────────────┐
 kinotic-frontend ────► │ kinotic-server, role OS_SERVER              │──► OS ES cluster
 kinotic-cli      ────► │ (Ignite cluster "os"): os_api/system zones, │    (kinotic_* domain
 VmManagers (STOMP)───► │ OIDC login, GitHub, orchestrator,           │     indices)
                        │ entity-definition mgmt                      │
                        └────────────────────────────────────────────┘
                                                                   ▲ read/write OS metadata
 customer UI  ─────────► ┌──────────────────────────────────────────┴─┐
 customer microservices─►│ kinotic-server, role APPLICATION_GATEWAY   │──► env ES cluster
 (STOMP, per env)        │ (per env, Ignite cluster "env-<id>"):      │    (entity data only)
                         │ app login, app.<org>.<app> + app_api       │
                         │ zones, entity data plane (RPC over STOMP)  │
                         └────────────────────────────────────────────┘

One container image; the role decides what a process is.
```

Design decisions baked into this plan (each has an "open questions" entry if Navid may want to
override):

- **Environments are platform-level (SYSTEM-scoped) entities**, a small fixed set operated by
  Kinotic (`development`, `production`), stored in the OS cluster. Not per-organization.
- **Event-bus topology: one Ignite/Vert.x cluster per environment**, separate from the
  kinotic-server cluster. Gateways of the same environment cluster together (so a UI on replica
  A reaches a microservice on replica B). Because each environment has its own bus, the existing
  zone grammar (`app.<orgId>.<appId>`, `app_api`) is unchanged — no collisions between a dev and
  prod instance of the same app, and `@kinotic-ai/persistence`'s hardcoded `app_api` CRI keeps
  working.
- **VmManagers stay on the kinotic-server bus** (they are platform infrastructure, connected via
  `@kinotic-ai/core` STOMP). Customer microservices running *inside* the VMs connect to their
  environment's gateway. This keeps `WorkloadOrchestrationService` → `VmManagerProxy` routing
  untouched.
- **The gateway reads OS metadata directly from the OS ES cluster** (IAM users, applications,
  OIDC configs, entity definitions) rather than proxying RPC to kinotic-server. It therefore has
  two ES clients. Scope its OS-cluster credentials to the indices it needs via ES security roles
  (it must write a few IAM indices: refresh tokens, device grants, pending invites).
- **Entity definitions stay environment-agnostic** (one schema, data per environment). Entity
  index naming inside each env cluster is unchanged: `kinotic_<orgId>.<appId>.<entityName>` —
  the cluster itself is the environment namespace.
- **Entity index creation moves to a gateway-side reconciler** (desired state = published
  `EntityDefinition`s in the OS cluster; each gateway creates missing indices/templates in its
  own cluster lazily + on startup).
- **kinotic-server stops serving the entity data plane.** `kinotic-frontend` browses entity data
  (data views) against the *selected environment's* gateway URL.
- **Entity data is served over the STOMP/RPC path only** (`JsonEntitiesRepository` in `app_api`).
  The GraphQL/OpenAPI/MCP HTTP surfaces are dropped from scope: the code under
  `kinotic-persistence/.../internal/endpoints/` is deliberately dormant (nothing calls
  `PersistenceVerticleFactory`) and is kept for reference — leave it unwired and undeleted.
- **Data Insights is dropped from scope** (owner decision), same reference-code treatment:
  unwire `DataInsightsServiceImpl` and `InsightsContextService` (remove their `@Service`
  stereotypes) so `DataInsightsService` is published nowhere; keep the classes. Its internals
  are already mostly commented out pending Spring AI 2 (`DataInsightsConfiguration`'s
  `ChatClient` bean is disabled), so this completes an unwiring that is half-done today.
- **Single binary, two roles** (owner decision). Every library module already has a whole-module
  gate (`kinotic.disableOsApi`, `disableGithub`, `disablePersistence`, `disableApiGateway`,
  `disableDomain` — see current state), so role composition is the established idiom. A required
  `kinotic.role` enum (`OS_SERVER` | `APPLICATION_GATEWAY`, **no default — missing role fails
  startup**) derives the disable flags and the publishable-zone allowlist *in code*, so the two
  deployment shapes are two tested configurations, not a hand-maintained flag matrix, and the
  fail-open `matchIfMissing = true` defaults on the existing gates can never silently enable an
  OS module on a gateway. Accepted trade-offs of one image: the gateway jar carries the OS admin
  SPA and OS module bytecode (never instantiated); image size is not optimized per role.
- **No OS service *bean* exists in a gateway process — absence, not just authorization.**
  Service publication is bean-driven (`ServiceRegistrationBeanPostProcessor` registers every
  Spring bean implementing a `@Publish` interface), so a bean the role gates keep out of the
  context cannot be invoked no matter what the authorizer does. The role-derived gates + the
  Phase 3 split achieve this (see Phase 4 for the inventory and the startup allowlist that makes
  it a hard invariant instead of an emergent property), and the guards that don't depend on the
  process at all remain underneath: separate env bus (no `os_api` listener to reach), the
  `StompAuthorizer` policy, and OS-cluster ES credentials that physically cannot write OS config
  indices.

---

## Current state (verified anchors)

**No `Environment` concept exists.** Scoping hierarchy is Organization → Application → Project,
via marker interfaces:

```java
// kinotic-domain/src/main/java/org/kinotic/domain/api/model/OrganizationScoped.java
// ApplicationScoped extends OrganizationScoped; ProjectScoped extends ApplicationScoped
```

**Exactly one ES client bean** serves both OS domain objects and customer entity data:

```java
// kinotic-domain/src/main/java/org/kinotic/domain/internal/config/KinoticElasticsearchConfig.java:36
@Bean
public ElasticsearchAsyncClient elasticsearchAsyncClient(JsonpMapper jsonpMapper){
    HttpHost[] hosts = domainProperties.getElasticConnections().stream()...  // kinotic.domain.elastic-connections
```

```java
// kinotic-domain/src/main/java/org/kinotic/domain/internal/api/services/CrudServiceTemplate.java:52
@Component
public class CrudServiceTemplate {
    private final ElasticsearchAsyncClient esAsyncClient;   // the single join point
```

Every OS repository extends `AbstractRepository`/`Abstract{Organization,Application,Project}ScopedRepository`
(`kinotic-domain/.../internal/api/repositories/`) and passes a literal `kinotic_*` index name plus
the shared client/template. The entity-data path consumes the *same* beans:

```java
// kinotic-persistence/.../internal/api/services/EntityServiceCache.java:40-41
private final CrudServiceTemplate crudServiceTemplate;
private final ElasticsearchAsyncClient esAsyncClient;      // same beans as the OS repos
```

Entity index name derivation:

```java
// kinotic-persistence/.../internal/api/services/DefaultEntityDefinitionService.java (validateAndCreate)
entityDefinition.setItemIndex(this.persistenceProperties.getIndexPrefix() + logicalIndexName);
// PersistenceUtil.createEntityDefinitionId → (orgId + "." + appId + "." + name).toLowerCase()
// PersistenceProperties.indexPrefix = "kinotic_" (final)
```

**Zones** (`kinotic-domain/.../internal/utils/DomainUtil.java:35-51`): `os_api`, `app_api`,
`system`, `APP_ZONE_PREFIX="app"` → `app.<orgId>.<appId>`. Per-connection enforcement in
`kinotic-api-gateway/.../internal/endpoints/stomp/StompAuthorizerFactory.java` keyed on participant
type (`SystemParticipant` / `OrganizationParticipant` / `ApplicationParticipant`).

**Edges today:** `kinotic-api-gateway` (a *library* module, pulled into kinotic-server) runs STOMP
on 58503 (`/v1`) + REST under `/api/*` + optional SPA server. An entity-data HTTP surface
(GraphQL/OpenAPI verticles under `kinotic-persistence/.../internal/endpoints/`) exists in code but
is **dormant by design**: `PersistenceVerticleFactory` has no callers, and
`PersistenceInitializer` only registers an ES health check. This code is deliberately kept for
reference — do not wire it up, and do not delete it.

**Module graph:** `kinotic-server` (only `java-application-conventions` module) depends on core,
domain, os-api, persistence, github, api-gateway. `kinotic-orchestrator` is an **orphan** — no
module depends on it, so `WorkloadOrchestrationService` is not currently wired into any deployable.

**Module gates:** every library module is wholly gated by a `kinotic.disable*` property on its
`@Import`ed library class — the seams role composition builds on. Note the fail-open default
(`matchIfMissing = true`: a *missing* flag means *enabled*), which is why the role mechanism
(Phase 4) must set these explicitly rather than relying on profile YAML remembering to:

```java
// kinotic-os-api/src/main/java/org/kinotic/os/KinoticOsApiLibrary.java:14
@ConditionalOnProperty(value = "kinotic.disableOsApi", havingValue = "false", matchIfMissing = true)
// same idiom: kinotic.disableGithub (KinoticGithubLibrary), disablePersistence
// (KinoticPersistenceLibrary), disableApiGateway (KinoticApiGatewayLibrary),
// disableDomain (KinoticDomainLibrary), disableClustering (KinoticProperties)
```

**Deployment:** one ECK ES cluster (`deployment/helm/eck-stack`), one kinotic-server chart
(`deployment/helm/kinotic`), env vars `KINOTIC_DOMAIN_ELASTICCONNECTIONS_*`. Compose:
`deployment/docker-compose/compose.elasticsearch.yml` (single node).

---

## Phase 1 — `Environment` domain model + OS API

New domain object in `kinotic-domain/src/main/java/org/kinotic/domain/api/model/Environment.java`:

```java
@Getter @Setter @Accessors(chain = true) @NoArgsConstructor
public class Environment implements Identifiable<String> {
    private String id;                 // slug of name, minted like Application ids ("development")
    private String name;
    private String description;
    private String gatewayUrl;         // public base URL of this environment's application gateway
    private EnvironmentStatus status;  // enum: PROVISIONING, ACTIVE, DISABLED — new file
    private Date created;
    private Date updated;
}
```

`gatewayUrl` is data, not a Spring property — the frontend and OS services discover per-environment
gateways through it. It is SYSTEM-scoped (plain `Identifiable`, like `Organization`), because
environments are platform infrastructure shared by all orgs.

- `EnvironmentRepository extends AbstractRepository<Environment>` with index `kinotic_environment`
  (`kinotic-domain/.../internal/api/repositories/`, same shape as `OrganizationRepository`).
- Migration: add `kinotic_environment` `CREATE TABLE` to a new
  `kinotic-migration/src/main/resources/migrations/V<next>__environment.sql` (mirror the columns
  from the model; follow `V1__init.sql` style).
- Publish CRUD in **kinotic-os-api** (next to `ApplicationService`):
  `api/services/EnvironmentService` (`@Publish`, `@Version`, zone `os_api`, extends
  `IdentifiableCrudService<Environment, String>`) → `internal/api/services/DefaultEnvironmentService`.
  Authorization: mutations SYSTEM participants only; reads allowed to organization participants
  (the frontend needs the list + `gatewayUrl`). Follow whatever participant-check pattern
  `DefaultApplicationService` uses — verify it rather than inventing one.
- Regenerate/extend `@kinotic-ai/os-api` client proxy for the new service
  (`kinotic-js/workspace/packages/os-api`).

## Phase 2 — split the ES client seam (no behavior change yet)

Goal: make "which cluster" an explicit dependency while kinotic-server keeps today's behavior
(both roles → same cluster) so this phase is a pure refactor. The mechanism is **two named
`ElasticsearchAsyncClient` beans** (plus a `CrudServiceTemplate` per client) — no new abstraction.

1. Demote `CrudServiceTemplate` from `@Component` to a plain class; **both** instances are
   constructed by `@Bean` methods — do not leave one coming from component scanning and add the
   other via `@Bean` (two creation paths for one class, and the scanned instance's target cluster
   would be implicit in `@Primary` resolution instead of visible in a method signature).
   `CrudServiceTemplate` already has a hand-written constructor taking
   `(ElasticsearchAsyncClient, ObjectMapper)`, so no Lombok/`@Qualifier` interaction to solve.
2. In `KinoticElasticsearchConfig`, extract the client construction into a reusable factory
   method and expose the OS pair as `@Primary` named beans:

```java
// kinotic-domain/.../internal/config/KinoticElasticsearchConfig.java  (target shape)
@Bean(OS_ELASTIC_CLIENT) @Primary
public ElasticsearchAsyncClient osElasticClient(JsonpMapper mapper) { ... }        // kinotic.domain.elastic-*

@Bean(OS_CRUD_SERVICE_TEMPLATE) @Primary
public CrudServiceTemplate osCrudServiceTemplate(ElasticsearchAsyncClient osElasticClient, ObjectMapper objectMapper) {
    return new CrudServiceTemplate(osElasticClient, objectMapper);
}
```

   `@Primary` keeps every existing injection point (all OS repositories, `kinotic-github`,
   secret storage, etc.) compiling and resolving to the OS cluster untouched. Declare the bean
   names as constants next to the config that defines them.
3. Switch the entity-data path to qualified injection of a second pair,
   `entityDataElasticClient` / `entityDataCrudServiceTemplate`:

```java
// kinotic-persistence/.../internal/api/services/EntityServiceCache.java  (target shape)
public EntityServiceCache(@Qualifier(ENTITY_DATA_CRUD_SERVICE_TEMPLATE) CrudServiceTemplate crudServiceTemplate,
                          @Qualifier(ENTITY_DATA_ELASTIC_CLIENT) ElasticsearchAsyncClient esAsyncClient,
                          ...)
```

   Same treatment for `DefaultEntityService` construction, `DefaultEntityDefinitionService`'s
   index-lifecycle calls, and `PersistenceInitializer`'s health checks. In this phase the
   entity-data beans are defined as **aliases of the OS pair** (identical behavior); Phase 4
   replaces the alias with construction from `kinotic.applicationGateway.elastic-*` properties.
4. `EntityDefinitionRepository` / `NamedQueriesDefinitionRepository` are **metadata**, not entity
   data — they stay on the OS (`@Primary`) template.

Verify with a full `:kinotic-server:test` run; this phase must be invisible at runtime.

## Phase 3 — split kinotic-persistence wiring into definition-management vs data-plane

Both roles need `kinotic-persistence` (the gateway role needs `EntityDefinition`, converters,
`EntityService`; the OS role needs definition CRUD). Today one library config wires everything
behind `kinotic.disablePersistence`. Split the Spring wiring, keep one Gradle module:

- **Definition-management half** — definition/named-query management services
  (`DefaultEntityDefinitionService`, `NamedQueries*`, their `@Publish` registrations in `os_api`).
- **Data-plane half** — `EntityServiceCache`, `DefaultEntityService` wiring,
  `JsonEntitiesRepository`/`AdminJsonEntitiesRepository` (`app_api` zone),
  `PersistenceInitializer`. The dormant GraphQL/OpenAPI endpoint code stays outside both halves —
  unwired, kept for reference.

Gate each half with the module-gate idiom already in the codebase:
`kinotic.disableEntityDefinitionManagement` / `kinotic.disableEntityDataPlane`
(`@ConditionalOnProperty`, same shape as `KinoticPersistenceLibrary`'s gate, nested under it).
In this phase both default ON — **no behavior change**; the role mechanism (Phase 4) takes over
setting them. Important nuance: **publishing an EntityDefinition no longer creates indices** on
the definition-management side — `DefaultEntityDefinitionService.validateAndCreate` keeps
computing/persisting `itemIndex` but the `createIndex`/`createIndexTemplate`/`createDataStream`
calls move behind the data-plane half (Phase 5 reconciler). Deletion likewise: the OS side
marks/deletes the definition; gateways reconcile index removal.

## Phase 4 — the `APPLICATION_GATEWAY` role (single binary, Spring profiles)

No new Gradle module. The role mechanism lives in kinotic-core; the role's runtime shape is a
Spring profile in `kinotic-server/src/main/resources`.

**The role mechanism.** A required enum on the `kinotic` prefix:

```java
// kinotic-core/.../api/config/KinoticRole.java  (new file)
public enum KinoticRole { OS_SERVER, APPLICATION_GATEWAY }
```

`kinotic.role` has **no default** — a process without a role fails startup with a clear message.
The role→flags mapping lives *in code* (an `EnvironmentPostProcessor` or equivalent that sets the
properties before binding — verify the right Spring Boot 4 hook rather than assuming; the
requirement is that the derived values win over the fail-open `matchIfMissing = true` defaults
and lose to nothing):

| derived value | `OS_SERVER` | `APPLICATION_GATEWAY` |
|---|---|---|
| `kinotic.disableOsApi` | false | **true** |
| `kinotic.disableGithub` | false | **true** |
| `kinotic.disableEntityDefinitionManagement` (Phase 3) | false | **true** |
| `kinotic.disableEntityDataPlane` (Phase 3) | false *(flips to true at the Phase 8 cutover — a one-line code change in this mapping)* | false |
| publishable-zone allowlist | `os_api`, `system` (+ `app_api` until the Phase 8 cutover) | `app_api`, `app` |

```yaml
# kinotic-server/src/main/resources/application-app-gateway.yml  (new profile — sets ONE role value
# plus per-deployment data; it never hand-sets disable flags)
kinotic:
  role: APPLICATION_GATEWAY
  applicationGateway:
    environmentId: ${KINOTIC_ENVIRONMENT_ID}
    elastic-connections:
      - host: ${KINOTIC_ENV_ES_HOST}
        port: 9200
        scheme: http
```

`ApplicationGatewayProperties` (`environmentId`, env-cluster `elasticConnections`/credentials)
binds under `kinotic.applicationGateway` and lives in kinotic-core next to `KinoticProperties`
(both kinotic-domain — JWT env claim — and kinotic-persistence — entity-data clients — need it,
and both depend on core). The OS-cluster connection keeps using `kinotic.domain.elastic-*`. The
data-plane half (Phase 3) now constructs the `entityDataElasticClient`/
`entityDataCrudServiceTemplate` beans from `kinotic.applicationGateway.elastic-*` — one code
path, replacing Phase 2's alias; the base `application.yml` defaults those connections to
`localhost:9200` so single-ES local dev keeps working without extra config.

Work items in this phase:

1. **Zone policy per role.** `StompAuthorizerFactory` currently encodes one global policy. Make
   the policy a bean selected by role: `APPLICATION_GATEWAY` — application participants keep
   today's rules (`app_api.**` + `app.<org>.<app>.**`), organization participants (frontend
   browsing data) get `app_api.**` send only, no participant reaches `os_api`/`system`;
   `OS_SERVER` — conversely drops `app_api` for application participants (after the Phase 8
   cutover; they have no business on the OS bus). Don't fork the class — check how
   `StompAuthorizerFactory` is constructed and pick the smallest seam.
2. **Auth surface per role.** `SuppliesGatewayRoutes` beans are mounted by discovery
   (`ApiGatewayVertcleFactory` iterates all beans), so the REST surface follows which route
   beans exist in the context. GitHub routes disappear via the role-derived `disableGithub`.
   The kinotic-domain handlers need role gates: gateway role mounts app-scope routes only
   (`ApplicationLoginHandler`, `SessionEndpointHandler`, invite acceptance if app-scoped); org
   login, signup, and CLI device-login handlers are OS_SERVER-only. Verify which module
   contributes each `SuppliesGatewayRoutes` bean and gate accordingly.
3. **JWT environment binding.** `KinoticJwtIssuer` (kinotic-domain) mints platform JWTs; signing
   keys come from `platformSecrets` shared across deployments. In the gateway role, add an
   `environmentId` claim at mint and reject tokens whose claim doesn't match this deployment's
   `environmentId` in the `KinoticSecurityService` validation path (a dev token must not work
   against prod). The OS role accepts only tokens without the claim (or ignores — decide during
   implementation and document with the auth docs).
4. **Ignite cluster identity.** A gateway deployment joins Ignite cluster
   `env-<environmentId>`; verify how `KinoticIgniteConfig` names/discovers the cluster
   (`kinotic.ignite.*`) and ensure two clusters on one network can't merge. Also audit what the
   gateway role actually uses Ignite for (session store, eventbus, caches in
   `KinoticIgniteConfigCaches`) — anything OS-specific should not be created on env clusters.
5. **No entity HTTP surface.** The GraphQL/OpenAPI/MCP code stays dormant (see design
   decisions) — the gateway role's only entity-data surface is the `app_api` RPC path.
6. **No SPA in the gateway role.** The role mapping (or profile) sets the web-server verticle
   disabled (`WebServerProperties`); the SPA files remain in the jar — accepted single-image
   trade-off — but are never served.
7. **No OS services published — verified inventory + startup guard.** Everything is on the
   classpath in a single binary, so what matters is which *beans* the role admits. The full
   `@Publish` inventory and its fate in the gateway role:

   | Service | Zone | In a gateway process? |
   |---|---|---|
   | `JsonEntitiesRepository`, `AdminJsonEntitiesRepository`, `NamedQueriesService` (persistence) | `app_api` (explicit `@Zones`) | yes — the data plane |
   | `EntityDefinitionService`, `NamedQueriesDefinitionService`, `MigrationService` (persistence) | `os_api` (package-level `@Zones` in `api/services/package-info.java`) | no — `disableEntityDefinitionManagement` (role-derived) |
   | `ApplicationService`, `ProjectService`, `MemberService`, `LogService`/`LogManager`, `DeviceApprovalService`, `InviteEmailTemplateService`, `KinoticClusterInfoService`, `EnvironmentService` (os-api) | `os_api` | no — `disableOsApi` (role-derived) gates the whole `KinoticOsApiLibrary` |
   | `GitHubAppInstallationService`, `GitHubWebhookEventService`, `GitHubProjectRepoService` (github) | `os_api` | no — `disableGithub` (role-derived) |
   | `WorkloadOrchestrationService`, `VmNodeOrchestrationService` (orchestrator, once Phase 6 wires it in) | verify | no — verify the orchestrator library has (or add) the same `disable*` gate, driven by the role |
   | `DataInsightsService` | `os_api` (`insights/package-info.java`) | published nowhere — dropped from scope (see design decisions) |

   `kinotic-domain` publishes **nothing** (`LocalAuthenticationService` is deliberately not
   `@Publish` — raw passwords never travel over RPC). The **publishable-zone allowlist** turns
   this table from configuration into an invariant: `ServiceRegistrationBeanPostProcessor`
   (kinotic-core) checks every registration against the role-derived allowlist and **fails
   startup** on a violation — a future module gate that's forgotten, renamed, or fail-opens can
   never silently publish an OS service in a gateway process. Client-*hosted* services are
   already constrained by the authorizer (an `ApplicationParticipant` may only host inside its
   own `app.<org>.<app>` zone). Depth beneath all of this: even if the `StompAuthorizer` had a
   bug allowing an `os_api` send, there is no `os_api` listener on the environment bus — the
   send has nothing to reach.

## Phase 5 — entity index reconciler (gateway side)

The OS cluster's `kinotic_entity_definition` index is the desired state; each gateway makes its
own cluster match:

- On `EntityServiceCache` miss (already loads the `EntityDefinition` from the OS cluster via
  `EntityDefinitionRepository`): before constructing the `DefaultEntityService`, ensure
  `itemIndex` exists in the env cluster — reuse the exact create logic that Phase 3 lifted out of
  `DefaultEntityDefinitionService` (mappings via `entityDefinitionConversionService`, data-stream
  vs index decision by `isStream()`).
- On startup + a periodic Vert.x timer: sweep published definitions, create missing indices, and handle definition **updates**
  (mapping changes) the same way the current update path does. Deletions: remove index/template.
- Make creation idempotent and concurrency-safe across gateway replicas (ES create-index races
  return `resource_already_exists_exception` — treat as success).
- **Stranded os_api data service.** `MigrationService` is `os_api`-zoned (published by
  kinotic-server, callable by the frontend via `@kinotic-ai/os-api`) but operates on **entity
  data**, which now lives only in env clusters that kinotic-server cannot reach. It doesn't touch
  OS config, so hosting it on the gateway would not violate the no-OS-services invariant — but it
  would need re-zoning out of `os_api` (e.g. into `app_api` with organization-participant
  authorization, invoked over the frontend's per-environment connection) or deferring. See open
  question 7 — do not silently leave it published-but-broken.

## Phase 6 — environment-scoped workloads

- `Workload` (`kinotic-domain/.../api/model/workload/Workload.java`) gains `environmentId`
  (required when `applicationId` is set); `VmNode` gains `environmentId` (a node belongs to one
  environment). Update the corresponding migration SQL.
- `kinotic-orchestrator` node selection (`DefaultWorkloadOrchestrationService`) filters candidate
  nodes by the workload's `environmentId`.
- **Wire kinotic-orchestrator into kinotic-server** (`kinotic-server/build.gradle`) — it is
  currently depended on by nothing, so orchestration RPCs are dead weight until this lands.
  Confirm with Navid this is intended before doing it. Since the binary is shared, this also
  puts the orchestrator on every gateway process's classpath: give the orchestrator library the
  same `kinotic.disable*` gate as the other modules (if it lacks one) and add it to the role
  mapping (`APPLICATION_GATEWAY` → disabled) **in the same commit** — the zone-allowlist guard
  will fail gateway startup if this is forgotten, which is the guard working as intended.
- Workload provisioning must inject the environment's gateway connection info (host/port of
  `Environment.gatewayUrl` or an internal equivalent) into the VM's env vars so the customer
  microservice's `@kinotic-ai/core` client connects to the right gateway. VmManagers themselves
  keep connecting to kinotic-server.

## Phase 7 — kinotic-frontend + kinotic-js

- Frontend: environment selector (list from the new `EnvironmentService`). OS configuration
  traffic is untouched (same kinotic-server connection). Entity-data views re-point to the
  selected environment's `gatewayUrl` — this is a *second* Kinotic connection
  (`src/util/helpers.ts` currently builds exactly one from `VITE_KINOTIC_*`). The
  `@kinotic-ai/core` singleton (`Kinotic`) must support two live connections or the persistence
  plugin needs a per-connection instance — inspect `KinoticSingleton` before choosing; this is
  the riskiest client-side change.
- The GraphQL/OpenAPI playground pages embed the dropped HTTP surfaces, and the Data Insights UI
  (`src/pages/DataInsights.vue`, `DashboardView.vue`, `SavedWidgets.vue`, widget components/
  entity repos) calls the dropped `DataInsightsService` — hide or remove the frontend entry
  points for both (confirm with Navid which), since there is no backend behind them. Same for
  `IDataInsightsService` in `@kinotic-ai/os-api` (`OsApiPlugin`): stop wiring it into the plugin;
  keep the source for reference.
- Gateway CORS must allow the frontend origin (and later customer app origins — flag as open
  question; likely per-application allowed-origins data on `Application`).
- `@kinotic-ai/persistence` itself is unchanged (same `app_api` zone, same service CRI) — it just
  gets pointed at a gateway instead of kinotic-server.

## Phase 8 — deployment, cutover, local dev + docs

One image (`kinoticai/kinotic-server`) everywhere; the role/profile decides what a container is.

- **Compose** (`deployment/docker-compose/`): second ES service (env cluster) +
  `compose.kinotic-app-gateway.yml` running the *same* kinotic-server image with
  `SPRING_PROFILES_ACTIVE` including `app-gateway` (env=`development`, distinct STOMP/HTTP
  ports). `compose.yml` wires: OS ES + env ES + migration + kinotic-server + one gateway.
- **Helm**: parameterize the existing `deployment/helm/kinotic` chart by role (profile +
  `KINOTIC_APPLICATIONGATEWAY_*` env vars for gateway releases) rather than cloning a second
  chart — one gateway release per environment via values overlays. One eck-stack release per
  environment. NetworkPolicy: env ES reachable only from its gateway namespace, OS ES reachable
  from kinotic + gateway namespaces.
- **Cutover**: flip the `OS_SERVER` role mapping to `disableEntityDataPlane=true` and drop
  `app_api` from its zone allowlist (the one-line code changes flagged in Phase 4), once the
  frontend (Phase 7) talks to gateways for entity data. From here the OS bus carries no entity
  data plane.
- **Local dev without containers**: document running the same app twice from the IDE —
  `KinoticServerApplication` with the default (OS) profile and again with `app-gateway` — against
  one local ES playing both roles (both connection property sets default to `localhost:9200`).
  That keeps a one-ES laptop workflow.
- **Docs**: `website/content/**` — grep for `app_api`, `58503`, login routes, and anything
  describing "the server" as the single endpoint; reconcile every hit. Any pages advertising the
  GraphQL/OpenAPI/MCP HTTP endpoints describe dropped functionality — flag them to Navid (remove
  vs mark unsupported) rather than silently leaving them. Add an environments concept page.

## Testing strategy

Follow the repo rule: behavioral tests through real infrastructure over mocked units.

- **Two-cluster integration test** (Testcontainers, two ES containers): publish an
  `EntityDefinition` via the definition-management path against cluster A, run the data plane +
  reconciler against cluster B, assert the entity index exists only in B and `kinotic_*` domain
  indices only in A, then exercise `JsonEntitiesRepository` CRUD end-to-end.
- **Gateway-role boot test**: start `KinoticServerApplication` with the `app-gateway` profile,
  assert: the `ServiceRegistry` contains **zero** registrations in `os_api`/`system` zones (the
  no-OS-services invariant), os_api CRIs are rejected by the authorizer for an application
  participant, app login routes respond, org signup routes 404.
- **Role guard**: booting with no `kinotic.role` fails startup with a clear message; booting the
  gateway role with a context that registers a bean carrying an out-of-allowlist `@Publish` zone
  fails startup (drive through `ServiceRegistrationBeanPostProcessor` with a real context, not a
  mock).
- **JWT env binding**: token minted with `environmentId=development` rejected by a gateway
  configured as `production` (drive through the real `SecurityService.authenticate`).
- **Existing e2e** (`compose.kinotic-e2e-test.yml`, `kinotic-js/workspace/packages/e2e-tests`):
  extend the compose topology; the JS SDK tests should pass unmodified against a gateway — that
  is the wire-compat check.

## Open questions for Navid (answer before/at handoff)

1. **Environment set**: platform-global fixed set (assumed here) vs per-organization custom
   environments? Per-org changes `Environment` to `OrganizationScoped` and multiplies clusters.
2. **App ↔ environment enablement**: is every application implicitly present in every
   environment (assumed), or is there an explicit "deploy app X to env Y" record?
3. **App end-users per environment?** Assumed shared (`IamUser` unchanged; env binding only via
   the JWT claim from whichever gateway they logged into). If dev/prod user separation is wanted,
   `IamUser` needs an `environmentId` and the login handlers need env context.
4. **Frontend data browsing via env gateway** (assumed) vs kinotic-server holding connections to
   every env cluster. The latter avoids the dual-connection frontend work but couples the OS
   server to every environment's ES.
5. **Wiring kinotic-orchestrator into kinotic-server** (Phase 6) — intended now, or is the
   orchestrator's orphan status deliberate?
6. **Customer app CORS/origins** at the gateway — per-application allowed-origins data?
7. **`MigrationService`**: `os_api`-zoned but operates on entity data that moves to the env
   clusters (see Phase 5). Re-home it on the gateway under an org-participant-authorized
   surface, or defer the functionality for now?

## Guardrails for the implementer

- **One phase per approval.** Finish the phase, report, and wait for Navid's go-ahead before
  touching the next one (see the stop rule at the top of this document). If a phase turns out to
  need something from a later phase, stop and raise it — don't pull the later work forward on
  your own.
- Re-verify every `path:line` anchor in this plan before editing; don't trust the plan over the
  tree.
- CLAUDE.md rules apply in full: Lombok, enums over string constants, `api/` vs `internal/`
  layout, one top-level type per file, no version literals in module `build.gradle`, docs synced
  in the same change, and the smells catalog (in particular: no speculative config beyond the
  role mechanism and per-role flags specified here, no test-only seams).
- `docs/future-prompts/Gateway ABAC.md` describes a planned authorization overhaul that will land
  in this same gateway — keep the authorizer seam (Phase 4 item 1) small and policy-shaped so
  ABAC can replace it without another restructuring.
