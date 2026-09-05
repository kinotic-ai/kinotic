# Multi-environment support for Kinotic Applications

This is a phased implementation plan. Each phase compiles and passes tests on its own and is a
reasonable PR boundary. Current-state claims below were verified against `develop` at `d2487ff`
("Update Application e2e tests for dash-slugified ids", 2026-07) — re-verify with fresh
inspection before acting on any of them (files move; the zone model was reworked once already:
single `@Zone`, dash names).

**STOP AT EVERY PHASE BOUNDARY.** When a phase is complete (implemented, tested, committed,
pushed), report what was done and wait for Navid's explicit approval before starting the next
phase. Do not begin any work belonging to a later phase while waiting — no "preparatory"
refactors, no scaffolding. This applies between every pair of consecutive phases, 1 through 9.

## Goal

Support multiple runtime environments (e.g. `development`, `production`) for customer
applications:

1. **One "OS" Elasticsearch cluster** holds every domain object the Kinotic OS manages
   (organizations, applications, projects, IAM, entity *definitions*, workloads, …).
2. **One Elasticsearch cluster per environment** holds customer entity *data* only.
3. **One application-gateway deployment per environment** — the *same* `kinotic-server` binary
   running with the `app-gateway` Spring profile (**no new Gradle module, no role concept in
   code** — the profile YAML is the whole difference). It is the edge for customer applications:
   UI ↔ backend-microservice RPC, and entity-data reads/writes for that environment.
4. **One `kinotic-server` cluster with the `os-server` profile** remains the single edge for
   `kinotic-frontend` — all OS configuration (orgs, apps, projects, members, entity definitions,
   environments) goes there.

## Target topology

```
                        ┌────────────────────────────────────────────┐
 kinotic-frontend ────► │ kinotic-server, profile "os-server"         │──► OS ES cluster
 kinotic-cli      ────► │ (own Ignite cluster): management-api/       │    (kinotic_* domain
 VmManagers (STOMP)───► │ system-api zones, OIDC login, GitHub,       │     indices)
                        │ orchestrator, entity-definition mgmt        │
                        └────────────────────────────────────────────┘
                                                                   ▲ read/write OS metadata
 customer UI  ─────────► ┌──────────────────────────────────────────┴─┐
 customer microservices─►│ kinotic-server, profile "app-gateway"      │──► env ES cluster
 (STOMP, per env)        │ (per env, own Ignite cluster via k8s       │    (entity data only)
                         │ Service discovery — Terraform-scoped):     │
                         │ app login, app.<org>.<app> + app-api       │
                         │ zones, entity data plane (RPC over STOMP)  │
                         └────────────────────────────────────────────┘

One container image; the active profile decides what a process is.
```

Design decisions baked into this plan (each has an "open questions" entry if Navid may want to
override):

- **Environments are platform-level (SYSTEM-scoped) entities** (owner decision — confirmed, not
  an open question): a fixed set operated by Kinotic (`development`, `production`), stored in the
  OS cluster. They never belong to an organization.
- **An application reaches an environment through an explicit Promotion** (owner decision), a
  Kinotic-managed flow: sync the app's `EntityDefinition` ES mappings to the target env cluster,
  deploy the application's artifacts to the target environment, and run predefined Git
  branching/tagging actions on the app's project repos. Presence in an environment is therefore
  a *record* written by promotion, not an implicit property of existing (see Phase 9). The
  development environment is assumed to sync continuously on publish (open question).
- **Event-bus topology: one Ignite/Vert.x cluster per environment**, separate from the
  kinotic-server cluster. Isolation is **infrastructure-level, not app config** (owner
  decision): Ignite discovers peers via Kubernetes-topology discovery resolving to a k8s
  Service, and Terraform gives each environment its own discovery Service (prod: a dedicated
  node pool or a separate k8s cluster) — membership is whatever the Service selects, so no
  cluster naming in code. Gateways of the same environment cluster together (so a UI on replica
  A reaches a microservice on replica B). Because each environment has its own bus, the existing
  zone grammar (`app.<orgId>.<appId>`, `app-api`) is unchanged — no collisions between a dev and
  prod instance of the same app, and the `APP_API_ZONE` CRIs built by `@kinotic-ai/persistence`
  keep working.
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
- **ES index/mapping lifecycle is performed by the OS server directly, per environment** (owner
  decision). The OS server holds one **admin-scoped** ES client per environment, used only
  during promotion and dev auto-sync to create/update indices, templates, and mappings. Least
  privilege via ES security roles — each env cluster has two users: the gateway's data user
  (document CRUD on entity indices, no index management) and the OS server's admin user
  (index/template/mapping management, **no document read or write**) — so the OS server can
  shape env clusters but can never touch customer data. What is applied is recorded in
  per-environment **deployment records** (written by promotion / dev auto-sync), *not* inferred
  from the raw set of published `EntityDefinition`s — a definition edited after promotion must
  not leak into prod until the next promotion, and gateways serve entity RPCs from the record's
  snapshot. (The alternative — a gateway-side reconciler pulling desired state, keeping the OS
  server off env clusters entirely — was considered and rejected as more moving parts for
  little gain given the fixed, Kinotic-operated environment set.)
- **kinotic-server stops serving the entity data plane.** `kinotic-frontend` browses entity data
  (data views) against the *selected environment's* gateway URL.
- **Definition management is OS management code, not persistence code** (owner decision): the
  `EntityDefinition`/`NamedQueriesDefinition` models and their repositories move to
  **kinotic-domain** (with the other OS domain objects), the `@Publish` management services move
  to **kinotic-management-api** (with `ApplicationService`/`ProjectService`), and kinotic-persistence
  publishes **only** the `app-api` data-access services. The JS packages already have this shape
  — `IEntityDefinitionService` lives in `@kinotic-ai/management-api` — so the move aligns the server
  modules with it, at the cost of a CRI namespace change (Phase 3).
- **Entity data is served over the STOMP/RPC path only** (`JsonEntitiesRepository` in `app-api`).
  The GraphQL/OpenAPI/MCP HTTP surfaces are dropped from scope: the code under
  `kinotic-persistence/.../internal/endpoints/` is deliberately dormant (nothing calls
  `PersistenceVerticleFactory`) and is kept for reference — leave it unwired and undeleted.
- **Data Insights is dropped from scope** (owner decision), same reference-code treatment:
  unwire `DataInsightsServiceImpl` and `InsightsContextService` (remove their `@Service`
  stereotypes) so `DataInsightsService` is published nowhere; keep the classes. Its internals
  are already mostly commented out pending Spring AI 2 (`DataInsightsConfiguration`'s
  `ChatClient` bean is disabled), so this completes an unwiring that is half-done today.
- **Single binary, two profiles, no role concept in code** (owner decision). Every library
  module already has a whole-module gate (`kinotic.disableOsApi`, `disableManagement`,
  `disablePersistence`, `disableApiGateway`, `disableDomain` — see current state), so
  composition-by-config is the established idiom. Two profiles in
  `kinotic-server/src/main/resources` — `application-os-server.yml` and
  `application-app-gateway.yml` — hold the *entire* per-deployment difference **explicitly in
  YAML**: the disable flags, the auth-route flags, and `kinotic.zones` — one list consumed by
  both the publish guard and the STOMP authorizer (the hardcoded participant rules, temporary
  until Gateway ABAC, are intersected with it). JWT env-claim behavior keys off whether
  `kinotic.applicationGateway.environmentId` is set. No enum, no derivation, no
  deployment-conditional beans — nothing spread through code. The **allowlist startup guard is
  what makes this safe**: any drift — a forgotten flag on a new module, a stray env var
  override — that lets a `management-api`/`system-api` service register in a gateway kills the process at
  boot. Accepted trade-offs of one image: the gateway jar carries the OS admin SPA and OS
  module bytecode (never instantiated); image size is not optimized per deployment shape.
- **No OS service *bean* exists in a gateway process — absence, not just authorization.**
  Service publication is bean-driven (`ServiceRegistrationBeanPostProcessor` registers every
  Spring bean implementing a `@Publish` interface), so a bean the role gates keep out of the
  context cannot be invoked no matter what the authorizer does. The `kinotic.disable*` gates + the
  Phase 3 split achieve this (see Phase 4 for the inventory and the startup allowlist that makes
  it a hard invariant instead of an emergent property), and the guards that don't depend on the
  process at all remain underneath: separate env bus (no `management-api` listener to reach), the
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

**Zones** (`kinotic-domain/.../internal/utils/DomainUtil.java`, constants near the top): `management-api`,
`app-api`, `system-api`, `APP_ZONE_PREFIX="app"` → `app.<orgId>.<appId>`. Names use **dashes**, not
underscores (CRIs are valid URIs by convention; ids are dash-slugified). A service declares
**exactly one zone** via `@Zone` (`kinotic-core/.../api/annotations/Zone.java`) on the type or its
`package-info.java` — type-level overrides package-level, and **no declaration means the service
registers at an un-zoned address**. The JS side mirrors the constants in
`@kinotic-ai/management-api`'s `PlatformZones.ts` (re-exporting `APP_API_ZONE` from
`@kinotic-ai/persistence`). Per-connection enforcement in
`kinotic-api-gateway/.../internal/endpoints/stomp/StompAuthorizerFactory.java` keyed on participant
type (`SystemParticipant` / `OrganizationParticipant` / `ApplicationParticipant`).

**Edges today:** `kinotic-api-gateway` (a *library* module, pulled into kinotic-server) runs STOMP
on 58503 (`/v1`) + REST under `/api/*` + optional SPA server. An entity-data HTTP surface
(GraphQL/OpenAPI verticles under `kinotic-persistence/.../internal/endpoints/`) exists in code but
is **dormant by design**: `PersistenceVerticleFactory` has no callers, and
`PersistenceInitializer` only registers an ES health check. This code is deliberately kept for
reference — do not wire it up, and do not delete it.

**Module graph:** `kinotic-server` (only `java-application-conventions` module) depends on core,
domain, management-api, persistence, github, api-gateway. `kinotic-system-api` is an **orphan** — no
module depends on it, so `WorkloadOrchestrationService` is not currently wired into any deployable.

**Module gates:** every library module is wholly gated by a `kinotic.disable*` property on its
`@Import`ed library class — the seams role composition builds on. Note the fail-open default
(`matchIfMissing = true`: a *missing* flag means *enabled*), which is why the deployment
profiles (Phase 4) set every flag explicitly and the publishable-zone guard backstops a
forgotten one:

```java
// kinotic-management-api/src/main/java/org/kinotic/os/KinoticOsApiLibrary.java:14
@ConditionalOnProperty(value = "kinotic.disableOsApi", havingValue = "false", matchIfMissing = true)
// same idiom: kinotic.disableGithub (KinoticGithubLibrary), disablePersistence
// (KinoticPersistenceLibrary), disableApiGateway (KinoticApiGatewayLibrary),
// disableDomain (KinoticDomainLibrary)
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
- Publish CRUD in **kinotic-management-api** (next to `ApplicationService`):
  `api/services/EnvironmentService` (`@Publish`, `@Version`, zone `management-api`, extends
  `IdentifiableCrudService<Environment, String>`) → `internal/api/services/DefaultEnvironmentService`.
  Authorization: mutations SYSTEM participants only; reads allowed to organization participants
  (the frontend needs the list + `gatewayUrl`). Follow whatever participant-check pattern
  `DefaultApplicationService` uses — verify it rather than inventing one.
- Regenerate/extend `@kinotic-ai/management-api` client proxy for the new service
  (`kinotic-js/workspace/packages/management-api`).

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
   data — they stay on the OS (`@Primary`) template (and move to kinotic-domain in Phase 3).

Verify with a full `:kinotic-server:test` run; this phase must be invisible at runtime.

## Phase 3 — move definition management out of kinotic-persistence

Definitions are OS domain objects; they move to the modules that own OS management. After this
phase, **kinotic-persistence is the data plane only and publishes only `app-api` data-access
services**. The dependency direction supports the move without cycles (persistence → management-api →
domain → core):

- **To kinotic-domain** (with the other OS domain models/repos):
  - `EntityDefinition`, `NamedQueriesDefinition` models — plus whichever supporting model types
    they reference (`MultiTenancyType`, decorators, …); enumerate by following the imports, don't
    guess. kinotic-domain gains a **kinotic-idl dependency** (the `schema` field is
    `ObjectC3Type`) — it has `elasticsearch-java` already.
  - `EntityDefinitionRepository`, `NamedQueriesDefinitionRepository` (they already extend
    domain's `AbstractProjectScopedRepository`; today they just live in the wrong module).
  - The **Elastic mapping conversion** (`EntityDefinitionConversionService.convertToElasticMapping`
    + `internal/converters/elastic` + `common`) — needed by the Phase 5 mapping-sync service,
    which also lands in kinotic-domain (next to the ES client config it uses). Split it out of
    `EntityDefinitionConversionService`, whose GraphQL/OpenAPI conversion parts stay behind in
    kinotic-persistence with the dormant endpoint code they serve.
- **To kinotic-management-api** (with `ApplicationService`/`ProjectService`):
  - `EntityDefinitionService`, `NamedQueriesDefinitionService`, `MigrationService` interfaces +
    `Default*` impls (`@Publish`, `management-api` zone — management-api's `package-info.java` already declares
    it). **Publishing an EntityDefinition no longer creates indices here** —
    `validateAndCreate` keeps computing/persisting `itemIndex` but the
    `createIndex`/`createIndexTemplate`/`createDataStream` calls move into the Phase 5
    mapping-sync service. Deletion likewise.
- **Stays in kinotic-persistence**: `EntityServiceCache`, `DefaultEntityService`,
  `JsonEntitiesRepository`/`AdminJsonEntitiesRepository`/`NamedQueriesService` (`app-api`),
  `PersistenceInitializer`, and the dormant GraphQL/OpenAPI/insights reference code. The
  existing `kinotic.disablePersistence` gate now means exactly "the data plane" — **no new
  flags needed**.

**Wire-contract change**: the management services' CRI namespace changes
(`org.kinotic.persistence.api.services` → `org.kinotic.os.api.services`). Update the proxy
targets in `@kinotic-ai/management-api` in the same change — e.g. `IEntityDefinitionService.ts:71`
currently binds `` `${MANAGEMENT_API_ZONE}.org.kinotic.persistence.api.services.EntityDefinitionService` ``
(the JS *package* layout already matches the target module layout; only the CRI strings move).
Server and JS ship together at this phase boundary. Preserve authorship comments verbatim on
every moved file.

## Phase 4 — the `app-gateway` profile (single binary, pure YAML)

No new Gradle module, and **no role concept in code**. The entire per-deployment difference
lives in two Spring profiles in `kinotic-server/src/main/resources`; everything they set is an
ordinary property, visible and greppable in one file per deployment shape.

**Repo convention (binding):** Spring profiles are *property bundles only* — never gate a bean
on `@Profile` except in test contexts; it's hard to audit. Every bean/module gate is an explicit
`kinotic.*` property read by `@ConditionalOnProperty`, exactly like the existing `disable*`
flags. The profile YAML selects property values; the properties gate behavior.

| profile sets | `application-os-server.yml` | `application-app-gateway.yml` |
|---|---|---|
| `kinotic.disableOsApi` (includes definition management after Phase 3) | false | **true** |
| `kinotic.disableGithub` | false | **true** |
| `kinotic.disablePersistence` (= the data plane after Phase 3) | false *(flips to true at the cloud cutover — companion plan)* | false |
| `kinotic.zones` (publish guard ∩ authorizer) | `management-api`, `system-api` (+ `app-api` until the cloud cutover — companion plan) | `app-api`, `app` |

```yaml
# kinotic-server/src/main/resources/application-app-gateway.yml  (new profile — flags, allowlist,
# and per-deployment data, all visible in one place)
kinotic:
  disableOsApi: true
  disableGithub: true
  disablePersistence: false
  zones: [app-api, app]
  applicationGateway:
    environmentId: ${KINOTIC_ENVIRONMENT_ID}
    elastic-connections:
      - host: ${KINOTIC_ENV_ES_HOST}
        port: 9200
        scheme: http
```

`application-os-server.yml` mirrors it with the OS values; existing deployments add the new
profile to their active set (e.g. `production,os-server`).

The three mechanisms that consume this configuration:

- **`kinotic.zones`** (`List<String>` on `KinoticProperties`) — the zones this deployment
  serves, consumed by two mechanisms: `ServiceRegistrationBeanPostProcessor` fails startup for
  any `@Publish` registration whose zone is outside the list (`app` matching the leading label
  of `app.<org>.<app>` zones) or **un-zoned**; and the `StompAuthorizer` intersects its
  hardcoded per-participant patterns with it (work item 1). When unset, both are unrestricted —
  today's behavior, so plain dev/test contexts keep working; both deployment profiles always
  set it.
- **Auth-route flags** (work item 2) gate which login route groups mount, in the existing
  `disable*` idiom — no `@Profile`, no deployment-conditional beans.
- **`kinotic.applicationGateway.environmentId` presence** drives the JWT env-claim behavior
  (work item 3): set ⇒ this deployment is an environment gateway.

Accepted trade, stated plainly: profile YAML can drift — a *new* module gate added later and
forgotten in a profile is silently ON (`matchIfMissing = true`), the same misconfiguration risk
every env-specific property already carries. The allowlist guard and the gateway boot test
exist precisely to convert the dangerous case into a **startup failure** (the new module's
`management-api`/`system-api` services register → allowlist violation → the process dies loudly). The same
guard neutralizes a stray `KINOTIC_DISABLE*` env var overriding profile YAML.

`ApplicationGatewayProperties` (`environmentId`, env-cluster `elasticConnections`/credentials)
binds under `kinotic.applicationGateway` and lives in kinotic-core next to `KinoticProperties`
(both kinotic-domain — JWT env claim — and kinotic-persistence — entity-data clients — need it,
and both depend on core). The OS-cluster connection keeps using `kinotic.domain.elastic-*`.
kinotic-persistence (data-plane-only after Phase 3) now constructs the `entityDataElasticClient`/
`entityDataCrudServiceTemplate` beans from `kinotic.applicationGateway.elastic-*` — one code
path, replacing Phase 2's alias; the base `application.yml` defaults those connections to
`localhost:9200` so single-ES local dev keeps working without extra config.

Work items in this phase:

1. **Zone authorization = the hardcoded rules ∩ `kinotic.zones`.** `StompAuthorizerFactory`'s
   per-participant-type patterns stay hardcoded exactly as they are — they are temporary until
   the Gateway ABAC work (`docs/future-prompts/Gateway ABAC.md`) replaces them with real
   RBAC-defined path patterns — but every allowed pattern is additionally filtered by the
   deployment's `kinotic.zones` list. The intersection lands every case where it should with
   zero per-deployment policy code:

   | participant | hardcoded today | ∩ gateway `[app-api, app]` | ∩ os-server `[management-api, system-api]` (post-cutover) |
   |---|---|---|---|
   | Organization | `management-api.**` + `app-api.**` | `app-api.**` (frontend data browsing) | `management-api.**` — `app-api` drops out at cutover automatically |
   | Application | `app-api.**` + `app.<org>.<app>.**` | unchanged — the customer surface | ∅ — app participants have nothing on the OS bus |
   | System | everything | `app-api.**` + `app.**` | `management-api.**` + `system-api.**` — VmManager orchestration intact |

   (`reply://` destinations stay exempt from the intersection, as they are from de-scoping
   today.)
2. **Auth-route surface via flags, not profiles.** Org users never log in at an app gateway —
   they log in at the OS server and present the minted JWT to the gateway. So the route split
   is coarse and flag-shaped: GitHub routes already disappear via `disableManagement`; add a small
   number of `disable*`-idiom flags for the kinotic-domain route groups (e.g.
   `kinotic.domain.disableOrgAuthRoutes` covering org login/signup/CLI device-login,
   `kinotic.domain.disableAppAuthRoutes` covering app login) set in the profile YAML — gateway:
   org auth off, app auth on; os-server: the reverse as needed. Verify the actual
   `SuppliesGatewayRoutes` bean inventory and group them into the fewest flags that make sense;
   don't invent one flag per handler.
3. **JWT environment binding.** `KinoticJwtIssuer` (kinotic-domain) mints platform JWTs; signing
   keys come from `platformSecrets` shared across deployments. When
   `kinotic.applicationGateway.environmentId` is set, add an
   `environmentId` claim at mint and reject app-participant tokens whose claim doesn't match
   this deployment's `environmentId` in the `KinoticSecurityService` validation path (a dev
   token must not work against prod). Organization-participant tokens are minted at the OS
   server and carry no env claim — the gateway accepts them (their authorization is already
   narrowed to `app-api.**` by item 1); the OS server accepts only claimless tokens.
4. **Ignite bus isolation is infrastructure-level — no app-side cluster naming.** Per-env bus
   separation comes from the Terraform deployment: Ignite uses Kubernetes-topology discovery
   resolving to a k8s Service, so cluster membership is exactly the pods that Service selects —
   each environment gets its own discovery Service (and prod gets a dedicated node pool or a
   separate k8s cluster). Do not add cluster-name/merge-prevention machinery in code. What
   remains app-side: audit what the gateway actually uses Ignite for (session store, eventbus,
   caches in `KinoticIgniteConfigCaches`) — anything OS-specific should not be created on env
   clusters — and for local dev/compose, running os-server + app-gateway on one machine must
   not form one cluster (use the existing `kinotic.ignite.*` discovery settings per process).
5. **No entity HTTP surface.** The GraphQL/OpenAPI/MCP code stays dormant (see design
   decisions) — the gateway's only entity-data surface is the `app-api` RPC path.
6. **No SPA in the gateway.** The app-gateway profile sets the web-server verticle
   disabled (`WebServerProperties`); the SPA files remain in the jar — accepted single-image
   trade-off — but are never served.
7. **No OS services published — verified inventory + startup guard.** Everything is on the
   classpath in a single binary, so what matters is which *beans* the disable props admit. The full
   `@Publish` inventory and its fate in a gateway process:

   | Service | Zone | In a gateway process? |
   |---|---|---|
   | `JsonEntitiesRepository`, `AdminJsonEntitiesRepository`, `NamedQueriesService` (persistence — its entire published surface after Phase 3) | `app-api` (explicit `@Zone`) | yes — the data plane |
   | `ApplicationService`, `ProjectService`, `MemberService`, `LogService`/`LogManager`, `DeviceApprovalService`, `InviteEmailTemplateService`, `KinoticClusterInfoService`, `EntityDefinitionService`/`NamedQueriesDefinitionService`/`MigrationService` (moved in Phase 3), `EnvironmentService` (Phase 1), `PromotionService` (Phase 9) — all management-api | `management-api` | no — `disableOsApi` (app-gateway profile) gates the whole `KinoticManagementApiLibrary` |
   | `GitHubAppInstallationService`, `GitHubWebhookEventService`, `GitHubProjectRepoService` (github) | `management-api` | no — `disableManagement` (app-gateway profile) |
   | `WorkloadOrchestrationService`, `VmNodeOrchestrationService` (orchestrator, once Phase 6 wires it in) | `system-api` (`@Zone` in `api/workload/package-info.java`) | no — verify the orchestrator library has (or add) the same `disable*` gate, set in the deployment profiles |
   | `DataInsightsService` | `management-api` (`insights/package-info.java`) | published nowhere — dropped from scope (see design decisions) |

   `kinotic-domain` publishes **nothing** (`LocalAuthenticationService` is deliberately not
   `@Publish` — raw passwords never travel over RPC). The **publishable-zone allowlist** turns
   this table from configuration into an invariant: `ServiceRegistrationBeanPostProcessor`
   (kinotic-core) checks every registration's single `@Zone` against `kinotic.zones`
   and **fails startup** on a violation — a future module gate that's forgotten, renamed, or
   fail-opens can never silently publish an OS service in a gateway process. A service with
   **no** `@Zone` registers at an un-zoned address (per the `Zone` javadoc): treat un-zoned
   registrations as allowlist violations whenever the allowlist is set — every service a gateway publishes
   must be deliberately zoned. Client-*hosted* services are
   already constrained by the authorizer (an `ApplicationParticipant` may only host inside its
   own `app.<org>.<app>` zone). Depth beneath all of this: even if the `StompAuthorizer` had a
   bug allowing a `management-api` send, there is no `management-api` listener on the environment bus — the
   send has nothing to reach.

## Phase 5 — deployment records + per-environment mapping sync (OS side)

Desired state per environment is a **deployment record**, not the raw definition set — this is
what makes promotion (Phase 9) possible: prod's indices reflect the last *promoted* state, never
a definition edit made after promotion. The OS server applies that state to env clusters
directly, synchronously, through admin-scoped clients.

New domain object in kinotic-domain (`kinotic_application_deployment` index + migration SQL):

```java
@Getter @Setter @Accessors(chain = true) @NoArgsConstructor
public class ApplicationDeployment implements ApplicationScoped<String> {
    private String id;                     // <organizationId>.<applicationId>.<environmentId>
    private String organizationId;
    private String applicationId;
    private String environmentId;
    private List<EntityDefinition> entityDefinitions;  // snapshot taken at deploy/promotion time
    private DeploymentStatus status;       // enum: PENDING, SYNCING, ACTIVE, FAILED — new file
    private Date created; private Date updated;
}
```

Whether the record embeds full definition snapshots (shown) or references immutable definition
versions is open question 8 — resolve it with Navid before implementing this phase.

- **Per-environment admin clients (OS server only).** Properties map the fixed environment set to
  cluster connections — `kinotic.environmentClusters.<envId>.{elastic-connections, username,
  password}` — and a bean (e.g. `EnvironmentClusterClients`, `Map<String, CrudServiceTemplate>`
  built with the Phase 2 factory method) exposes one admin client per environment. The bean
  exists only when `kinotic.environmentClusters` is configured — the app-gateway profile never
  sets it. A publish/promotion targeting an environment with no configured cluster
  fails fast with a clear message.
- **Mapping sync service (OS server, kinotic-domain internal).** The index/template/create/update
  logic Phase 3 lifted out of `DefaultEntityDefinitionService` (mappings via the Elastic
  conversion moved to kinotic-domain, data-stream vs index decision by `isStream()`) becomes a
  domain-internal service — next to `EnvironmentClusterClients` and the repositories it reads —
  invoked with a *target environment's* template by its two callers, `DefaultEntityDefinitionService`
  (management-api, dev auto-sync) and the promotion job (Phase 9): apply a deployment record's
  definitions to that env cluster —
  create missing indices/templates, update mappings, remove indices for withdrawn definitions.
  Idempotent (ES create-index races return `resource_already_exists_exception` — treat as
  success); errors surface synchronously to the caller (publish flow or promotion job), which
  sets the record `status`.
- **Dev auto-sync (assumed, open question)**: publishing an `EntityDefinition` upserts the
  `development` deployment record for its application and applies the mappings to the dev
  cluster in the same operation — dev goes through the same record + sync path promotion uses,
  just triggered by publish.
- **Gateway side**: `EntityServiceCache` serves entity RPCs from its environment's **deployment
  record** definitions, not the live `kinotic_entity_definition` docs — wire schema and index
  mappings must come from the same snapshot or they drift within an environment. A cache miss
  with no deployment record (or a missing index) is an error to surface, not something the
  gateway repairs — the OS server owns index lifecycle; gateways never create indices.
- **ES least privilege** (per env cluster): gateway data user — document CRUD on entity
  indices, no index management; OS admin user — index/template/mapping management, no document
  read/write. Wire these as distinct users in the cloud ES clusters (companion cloud-deployment
  plan); the local single-ES compose does not need them.
- **Stranded management-api data service.** `MigrationService` is `management-api`-zoned (published by
  kinotic-server, callable by the frontend via `@kinotic-ai/management-api`) but operates on **entity
  data**, which now lives only in env clusters that kinotic-server cannot reach. It doesn't touch
  OS config, so hosting it on the gateway would not violate the no-OS-services invariant — but it
  would need re-zoning out of `management-api` (e.g. into `app-api` with organization-participant
  authorization, invoked over the frontend's per-environment connection) or deferring. See open
  question 5 — do not silently leave it published-but-broken.

## Phase 6 — environment-scoped workloads

- `Workload` (`kinotic-domain/.../api/model/workload/Workload.java`) gains `environmentId`
  (required when `applicationId` is set); `VmNode` gains `environmentId` (a node belongs to one
  environment). Update the corresponding migration SQL.
- `kinotic-system-api` node selection (`DefaultWorkloadOrchestrationService`) filters candidate
  nodes by the workload's `environmentId`.
- **Wire kinotic-system-api into kinotic-server** (`kinotic-server/build.gradle`) — it is
  currently depended on by nothing, so orchestration RPCs are dead weight until this lands.
  Confirm with Navid this is intended before doing it. Since the binary is shared, this also
  puts the orchestrator on every gateway process's classpath: give the orchestrator library the
  same `kinotic.disable*` gate as the other modules (if it lacks one) and set it in
  `application-app-gateway.yml` (disabled) **in the same commit** — the zone-allowlist guard
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
  `IDataInsightsService` in `@kinotic-ai/management-api` (`ManagementApiPlugin`): stop wiring it into the plugin;
  keep the source for reference.
- Gateway CORS must allow the frontend origin (and later customer app origins — flag as open
  question; likely per-application allowed-origins data on `Application`).
- `@kinotic-ai/persistence` itself is unchanged (same `app-api` zone, same service CRI) — it just
  gets pointed at a gateway instead of kinotic-server.

## Phase 8 — local compose, local dev + docs

Local is deliberately simple: **only the `development` environment exists locally, and one
kinotic-server runs everything.** The cloud deployment (per-env clusters, gateway releases,
Terraform, and the cutover) is captured in the companion plan
`docs/future-prompts/Multi-environment cloud deployment.md` — it is blocked on infra budget and
is NOT part of this plan's phases.

- **Compose** (`deployment/docker-compose/`): stays single-ES, single-server. The one
  kinotic-server runs with **no deployment profile** — the base defaults (all modules enabled,
  `kinotic.zones` unset = unrestricted) *are* the all-in-one shape. The only addition:
  `kinotic.environmentClusters.development` points at the same local ES, so dev auto-sync and
  the deployment-record path work locally against one cluster playing both roles.
- **Local dev without containers**: same picture from the IDE — one `KinoticServerApplication`,
  no deployment profile, one local ES at `localhost:9200` serving as both OS and dev-env
  cluster (both connection property sets default there). Running a second process with the
  `app-gateway` profile is possible for gateway-specific work but is not the daily workflow.
- **Docs**: `website/content/**` — grep for `app-api`, `58503`, login routes, and
  anything describing "the server" as the single endpoint; reconcile every hit. Any pages
  advertising the GraphQL/OpenAPI/MCP HTTP endpoints describe dropped functionality — flag them
  to Navid (remove vs mark unsupported) rather than silently leaving them. Add an environments
  concept page.

The `application-os-server.yml` / `application-app-gateway.yml` profiles built in Phase 4 are
exercised locally only by tests (the gateway boot test, the two-cluster and promotion
integration tests run on Testcontainers, not compose) until the cloud deployment activates
them.

## Phase 9 — Promotion (Develop → Prod)

A Kinotic-managed, predefined flow that moves an application into a target environment. Runs
entirely on the OS server (it needs kinotic-github, the orchestrator, and OS data — all
os-server modules). Builds on Phase 5 (deployment records + mapping sync) and Phase 6 (env-scoped
workloads).

**Service surface**: `PromotionService` (`@Publish`, zone `management-api`) in kinotic-management-api —
`promote(organizationId, applicationId, targetEnvironmentId)` returning progress the frontend
can render, plus promotion history/status reads. Authorization: organization participants for
their own applications (verify against the participant-check pattern in
`DefaultApplicationService`).

**Flow engine**: the Grind job framework in kinotic-system-api is the natural fit — a
predefined multi-step flow with progress and diagnostics is exactly its shape:

```java
// kinotic-system-api/.../api/grind/JobService.java:18
Flux<Result<?>> assemble(JobDefinition jobDefinition);   // Steps/Tasks with Progress + Diagnostic results
```

Verify it end-to-end before committing to it (it has `src/testx/` tests but has never run inside
a deployable — Phase 6 wires the module in); if it proves unfit, a plain service method
executing the steps sequentially is acceptable — do not build a *third* flow mechanism.

**The predefined steps** (Kinotic-managed; not user-customizable in this iteration):

1. **Git actions** — branch/tag each of the application's project repos per the predefined
   promotion convention (e.g. tag the promoted commit, cut/advance a release branch — exact
   convention from Navid at implementation time). Uses kinotic-github
   (`GitHubProjectRepoService` / the GitHub client it wraps); `Project` already carries
   `repoFullName`/`repoDefaultBranch`.
2. **Definition sync** — snapshot the application's current `EntityDefinition`s into the target
   environment's `ApplicationDeployment` record and apply the mappings to the target env
   cluster via the Phase 5 mapping-sync service (the env's admin-scoped client). Synchronous:
   mapping conflicts and cluster errors fail this step directly with the ES error detail, and
   the step sets the record `status` (`ACTIVE` on success, `FAILED` with detail otherwise).
3. **Artifact deployment** — create/update the application's `Workload`s with
   `environmentId = target` from the promoted artifacts, via `WorkloadOrchestrationService`
   (Phase 6). What an "artifact" is (image reference, who built it, where it's recorded) is
   open question 10 — pin it down before this phase.
4. **Record + report** — promotion history persisted (who, when, what versions, outcome), and
   the deployment record is the durable statement of what's live in the environment.

**Ordering/rollback**: steps are sequential with fail-fast; a failed promotion leaves the
previous deployment record intact (snapshot-swap, not in-place edit) so the environment keeps
serving the last good state. Rollback = re-promote a previous snapshot; don't build a separate
rollback mechanism.

**Frontend**: promotion trigger + progress/history UI (extends the Phase 7 environment
selector); regenerate `@kinotic-ai/management-api` for `PromotionService`.

## Testing strategy

Follow the repo rule: behavioral tests through real infrastructure over mocked units.

- **Two-cluster integration test** (Testcontainers, two ES containers): publish an
  `EntityDefinition` via the definition-management path against OS cluster A with the
  environment cluster B configured in `kinotic.environmentClusters`; assert the mapping-sync
  service creates the entity index only in B and `kinotic_*` domain indices exist only in A,
  then exercise `JsonEntitiesRepository` CRUD (data plane pointed at B) end-to-end.
- **Gateway boot test**: start `KinoticServerApplication` with the `app-gateway` profile,
  assert: the `ServiceRegistry` contains **zero** registrations in `management-api`/`system-api` zones (the
  no-OS-services invariant), management-api CRIs are rejected by the authorizer for an application
  participant, app login routes respond, org signup routes 404.
- **Allowlist guard**: with `kinotic.zones` set, a context that registers a bean
  carrying an out-of-list (or missing) `@Zone` fails startup (drive through
  `ServiceRegistrationBeanPostProcessor` with a real context, not a mock).
- **JWT env binding**: token minted with `environmentId=development` rejected by a gateway
  configured as `production` (drive through the real `SecurityService.authenticate`).
- **Promotion end-to-end** (two ES containers, os-server + app-gateway processes): publish a
  definition (dev auto-sync creates indices in the dev cluster), promote to `production`, assert
  the prod cluster gains the promoted mappings and the deployment record goes `ACTIVE`; then
  edit the definition *without* promoting and assert the prod cluster is untouched — that last
  assertion is the whole point of deployment records. Git steps against a test repo or stubbed
  at the GitHub client boundary (the flow logic, not GitHub, is under test).
- **Existing e2e** (`compose.kinotic-e2e-test.yml`, `kinotic-js/workspace/packages/e2e-tests`):
  the compose topology stays all-in-one (Phase 8), so the suite must keep passing unmodified
  throughout — that is the wire-compat check for every phase. JS-SDK-against-a-gateway coverage
  comes from a Testcontainers-based run of the gateway profile (or waits for the cloud
  deployment) — don't add a second compose stack for it.

## Decided (owner answers — no longer open)

- Environments are a system-level construct with a fixed, Kinotic-operated set — never
  per-organization.
- An application reaches an environment via explicit Promotion (Phase 9): definition-mapping
  sync to the target env cluster, artifact deployment, and predefined Git branching/tagging.
- Single binary with per-deployment Spring profiles, all differences in YAML (Phase 4);
  GraphQL/OpenAPI/MCP and Data Insights dropped as dormant reference code.

## Open questions for Navid (answer before/at handoff)

1. **App end-users per environment?** Assumed shared (`IamUser` unchanged; env binding only via
   the JWT claim from whichever gateway they logged into). If dev/prod user separation is wanted,
   `IamUser` needs an `environmentId` and the login handlers need env context.
2. **Frontend data browsing via env gateway** (assumed) vs kinotic-server holding connections to
   every env cluster. The latter avoids the dual-connection frontend work but couples the OS
   server to every environment's ES.
3. **Wiring kinotic-system-api into kinotic-server** (Phase 6) — intended now, or is the
   orchestrator's orphan status deliberate? (Phase 9 assumes it: both the Grind flow engine and
   `WorkloadOrchestrationService` live there.)
4. **Customer app CORS/origins** at the gateway — per-application allowed-origins data?
5. **`MigrationService`**: `management-api`-zoned but operates on entity data that moves to the env
   clusters (see Phase 5). Re-home it on the gateway under an org-participant-authorized
   surface, or defer the functionality for now?
6. **Dev auto-sync**: does publishing an `EntityDefinition` auto-update the `development`
   environment (assumed in Phase 5), or does even dev require an explicit deploy action?
7. **Promotion path**: is the flow strictly `development → production`, or is the environment
   ordering something the `Environment` model must express (a field/sequence) once more
   environments exist? With a fixed set, the flow can start as a constant.
8. **Definition snapshot semantics** (Phase 5): does the `ApplicationDeployment` record embed
   full `EntityDefinition` snapshots (assumed — simple, immutable) or reference versioned
   definitions (requires definition versioning that doesn't exist today)?
9. **Git promotion convention** (Phase 9): the exact branch/tag actions per promotion (tag name
   scheme, release branch policy, which commit is promoted).
10. **What is an application artifact** (Phase 9): image reference on `Workload`? Built by what
    (kinotic-github CI? external)? Where is the promotable artifact version recorded?

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
  per-profile flags and allowlist specified here, no test-only seams).
- `docs/future-prompts/Gateway ABAC.md` describes a planned authorization overhaul that will land
  in this same gateway — keep the authorizer seam (Phase 4 item 1) small and policy-shaped so
  ABAC can replace it without another restructuring.
