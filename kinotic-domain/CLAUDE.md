# kinotic-domain — AI Reference

> For narrative documentation see [README.md](./README.md).

## Build & Test Commands

```bash
./gradlew :kinotic-domain:build      # compile
./gradlew :kinotic-domain:test       # run unit tests
./gradlew :kinotic-domain:check      # build + test + lint
```

## Hard Rules

Directives for AI working on this module (sourced from architectural constraints in the Notes section and code):

- Never add configurable properties for Elasticsearch index settings (shards, replicas, storage type); these are intentionally hard-coded in `CrudServiceTemplate.createIndex` at 3 shards, 2 replicas, and `fs` storage type.
- Always use `Strict` dynamic mapping when supplying Elasticsearch mappings; do not set `dynamic` to anything other than `Strict`, as unmapped fields must be rejected at index time.
- Never allow `CursorPageable` to be constructed or used without a non-null `Sort`; cursor paging without a sort is illegal and will throw `IllegalArgumentException` at query time.
- Never delete an `Application` without first verifying that no `Project` entities remain; `DefaultApplicationService.deleteById` must always query `ProjectService.countForApplication` and fail with `IllegalStateException` if the count is non-zero.
- Always use Jackson 3.x (`tools.jackson`) artifact coordinates in this module; never introduce or fall back to the legacy `com.fasterxml.jackson` coordinates. `RawJson` explicitly requires `Jackson3JsonpGenerator` and throws `UnsupportedOperationException` for any other generator.
- Never call `syncIndex` in production code paths or automatic workflows; it forces a full Lucene refresh and is reserved exclusively for test or batch-load scenarios where read-your-write guarantees are needed.

## Package Structure

| Package | Contents |
|---|---|
| `org.kinotic.domain.api.model` | Entity types: `Application`, `Project`, `ProjectType`, `RawJson` |
| `org.kinotic.domain.api.model.cluster` | `ClusterInfo`, `NodeInfo` |
| `org.kinotic.domain.api.model.log` | `LogLevel`, `LoggersDescriptor`, `LoggerLevelsDescriptor`, `SingleLoggerLevelsDescriptor`, `GroupLoggerLevelsDescriptor` |
| `org.kinotic.domain.api.services` | Public service interfaces: `ApplicationService`, `ProjectService`, `ClusterInfoService`, `LogManager` |
| `org.kinotic.domain.api.services.crud` | Persistence contract (`CrudService`, `IdentifiableCrudService`, `Identifiable`), paging types, `Sort`, `Order`, `SearchCriteria`, `SearchComparator` |
| `org.kinotic.domain.api.utils` | `CoreUtil` — static validation helpers for application and project ids |
| `org.kinotic.domain.internal.api.services` | Concrete implementations: `AbstractCrudService`, `CrudServiceTemplate`, `DefaultApplicationService`, `DefaultProjectService`, `DefaultClusterInfoService`, `DefaultLogManager` |
| `org.kinotic.domain.internal.config` | `KinoticDomainJacksonConfig` — registers the Jackson module |
| `org.kinotic.domain.internal.serializer` | Custom Jackson serializers and deserializers for `Pageable`, `Page`, `SearchComparator`, and `RawJson` |
| `org.kinotic.domain_autoconfig` | `KinoticDomainAutoConfiguration` — Spring Boot auto-configuration entry point |

## Operation Flow

1. Spring Boot picks up `KinoticDomainAutoConfiguration` from the `META-INF/spring/…` auto-configuration list and imports `KinoticDomainLibrary`.
2. `KinoticDomainLibrary` fires only when `kinotic.disablePersistence` is `false` (the default). It triggers a component scan of the `org.kinotic.domain` base package.
3. `KinoticDomainJacksonConfig` creates and registers the `KinoticDomainModule` Jackson `SimpleModule`.
4. `CrudServiceTemplate` is instantiated as a Spring `@Component`, wrapping `ElasticsearchAsyncClient`.
5. `DefaultApplicationService` and `DefaultProjectService` extend `AbstractCrudService`, each declaring a fixed Elasticsearch index name (`kinotic_application` and `kinotic_project` respectively).
6. On a `findAll` or `search` call, `CrudServiceTemplate.search()` inspects the `Pageable` type: for `OffsetPageable` it uses `from` + `size` + `track_total_hits`; for `CursorPageable` it deserializes the cursor JSON into a list of `FieldValue` objects and passes them as `search_after`.
7. Results are mapped back through the type-specific `JsonpDeserializer` chain. If the document type is `RawJson`, the dedicated `RawJsonJsonpDeserializer` is used to avoid a full parse cycle.

## Public API

| Type | Kind | Description |
|---|---|---|
| `ApplicationService` | Interface | CRUD for `Application` entities, with `createApplicationIfNotExist` and `syncIndex` |
| `ProjectService` | Interface | CRUD for `Project` entities, with application-scoped queries and `createProjectIfNotExist` |
| `ClusterInfoService` | Interface | Returns an Ignite `ClusterInfo` snapshot asynchronously |
| `LogManager` | Interface | Per-node runtime log level inspection and configuration |
| `CrudService<T, ID>` | Interface | Generic async persistence contract |
| `IdentifiableCrudService<T, ID>` | Interface | Extends `CrudService` with uniqueness-enforcing `create` |
| `Identifiable<T>` | Interface | Marker for types that carry a typed id |
| `Pageable` | Interface | Paging request contract; factory methods for `OffsetPageable` and `CursorPageable` |
| `OffsetPageable` | Class | Offset/page-number pagination |
| `CursorPageable` | Class | Cursor (search_after) pagination; requires a non-null `Sort` |
| `Page<T>` | Class | Paged result with content list and total element count |
| `CursorPage<T>` | Class | Paged result with opaque cursor for the next page |
| `Sort` / `Order` / `Direction` | Classes / Enum | Multi-field sort specification |
| `SearchCriteria<T>` | Class | Structured field/value/comparator triple for filtered queries |
| `SearchComparator` | Enum | Comparison operators: `EQUALS`, `NOT`, `GREATER_THAN`, `GREATER_THAN_OR_EQUALS`, `LESS_THAN`, `LESS_THAN_OR_EQUALS`, `LIKE` |
| `RawJson` | Class | Opaque JSON payload stored as a byte array; `JsonpSerializable` |
| `Application` | Class | Top-level entity; must satisfy `^[A-Za-z][A-Za-z0-9._-]*$` |
| `Project` | Class | Entity scoped to an `Application`; id auto-derived via slug if not provided |
| `ProjectType` | Enum | `TYPESCRIPT`, `GRAPHQL`, `GRAPHICAL`, `ELASTICSEARCH`, `DATA_INSIGHTS` |
| `ClusterInfo` / `NodeInfo` | Classes | Immutable cluster and per-node snapshot value objects |

## Module Dependencies

| Dependency | Reason |
|---|---|
| `kinotic-core` | Provides `@Publish`, `@Proxy`, `@Scope`, and `Kinotic` (used by `LogManager` to obtain the local node id) |
| `co.elastic.clients:elasticsearch-java` + `elasticsearch-rest5-client` | `CrudServiceTemplate` and all `AbstractCrudService` subclasses use `ElasticsearchAsyncClient` for all persistence operations |
| `org.apache.ignite:ignite-core` | `DefaultClusterInfoService` uses the `Ignite` API to inspect the cluster topology; the dependency is `@Autowired(required = false)` so the service degrades gracefully when Ignite is absent |
| `com.github.slugify:slugify` | `DefaultProjectService` auto-generates project ids by slugifying the project name |
| `tools.jackson.core:jackson-core` + `jackson-databind` | Custom serializers/deserializers for `Pageable`, `Page`, `SearchComparator`, and `RawJson` |
