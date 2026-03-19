# kinotic-migration — AI Reference

> For narrative documentation see [README.md](./README.md).

## Build & Test Commands

```bash
./gradlew :kinotic-migration:build      # compile
./gradlew :kinotic-migration:test       # run unit tests
./gradlew :kinotic-migration:check      # build + test + lint
```

## Hard Rules

Directives for AI working on this module (sourced from architectural constraints in the Notes section and code):

- Never activate the full Kinotic server stack (`@EnableKinotic`) in this module; it is intentionally absent and must remain so.
- Never introduce long-running threads, REST endpoints, or message bus connections — the process must exit immediately after all migrations complete.
- Never add distributed locking or coordination mechanisms; the single-node, sequential execution model is the architectural guarantee that prevents concurrent migration application.
- Always name migration files using the `V<N>__<description>.sql` convention with a positive integer version; do not introduce files that deviate from this pattern, as the version is parsed from the filename.
- Never allow two classpath migration files to share the same version number; `MigrationExecutor` will throw `IllegalStateException` before any statements execute if duplicates are detected.
- Always use `MirationProperties` (one `g`) as the authoritative spelling for the configuration properties class — do not rename or alias it, as this is the binding used by Spring configuration.

## Package Structure

```
org.kinotic.migration
├── KinoticMigrationApplication.java   — Spring Boot entry point
├── MigrationInitializer.java          — ApplicationReadyEvent listener; drives execution and shutdown
├── SystemMigrator.java                — Loads classpath migration files and applies pending ones
└── config/
    ├── MigrationElasticsearchConfig.java  — Produces ElasticsearchAsyncClient and JsonpMapper beans
    └── MirationProperties.java            — @ConfigurationProperties bound to "kinotic-migration"
```

The SQL parsing and execution infrastructure lives entirely in `kinotic-sql`:

```
org.kinotic.sql
├── domain/          — Migration, ResourceMigration, MigrationContent, Statement subtypes
├── executor/        — MigrationExecutor, StatementExecutor implementations
├── parsers/         — MigrationParser, per-statement parsers
└── parser/          — ANTLR-generated lexer/parser for the KinoticSQL grammar
```

## Operation Flow

1. Spring Boot initializes all beans. `MirationProperties` is validated — host, port, and scheme are required.
2. `MigrationElasticsearchConfig` constructs an `ElasticsearchAsyncClient` pointing at the configured cluster, optionally adding a `Basic` `Authorization` header when username and password are present.
3. Once the context is fully ready, `MigrationInitializer` receives `ApplicationReadyEvent`.
4. `SystemMigrator.execute()` calls `MigrationExecutor.ensureMigrationIndexExists()` to create the `migration_history` index if absent.
5. All `.sql` files matching `classpath:migrations/*.sql` are resolved via `PathMatchingResourcePatternResolver`.
6. `MigrationExecutor.getLastAppliedMigrationVersion(SYSTEM_PROJECT)` is queried to find the highest already-applied version.
7. Files whose version number exceeds the last applied version are wrapped in `ResourceMigration` instances and passed to `MigrationExecutor.executeSystemMigrations()`.
8. `MigrationExecutor` sorts migrations by version, executes each statement in order, and records a `MigrationRecord` (version, project ID, timestamp, name, duration) in `migration_history` with `refresh=wait_for`.
9. On success, `MigrationInitializer` closes the `ConfigurableApplicationContext` and the JVM exits.
10. On any error, `SystemMigrator` wraps the exception in `IllegalStateException` with the message `"Failed to initialize system migrations"`, causing the application to exit non-zero.

## Public API

| Class / Interface | Package | Role |
|---|---|---|
| `KinoticMigrationApplication` | `org.kinotic.migration` | Spring Boot main class; entry point for the executable JAR |
| `MigrationInitializer` | `org.kinotic.migration` | Wires `ApplicationReadyEvent` to migration execution and process shutdown |
| `SystemMigrator` | `org.kinotic.migration` | Classpath migration loader; compares file versions against applied history |
| `MirationProperties` | `org.kinotic.migration.config` | Typed configuration holder bound to the `kinotic-migration` prefix |
| `MigrationElasticsearchConfig` | `org.kinotic.migration.config` | Spring `@Configuration` that registers the Elasticsearch client beans |
| `Migration` _(kinotic-sql)_ | `org.kinotic.sql.domain` | Interface for a versioned migration unit |
| `MigrationExecutor` _(kinotic-sql)_ | `org.kinotic.sql.executor` | Applies migrations against Elasticsearch and tracks history |
| `MigrationParser` _(kinotic-sql)_ | `org.kinotic.sql.parsers` | Parses `.sql` files into `MigrationContent` via the KinoticSQL ANTLR grammar |

## Module Dependencies

| Module | Reason |
|---|---|
| `kinotic-sql` | Provides the KinoticSQL grammar, `MigrationParser`, `MigrationExecutor`, `Migration`/`ResourceMigration`/`MigrationContent` domain types, and all `StatementExecutor` implementations that translate SQL-dialect statements into Elasticsearch API calls |
| `co.elastic.clients:elasticsearch-java` | Elasticsearch Java API client (`ElasticsearchAsyncClient`) used by `MigrationExecutor` and `MigrationElasticsearchConfig` |
| `co.elastic.clients:elasticsearch-rest5-client` | Low-level HTTP transport (`Rest5Client`, `Rest5ClientTransport`) underlying the high-level client |
| `spring-boot-starter` | Core Spring Boot infrastructure (context, events, auto-configuration) |
| `spring-boot-starter-jackson` | Jackson `JsonMapper` bean wired into the `Jackson3JsonpMapper` JSONP mapper for the Elasticsearch transport |
| `spring-boot-starter-validation` | Bean validation used to enforce required fields on `MirationProperties` at startup |
