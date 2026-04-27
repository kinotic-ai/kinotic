# kinotic-domain

A Spring Boot library that provides the core domain model, CRUD service abstractions, and operational services for the Kinotic platform.

## Overview

kinotic-domain solves the problem of having a single, consistent definition of the platform's first-class entities — Applications, Projects, and cluster-level operational data — and of providing reusable patterns for persisting and querying those entities. Without this module every Kinotic component would need to define its own persistence layer and its own conventions for paging, sorting, and search, leading to fragmentation. kinotic-domain centralises all of that.

The module acts as a foundational library in the Kinotic architecture. It defines the data-access contract (the `CrudService` / `IdentifiableCrudService` hierarchy) that higher-level modules program against, and it supplies the concrete Elasticsearch-backed implementations of those contracts for the built-in domain types (`Application` and `Project`). It also exposes two operational service interfaces — `ClusterInfoService` and `LogManager` — that give the rest of the platform a uniform view of the running Ignite cluster and of the active logging configuration.

Persistence can be disabled for environments that do not need it. Setting `kinotic.disablePersistence=true` prevents `KinoticDomainLibrary` from loading, which in turn suppresses the entire component-scan and stops all Elasticsearch / Ignite wiring from being attempted. This makes the module safe to include as a compile-time dependency in contexts that only need the API types.

Jackson serialization for the module's own types — `Pageable`, `Page`, `SearchComparator`, and `RawJson` — is provided automatically via a registered `SimpleModule` (`KinoticDomainModule`). Consumers do not need to configure any custom serializers or deserializers for these types.

## Key Concepts

- **`Application`** — A top-level organisational unit identified by a `String` id. The id must start with a letter and contain only letters, digits, periods, underscores, or dashes. An application owns zero or more Projects and cannot be deleted while any projects remain.

- **`Project`** — A named artefact that belongs to exactly one Application. Its id is derived automatically from the parent application id and the slugified project name when not supplied explicitly. The `ProjectType` enum (`TYPESCRIPT`, `GRAPHQL`, `GRAPHICAL`, `ELASTICSEARCH`, `DATA_INSIGHTS`) records the project's source of truth.

- **`CrudService<T, ID>` / `IdentifiableCrudService<T, ID>`** — The public persistence contract. `CrudService` provides `save`, `findById`, `count`, `deleteById`, `findAll(Pageable)`, and `search(String, Pageable)`, all returning `CompletableFuture`. `IdentifiableCrudService` extends this with a `create` default method that enforces uniqueness: it checks for an existing document before calling `save` and fails the future if one already exists.

- **`Pageable` / `OffsetPageable` / `CursorPageable`** — The paging contract and its two concrete strategies. `OffsetPageable` uses a zero-based page number and maps to Elasticsearch's `from` / `size` parameters. `CursorPageable` uses the Elasticsearch `search_after` mechanism, storing the sort-field values of the last seen document as a JSON string cursor. A `Sort` is mandatory when using cursor paging.

- **`Page<T>` / `CursorPage<T>`** — The two result envelope types. `Page` carries a content list and a total element count. `CursorPage` extends `Page` and carries the opaque cursor string for the next page; a `null` cursor indicates the final page.

- **`ClusterInfoService`** — A single-method service that returns a `ClusterInfo` snapshot describing the Apache Ignite cluster (topology version, cluster state, per-node details). If Ignite is not present on the classpath or is not wired into the Spring context the implementation returns a static `clusteringEnabled=false` response rather than failing.

- **`LogManager`** — A per-node service for querying and modifying the runtime log level of any named logger or logger group. The `@Scope` annotation on `nodeId()` signals to kinotic-core that each physical node exposes its own instance, enabling targeted RPC routing.

- **`RawJson`** — A value type that carries an already-serialized JSON payload as a raw byte array. It implements `JsonpSerializable` so it can be stored in and retrieved from Elasticsearch without being re-parsed into an intermediate object graph.

## Configuration

### Spring Properties

| Property | Default | Effect |
|---|---|---|
| `kinotic.disablePersistence` | `false` | Set to `true` to prevent `KinoticDomainLibrary` from loading. All Elasticsearch wiring and component scanning is suppressed. |

### Auto-configuration Classes

`KinoticDomainAutoConfiguration` (`org.kinotic.domain_autoconfig`) is the Spring Boot auto-configuration class. It is intentionally placed in a separate package so that the Spring component scan initiated by `KinoticDomainLibrary` does not double-register it. It imports `KinoticDomainLibrary`, which carries the `@ConditionalOnProperty` guard.

`KinoticDomainJacksonConfig` is not conditional; it is always registered as long as the library is active, so the `KinoticDomainModule` Jackson customisations are always present.

## Usage Example

```java
@Service
public class OnboardingService {

    private final ApplicationService applicationService;
    private final ProjectService projectService;

    public OnboardingService(ApplicationService applicationService,
                             ProjectService projectService) {
        this.applicationService = applicationService;
        this.projectService = projectService;
    }

    public CompletableFuture<Project> onboard(String appId, String projectName) {
        return applicationService.createApplicationIfNotExist(appId, "My application")
                .thenCompose(app -> {
                    Project project = new Project()
                            .setApplicationId(app.getId())
                            .setName(projectName)
                            .setSourceOfTruth(ProjectType.TYPESCRIPT);
                    return projectService.createProjectIfNotExist(project);
                });
    }

    public CompletableFuture<Page<Project>> listProjects(String appId, int page, int size) {
        Pageable pageable = Pageable.create(page, size, Sort.by(Direction.ASC, "name"));
        return projectService.findAllForApplication(appId, pageable);
    }
}
```

## IAM Authentication

Kinotic supports email/password and OIDC at the Organization and Application scopes. System-scope auth is a separate path. The IAM data model lives in this module — `IamUser`, `IamCredential`, `OidcConfiguration`, `PendingRegistration`, `SignUpRequest`, plus the corresponding services.

For the architecture (scope isolation, credential separation, why standalone `OidcConfiguration`) and the end-to-end flows (org signup, login lookup, social-IdP, per-org SSO), see the docsite:

- `website/content/2.platform/4.organization-management.md` — flows, data model, endpoint reference
- `website/content/2.platform/5.system-security.md` — architecture, scope isolation, design decisions

---

## Notes

- **Elasticsearch index defaults.** `CrudServiceTemplate.createIndex` always creates indices with 3 shards, 2 replicas, and `fs` storage type. These values are hard-coded and not exposed as configurable properties. The mapping `dynamic` setting is always `Strict` when mappings are supplied, so unmapped fields are rejected at index time.

- **Cursor paging requires a Sort.** `CursorPageable` without a `Sort` throws `IllegalArgumentException` at query time inside `CrudServiceTemplate`. This constraint is enforced at runtime, not at construction time.

- **Application deletion guard.** `DefaultApplicationService.deleteById` queries `ProjectService.countForApplication` before delegating to Elasticsearch. If the application still has projects, the future is failed with `IllegalStateException`. This check is not atomic; a concurrent project creation between the count and the delete is theoretically possible.

- **`RawJson` is Jackson 3-only.** The `serialize` implementation in `RawJson` explicitly requires `Jackson3JsonpGenerator` and throws `UnsupportedOperationException` for any other generator. The module targets the `tools.jackson` (Jackson 3.x) artifact coordinates, not the legacy `com.fasterxml.jackson` coordinates.

- **`DefaultClusterInfoService` is optional-safe.** The `Ignite` bean is injected with `@Autowired(required = false)`. When Ignite is not present, `getClusterInfo()` returns a static `ClusterInfo` with `clusteringEnabled=false` instead of failing. Callers must check this flag before interpreting the node list.

- **Project id derivation.** When a `Project` is saved without an explicit id, `DefaultProjectService` constructs one as `<applicationId>_<slugified-name>` (all lower-cased, underscore separator). The derived id is then validated against `^[a-z][a-z0-9._-]*$`. Callers that supply their own id must satisfy this pattern themselves.

- **`@Publish` / `@Proxy` annotations are present but not active on all services.** `ApplicationService` carries `@Publish`. `ProjectService` carries both `@Publish` and `@Proxy`. `ClusterInfoService` and `LogManager` have the annotations commented out in the source. The remote-publishing behaviour is governed by kinotic-core, not by this module.

- **`syncIndex` forces a Lucene refresh.** Both `ApplicationService` and `ProjectService` expose a `syncIndex` method that calls the Elasticsearch indices refresh API, making all recent writes immediately visible to search. This is an expensive operation and is not called automatically; it is intended for test or batch-load scenarios where read-your-write guarantees are needed outside of the normal refresh interval.

- **Auto-configuration package isolation.** `KinoticDomainAutoConfiguration` lives in `org.kinotic.domain_autoconfig`, a package outside the `org.kinotic.domain` tree. This is deliberate: `KinoticDomainLibrary` uses `@ComponentScan` with no explicit base package, which defaults to scanning `org.kinotic.domain.*`. Placing the auto-config class outside that tree prevents it from being picked up by the scan and instantiated a second time.
