# Multi-environment support for Kinotic Applications

This is a phased implementation plan. Each phase compiles and passes tests on its own and is a
reasonable PR boundary. Current-state claims below were verified against the tree at the time of
writing — re-verify with fresh inspection before acting on any of them (files move).

## Goal

Support multiple runtime environments (e.g. `development`, `production`) for customer
applications:

1. **One "OS" Elasticsearch cluster** holds every domain object the Kinotic OS manages
   (organizations, applications, projects, IAM, entity *definitions*, workloads, …).
2. **One Elasticsearch cluster per environment** holds customer entity *data* only.
3. **One `kinotic-application-gateway` deployable per environment** (new Gradle module, an
   application module like `kinotic-server`). It is the edge for customer applications: UI ↔
   backend-microservice RPC, and entity-data reads/writes for that environment.
4. **One `kinotic-server` cluster** remains the single edge for `kinotic-frontend` — all OS
   configuration (orgs, apps, projects, members, entity definitions, environments) goes there.

## Target topology

```
                        ┌────────────────────────────────────────────┐
 kinotic-frontend ────► │ kinotic-server cluster (Ignite cluster "os")│──► OS ES cluster
 kinotic-cli      ────► │  os_api / system zones, OIDC login, GitHub, │    (kinotic_* domain
 VmManagers (STOMP)───► │  orchestrator, entity-definition mgmt      │     indices)
                        └────────────────────────────────────────────┘
                                                                   ▲ read/write OS metadata
 customer UI  ─────────► ┌──────────────────────────────────────────┴─┐
 customer microservices─►│ kinotic-application-gateway (per env,      │──► env ES cluster
 (STOMP, per env)        │ Ignite cluster "env-<id>"): app login,     │    (entity data only)
                         │ app.<org>.<app> + app_api zones, entity    │
                         │ data plane (RPC over STOMP only)           │
                         └────────────────────────────────────────────┘
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
- **No OS service exists on the gateway — absence, not just authorization.** Service publication
  is bean-driven (`ServiceRegistrationBeanPostProcessor` registers every Spring bean implementing
  a `@Publish` interface), so a service that is not on the gateway's classpath/context cannot be
  invoked there no matter what the authorizer does. The gateway's dependency set + the Phase 3
  split already achieve this (see Phase 4 item 5 for the inventory and the startup guard that
  makes it a hard invariant instead of an emergent property).

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
   index-lifecycle calls, and `PersistenceInitializer`'s health checks. The entity-data beans are
   **supplied by the deployable**, not by kinotic-persistence: kinotic-server defines them as
   aliases of the OS beans (identical behavior today); the gateway builds them from its own
   connection properties (Phase 4).
4. `EntityDefinitionRepository` / `NamedQueriesDefinitionRepository` are **metadata**, not entity
   data — they stay on the OS (`@Primary`) template.

Verify with a full `:kinotic-server:test` run; this phase must be invisible at runtime.

## Phase 3 — split kinotic-persistence wiring into definition-management vs data-plane

Both future deployables depend on `kinotic-persistence` (the gateway needs `EntityDefinition`,
converters, `EntityService`; the server needs definition CRUD). Today one auto-config wires
everything. Split the Spring wiring, keep one Gradle module:

- `KinoticEntityDefinitionAutoConfiguration` — definition/named-query management services
  (`DefaultEntityDefinitionService`, `NamedQueries*`, their `@Publish` registrations in `os_api`),
  used by kinotic-server.
- `KinoticEntityDataAutoConfiguration` — the data plane: `EntityServiceCache`,
  `DefaultEntityService` wiring, `JsonEntitiesRepository`/`AdminJsonEntitiesRepository`
  (`app_api` zone), `PersistenceInitializer`. The dormant GraphQL/OpenAPI endpoint code stays
  outside both auto-configs — unwired, kept for reference.

Gate each with `@ConditionalOnProperty` (e.g. `kinotic.persistence.definition-management.enabled`,
`kinotic.persistence.data-plane.enabled`) set in each deployable's `application.yml`. These
properties genuinely differ per deployable, so they pass the "Properties" rule. Important nuance:
**publishing an EntityDefinition no longer creates indices** on the server side —
`DefaultEntityDefinitionService.validateAndCreate` keeps computing/persisting `itemIndex` but the
`createIndex`/`createIndexTemplate`/`createDataStream` calls move behind the data-plane side
(Phase 5 reconciler). Deletion likewise: the OS side marks/deletes the definition; gateways
reconcile index removal.

kinotic-server after this phase: definition-management ON, data-plane OFF (except in a local-dev
profile — see Phase 8).

## Phase 4 — new deployable: `kinotic-application-gateway`

New top-level Gradle module (auto-included by root `settings.gradle` — it just needs its own
`settings.gradle` like the others):

```groovy
// kinotic-application-gateway/build.gradle
plugins { id 'org.kinotic.java-application-conventions' }
dependencies {
    implementation project(':kinotic-core')
    implementation project(':kinotic-domain')       // IAM/auth, OS repositories, app login REST
    implementation project(':kinotic-persistence')  // data plane only (Phase 3 flag)
    implementation project(':kinotic-api-gateway')  // STOMP engine, router, web-server verticle
    // deliberately NOT os-api, NOT github, NOT frontend
}
```

```java
// kinotic-application-gateway/src/main/java/org/kinotic/appgateway/KinoticApplicationGatewayApplication.java
@SpringBootApplication
@EnableKinotic
public class KinoticApplicationGatewayApplication { ... }   // mirror KinoticServerApplication
```

Configuration (`api/config/ApplicationGatewayProperties.java`, prefix
`kinotic.applicationGateway`):

```java
private String environmentId;                        // which Environment this deployment serves
private List<ElasticConnectionInfo> elasticConnections;  // the env's entity-data cluster
private String elasticUsername; private String elasticPassword;
```

The OS cluster connection reuses the existing `kinotic.domain.elastic-*` properties (that's what
all the domain repositories bind to). The gateway defines the `entityDataElasticClient` /
`entityDataCrudServiceTemplate` beans (Phase 2 qualifiers) from `kinotic.applicationGateway.*` —
where kinotic-server aliases them to the OS beans, the gateway points them at its environment's
cluster.

Work items in this phase:

1. **Zone policy.** `StompAuthorizerFactory` currently encodes one global policy. The gateway
   must not expose `os_api`/`system`: application participants keep today's rules
   (`app_api.**` + `app.<org>.<app>.**`); organization participants (frontend browsing data) get
   `app_api.**` send only; system participants are for platform tooling. kinotic-server's policy
   conversely drops `app_api` for application participants (they have no business on the OS bus
   anymore). Make the authorizer policy a bean the deployable supplies rather than forking the
   class — check how `StompAuthorizerFactory` is constructed and pick the smallest seam.
2. **Auth surface.** Mount only the app-scope REST routes (`ApplicationLoginHandler`,
   `SessionEndpointHandler`, invite acceptance if app-scoped). `SuppliesGatewayRoutes` beans are
   mounted by discovery (`ApiGatewayVertcleFactory` iterates all beans), so this falls out of
   which beans exist on the gateway's classpath/context — org login, signup, GitHub, and CLI
   device-login handlers must not be wired here. Verify which module contributes each
   `SuppliesGatewayRoutes` bean and gate accordingly.
3. **JWT environment binding.** `KinoticJwtIssuer` (kinotic-domain) mints platform JWTs; signing
   keys come from `platformSecrets` shared across deployables. Add an `environmentId` claim when
   the issuer runs inside a gateway, and make the gateway's `KinoticSecurityService` validation
   path reject tokens whose claim doesn't match its own `environmentId` (a dev token must not
   work against prod). kinotic-server accepts only tokens without the claim (or ignores — decide
   during implementation and document in AUTH docs).
4. **Ignite cluster identity.** The gateway joins Ignite cluster `env-<environmentId>`; verify
   how `KinoticIgniteConfig` names/discovers the cluster (`kinotic.ignite.*`) and ensure two
   clusters on one network can't merge. Also audit what the gateway actually uses Ignite for
   (session store, eventbus, caches in `KinoticIgniteConfigCaches`) — anything OS-specific should
   not be created on env clusters.
5. **No entity HTTP surface.** The GraphQL/OpenAPI/MCP code stays dormant (see design
   decisions) — the gateway's only entity-data surface is the `app_api` RPC path.
6. **No SPA.** `WebServerProperties.enabled=false` by default; the gateway serves no frontend.
7. **No OS services published — verified inventory + startup guard.** The full `@Publish`
   inventory on the gateway's classpath (core + domain + persistence + api-gateway) is:

   | Service | Zone | On gateway? |
   |---|---|---|
   | `JsonEntitiesRepository`, `AdminJsonEntitiesRepository`, `NamedQueriesService` | `app_api` (explicit `@Zones`) | yes — the data plane |
   | `EntityDefinitionService`, `NamedQueriesDefinitionService`, `MigrationService`, `DataInsightsService` | `os_api` (package-level `@Zones` in `api/services/package-info.java` + `insights/package-info.java`) | no — definition-management auto-config is off (Phase 3) |

   `kinotic-domain` publishes **nothing** (`LocalAuthenticationService` is deliberately not
   `@Publish` — raw passwords never travel over RPC). `kinotic-os-api`, `kinotic-github`, and
   `kinotic-orchestrator` are not dependencies, so their `os_api` services cannot exist here.
   To turn this from an emergent property into an invariant, add a per-deployable **publishable
   zone allowlist** checked in `ServiceRegistrationBeanPostProcessor` (kinotic-core): a
   `KinoticProperties` list (gateway: `app_api`, `app`; kinotic-server: `os_api`, `system`) —
   registration of a bean whose `@Publish` zones fall outside the allowlist **fails startup**,
   so accidentally adding a dependency that carries an OS service breaks the build/boot loudly
   instead of silently widening the gateway's surface. Client-*hosted* services are already
   constrained by the authorizer (an `ApplicationParticipant` may only host inside its own
   `app.<org>.<app>` zone). Note the resulting depth: even if the `StompAuthorizer` had a bug
   allowing an `os_api` send, there is no `os_api` listener on the environment bus — the send
   has nothing to reach.

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
- **Stranded os_api data services.** `MigrationService` and `DataInsightsService` are
  `os_api`-zoned (published by kinotic-server, callable by the frontend via `@kinotic-ai/os-api`)
  but operate on **entity data**, which now lives only in env clusters that kinotic-server cannot
  reach. They don't touch OS config, so hosting them on the gateway would not violate the
  no-OS-services invariant — but they'd need re-zoning out of `os_api` (e.g. into `app_api` with
  organization-participant authorization, invoked over the frontend's per-environment connection)
  or deferring. See open question 7 — do not silently leave them published-but-broken.

## Phase 6 — environment-scoped workloads

- `Workload` (`kinotic-domain/.../api/model/workload/Workload.java`) gains `environmentId`
  (required when `applicationId` is set); `VmNode` gains `environmentId` (a node belongs to one
  environment). Update the corresponding migration SQL.
- `kinotic-orchestrator` node selection (`DefaultWorkloadOrchestrationService`) filters candidate
  nodes by the workload's `environmentId`.
- **Wire kinotic-orchestrator into kinotic-server** (`kinotic-server/build.gradle`) — it is
  currently depended on by nothing, so orchestration RPCs are dead weight until this lands.
  Confirm with Navid this is intended before doing it.
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
- The GraphQL/OpenAPI playground pages embed the dropped HTTP surfaces — hide or remove the
  frontend entry points for them (confirm with Navid which), since there is no backend to point
  them at.
- Gateway CORS must allow the frontend origin (and later customer app origins — flag as open
  question; likely per-application allowed-origins data on `Application`).
- `@kinotic-ai/persistence` itself is unchanged (same `app_api` zone, same service CRI) — it just
  gets pointed at a gateway instead of kinotic-server.

## Phase 8 — deployment + local dev + docs

- **Compose** (`deployment/docker-compose/`): second ES service (env cluster) +
  `compose.kinotic-application-gateway.yml` (env=`development`, distinct STOMP/HTTP ports).
  `compose.yml` wires: OS ES + env ES + migration + kinotic-server + one gateway.
- **Helm**: new chart `deployment/helm/kinotic-application-gateway/` cloned from the kinotic
  chart's shape (config-map env-var emission pattern for `KINOTIC_APPLICATIONGATEWAY_*` +
  existing `KINOTIC_DOMAIN_ELASTIC*` for the OS cluster). One eck-stack release per environment
  (new values overlays); NetworkPolicy: env ES reachable only from its gateway namespace, OS ES
  reachable from kinotic + gateway namespaces.
- **Images**: publish `kinoticai/kinotic-application-gateway` alongside
  `kinoticai/kinotic-server` (mirror whatever builds the server image — check the conventions
  plugin / CI, don't guess).
- **Local dev without containers**: document running `KinoticServerApplication` +
  `KinoticApplicationGatewayApplication` side by side against one local ES playing both roles
  (both property sets point at `localhost:9200`). That keeps a one-ES laptop workflow.
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
- **Gateway boot test**: start `KinoticApplicationGatewayApplication` (test profile), assert:
  the `ServiceRegistry` contains **zero** registrations in `os_api`/`system` zones (the
  no-OS-services invariant), os_api CRIs are rejected by the authorizer for an application
  participant, app login routes respond, org signup routes 404.
- **Zone allowlist guard**: a context that registers a bean with an out-of-allowlist `@Publish`
  zone fails startup (drive through `ServiceRegistrationBeanPostProcessor` with a real context,
  not a mock).
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
7. **`MigrationService` / `DataInsightsService`**: both are `os_api`-zoned but operate on entity
   data that moves to the env clusters (see Phase 5). Re-home them on the gateway under an
   org-participant-authorized surface, or defer the functionality for now?

## Guardrails for the implementer

- Re-verify every `path:line` anchor in this plan before editing; don't trust the plan over the
  tree.
- CLAUDE.md rules apply in full: Lombok, enums over string constants, `api/` vs `internal/`
  layout, one top-level type per file, no version literals in module `build.gradle`, docs synced
  in the same change, and the smells catalog (in particular: no speculative config beyond the
  per-deployable flags specified here, no test-only seams).
- `docs/future-prompts/Gateway ABAC.md` describes a planned authorization overhaul that will land
  in this same gateway — keep the authorizer seam (Phase 4 item 1) small and policy-shaped so
  ABAC can replace it without another restructuring.
