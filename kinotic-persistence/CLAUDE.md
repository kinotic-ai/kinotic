# kinotic-persistence — AI Reference

> For narrative documentation see [README.md](./README.md).

## Build & Test Commands

```bash
./gradlew :kinotic-persistence:build      # compile
./gradlew :kinotic-persistence:test       # run unit tests
./gradlew :kinotic-persistence:check      # build + test + lint
```

## Hard Rules

Directives for AI working on this module (sourced from architectural constraints in the Notes section and code):

- Always prefer `EntitiesService` for in-process (same-JVM) callers — it is the native, type-safe API and the preferred access path. Only use `JsonEntitiesService` / `AdminJsonEntitiesService` or the HTTP endpoints (OpenAPI / GraphQL) when the caller runs outside the JVM.
- Never place `PolicyDecorator` or `RoleDecorator` directly on a field and expect authorization enforcement; they are only meaningful when placed inside an `EntityServiceDecoratorsConfig` and attached to a type via `EntityServiceDecoratorsDecorator`.
- Never use `MultiTenancyType.ISOLATED` or `HYBRID` in entity schemas — only `NONE` and `SHARED` are active serialized values; the others are present as enum comments only.
- Always compose `EntityDefinition` IDs as `applicationId + "." + name` (the `PersistenceUtil.entityDefinitionNameToId` pattern); never pass bare entity names as entity definition IDs.
- Never call `syncIndex` in high-throughput paths — it bypasses the normal Elasticsearch refresh interval and should be used with care only when immediately-visible writes are required.
- Always keep service implementations and all non-API types in `internal` packages; only types under `org.kinotic.persistence.api` are part of the public surface.
- Never assume an `EntityDefinition` is available to HTTP endpoints or data operations until `EntityDefinitionService.publish()` has been called; creation alone does not expose the definition.
- Always provide a custom `AuthorizationServiceFactory` bean when policy or role enforcement is required; the default is a no-op (`NoopAuthorizationServiceFactory`) that silently skips all authorization checks.

## Package Structure

| Package | Responsibility |
|---|---|
| `org.kinotic.persistence` | Library entry point (`KinoticPersistenceLibrary`) |
| `org.kinotic.persistence.api.config` | Public configuration types (`PersistenceProperties`, `ElasticConnectionInfo`, `KinoticPersistenceProperties`) |
| `org.kinotic.persistence.api.model` | Public model types (`EntityDefinition`, `EntityContext`, `EntityOperation`, `DecoratedProperty`, `QueryOptions`, migration records) |
| `org.kinotic.persistence.api.model.idl.decorators` | All `C3Decorator` subclasses that consumers attach to entity schemas |
| `org.kinotic.persistence.api.services` | Public service interfaces (`EntitiesService`, `EntityDefinitionService`, `JsonEntitiesService`, `AdminJsonEntitiesService`, `NamedQueriesService`, `MigrationService`) |
| `org.kinotic.persistence.api.services.security` | Authorization extension points (`AuthorizationService`, `AuthorizationServiceFactory`, policy evaluators) |
| `org.kinotic.persistence.internal` | Bootstrap (`PersistenceInitializer`) |
| `org.kinotic.persistence.internal.api.hooks` | Upsert pre-processing pipeline (`UpsertPreProcessor`, `UpsertFieldPreProcessor`, `DecoratorLogic`, per-decorator implementations) |
| `org.kinotic.persistence.internal.api.services` | Default service implementations, Elasticsearch DAO, entity definition conversion |
| `org.kinotic.persistence.internal.api.services.json` | Streaming JSON processing for bulk operations |
| `org.kinotic.persistence.internal.api.services.sql` | SQL query parsing, parameter binding, and Elasticsearch SQL execution |
| `org.kinotic.persistence.internal.api.services.security.graphos` | Policy expression evaluation for GraphQL authorization |
| `org.kinotic.persistence.internal.cache` | Caffeine-backed caches for entity definition schemas, GraphQL schemas, and OpenAPI specs; cluster-wide cache eviction via Apache Ignite |
| `org.kinotic.persistence.internal.config` | Internal Spring configuration (`PersistenceConfiguration`, `CacheEvictionConfiguration`, `DataInsightsConfiguration`) |
| `org.kinotic.persistence.internal.converters` | C3 type → Elasticsearch mapping / GraphQL SDL / OpenAPI schema converters |
| `org.kinotic.persistence.internal.endpoints.graphql` | `GqlVerticle`, `DelegatingGqlHandler`, all GraphQL data fetchers |
| `org.kinotic.persistence.internal.endpoints.openapi` | `OpenApiVerticle`, per-operation route handlers |
| `org.kinotic.persistence.internal.serializer` | Custom Jackson serializers and deserializers |
| `org.kinotic.persistence_autoconfig` | `KinoticPersistenceAutoConfiguration` (isolated from component scan) |

## Operation Flow

1. **Definition registration** — A caller invokes `EntityDefinitionService.create()` with an `EntityDefinition` carrying an `ObjectC3Type` schema decorated with IDL decorators. The service persists the definition to an Elasticsearch index (prefix `kinotic_`).
2. **Publication** — `EntityDefinitionService.publish()` processes the decorators, derives the Elasticsearch index mapping, computes `decoratedProperties`, and marks the definition as published. Cache entries for the affected application are invalidated across the cluster via Ignite.
3. **Schema generation** — On first request after publication, converter chains translate the `ObjectC3Type` to a GraphQL SDL type and an OpenAPI schema component. Both representations are cached with Caffeine.
4. **Write** — A client POSTs to the OpenAPI endpoint or sends a GraphQL mutation. The handler constructs a `DefaultEntityContext` from the authenticated participant and routes to `JsonEntitiesService`. The upsert pre-processor pipeline runs (ID generation, tenant injection, version increment, time reference stamping), and the document is indexed to Elasticsearch.
5. **Read** — Queries, pagination, full-text search, and named SQL queries are dispatched to the corresponding data fetcher or route handler, translated to Elasticsearch DSL or SQL, and results are projected back through the configured field inclusion filter.

## Public API

| Type | Kind | Purpose |
|---|---|---|
| `EntityDefinitionService` | Interface | CRUD for `EntityDefinition` records; publish/unpublish lifecycle |
| `EntitiesService` | Interface | Type-safe entity CRUD (save, update, delete, findById, findAll, search, namedQuery) |
| `JsonEntitiesService` | Interface (`@Publish`) | JSON-level entity CRUD for remote callers within the caller's tenant |
| `AdminJsonEntitiesService` | Interface (`@Publish`) | JSON-level entity CRUD with explicit tenant selection |
| `NamedQueriesService` | Interface (`@Publish`) | CRUD for `NamedQueriesDefinition`; named query execution |
| `MigrationService` | Interface (`@Publish`) | Project migration execution and version tracking |
| `EntityDefinition` | Model | Schema record: name, applicationId, projectId, `ObjectC3Type` schema, decorator metadata |
| `EntityContext` | Interface | Per-request context: participant, field inclusion filter, tenant selection |
| `EntityOperation` | Enum | Canonical set of operations (SAVE, UPDATE, DELETE_BY_ID, FIND_ALL, etc.) used for decorator dispatch |
| `DecoratedProperty` | Model | JSON path + list of `C3Decorator` instances, derived from the schema on publication |
| `QueryOptions` | Model | Optional per-query settings: timeZone, requestTimeout, pageTimeout |
| `KinoticPersistenceProperties` | Spring `@Component` | Root properties bean; injected at `kinotic.*` prefix |
| `PersistenceProperties` | Config | All persistence settings; accessed via `kinotic.persistence.*` |
| `AuthorizationServiceFactory` | SPI | Implement and expose as a Spring bean to supply a custom `AuthorizationService` |

## Module Dependencies

| Module | Reason |
|---|---|
| `kinotic-core` | `KinoticProperties`, `SecurityService`, `AuthenticationHandler`, `Participant`, `@Publish`, and core Vert.x lifecycle utilities |
| `kinotic-domain` | `IdentifiableCrudService`, `Page`, `Pageable`, `ApplicationService`, and shared domain primitives (`RawJson`) |
| `kinotic-idl` | `ObjectC3Type`, `C3Decorator`, `DecoratorTarget`, `FunctionDefinition`, and the full IDL type hierarchy used to define and convert entity schemas |
| `kinotic-rpc-gateway` | Remote service publication; services annotated with `@Publish` are registered with the RPC gateway for external callers |
| `kinotic-sql` | SQL grammar parsing and parameter holder types used by named-query execution against the Elasticsearch SQL endpoint |
