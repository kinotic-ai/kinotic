# kinotic-sql

A SQL-like DSL and migration engine for managing Elasticsearch index schemas and data through versioned migration scripts.

## Overview

Relational-style schema management tooling does not naturally translate to Elasticsearch, where index mappings, component templates, and index templates must be created and evolved through the Elasticsearch API. kinotic-sql bridges this gap by providing a concise, SQL-inspired grammar that lets developers express Elasticsearch schema changes and data operations in plain text files rather than hand-written API calls.

Each migration is a versioned `.sql` file containing one or more statements written in the KinoticSQL dialect. The migration engine parses these files using an ANTLR4-generated lexer/parser, converts each statement into a typed Java domain object, and executes it against Elasticsearch asynchronously using the official Elasticsearch Java client. Applied migrations are recorded in a dedicated `migration_history` index, making the system idempotent: a migration that has already been applied for a given project will not be executed again.

The module integrates with Spring Boot through a standard `@AutoConfiguration` class. The entire library activates only when the `kinotic.disablePersistence` property is absent or set to `false`, making it straightforward to disable all persistence-related wiring in test or edge-case environments without removing the dependency.

Migration scope is tracked per project. System-level migrations (applied at application startup) and project-specific migrations (applied on demand) are stored in the same history index but are isolated by a `projectId` field, allowing independently versioned migration sets to coexist in a single Elasticsearch cluster.

## Key Concepts

- **Migration** — An interface representing a single versioned migration script. Implementations expose a numeric `version` (parsed from the filename prefix `V<number>__`), a `name`, and lazily loaded `MigrationContent`.
- **MigrationContent** — A record holding the ordered list of `Statement` objects parsed from one migration file.
- **Statement** — A marker interface implemented by all domain objects that represent a single parsed SQL statement (`CreateTableStatement`, `AlterTableStatement`, `InsertStatement`, `UpdateStatement`, `DeleteStatement`, `ReindexStatement`, `CreateComponentTemplateStatement`, `CreateIndexTemplateStatement`).
- **MigrationParser** — The entry point for parsing. Feeds raw SQL text or a Spring `Resource` through the ANTLR4 lexer/parser, then delegates each statement parse tree node to a matching `StatementParser` implementation.
- **StatementParser** — An interface with `supports(StatementContext)` and `parse(StatementContext)` methods. One implementation exists per statement type and is discovered by Spring component scanning.
- **StatementExecutor** — A generic interface parameterised on statement type and return type. Provides `executeMigration` for migration-time execution and `executeQuery` for parameterised runtime execution against Elasticsearch. One implementation exists per statement type.
- **MigrationExecutor** — The orchestration component that sorts migrations by version, checks which have already been applied using the `migration_history` index, executes unapplied migrations as a sequential `CompletableFuture` chain, and records the result.
- **QueryBuilder** — A utility that translates a `WhereClause` domain object (produced by the parser) into an Elasticsearch `Query` object, supporting `AND`/`OR` composition and all six comparison operators.

## Configuration

### Spring Auto-configuration

`KinoticSqlAutoConfiguration` (in `org.kinotic.sql_autoconfig`) is the registered auto-configuration class. It imports `KinoticSqlLibrary`, which triggers a `@ComponentScan` of the `org.kinotic.sql` package tree and enables configuration properties.

### Property Reference

| Property | Type | Default | Description |
|---|---|---|---|
| `kinotic.disablePersistence` | `boolean` | `false` | Set to `true` to disable all kinotic-sql beans. When `false` (or absent), the library is fully active. |

The auto-configuration class is intentionally placed in a separate package (`org.kinotic.sql_autoconfig`) to prevent it from being picked up by Spring's component scan. It must be declared in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` to take effect.

## Usage Example

The following example shows how to load migration files from the classpath, build `Migration` objects, and execute them for a specific project.

```java
@Service
@RequiredArgsConstructor
public class MyProjectMigrationRunner {

    private final MigrationExecutor migrationExecutor;
    private final MigrationParser migrationParser;

    @Value("classpath:migrations/V1__create_orders.sql")
    private Resource v1;

    @Value("classpath:migrations/V2__add_status_column.sql")
    private Resource v2;

    public CompletableFuture<Void> runMigrations(String projectId) {
        List<Migration> migrations = List.of(
            new ResourceMigration(v1, migrationParser),
            new ResourceMigration(v2, migrationParser)
        );
        return migrationExecutor.ensureMigrationIndexExists()
            .thenCompose(created -> migrationExecutor.executeProjectMigrations(migrations, projectId));
    }
}
```

A matching migration file `V1__create_orders.sql` might contain:

```sql
-- Create the orders index
CREATE TABLE IF NOT EXISTS orders (
    id       KEYWORD,
    customer KEYWORD,
    total    DECIMAL NOT INDEXED,
    status   KEYWORD,
    placed   DATE
);

INSERT INTO orders (id, customer, total, status, placed)
    VALUES ('ord-001', 'acme', 199.99, 'pending', '2024-01-15') WITH REFRESH;
```

And `V2__add_status_column.sql`:

```sql
ALTER TABLE orders ADD COLUMN shipped BOOLEAN NOT INDEXED;
```

## Notes

- **ANTLR4-generated files must not be edited manually.** The six files in `org.kinotic.sql.parser` (`KinoticSQLLexer`, `KinoticSQLParser`, `KinoticSQLListener`, `KinoticSQLBaseListener`, `KinoticSQLVisitor`, `KinoticSQLBaseVisitor`) are produced by the Gradle `generateGrammarSource` task from `src/main/antlr/KinoticSQL.g4`. Any changes to the grammar must be made in the `.g4` file and the task re-run. The task is gated behind an explicit invocation (`project.gradle.startParameter.taskNames.contains(':kinotic-sql:generateGrammarSource')`) to avoid regenerating them on every build.

- **Indices created by `CREATE TABLE` use strict dynamic mapping.** `CreateTableStatementExecutor` sets `dynamic: strict` on every index, meaning Elasticsearch will reject documents with fields not declared in the mapping. Schema evolution must go through `ALTER TABLE` migrations.

- **Default shard/replica settings for `CREATE TABLE`.** When executing a `CREATE TABLE` statement without a component template, the executor applies fixed defaults: 3 primary shards and 2 replicas. These values are not configurable via the DSL at the `CREATE TABLE` level; use `CREATE COMPONENT TEMPLATE` and `CREATE INDEX TEMPLATE` when per-index shard/replica configuration is required.

- **`REINDEX` always submits as non-blocking.** The executor submits all reindex requests with `wait_for_completion=false`. If `WAIT = TRUE` is specified in the DSL, the executor polls the Elasticsearch tasks API every 2 seconds until the task completes or a 1-hour timeout is reached.

- **`REINDEX` supports `SKIP_IF_NO_SOURCE`.** When `SKIP_IF_NO_SOURCE = TRUE` is set, the executor checks whether the source index exists before issuing the reindex call. If the source is absent, the operation is silently skipped, which is useful for idempotent migrations that may run against environments where the source index was never created.

- **Column indexing control.** Appending `NOT INDEXED` to a column type in any DDL statement sets both `index: false` and `doc_values: false` on the resulting Elasticsearch field mapping (except `TEXT`, `BINARY`, `GEO_POINT`, and `GEO_SHAPE`, which do not support this modifier).

- **Version uniqueness is enforced at runtime.** `MigrationExecutor` validates that no two migrations in a batch share the same version number and that all versions are positive integers. Violations throw immediately before any migration is applied.

- **Migration history is scoped by `projectId`.** The constant `MigrationExecutor.SYSTEM_PROJECT` (`"structures_system"`) identifies the project used for system-level migrations. Each project maintains its own independent migration history within the shared `migration_history` index.

- **`StatementParser` and `StatementExecutor` are extensible via Spring.** Both interfaces are component-scanned. Adding a new statement type requires: a new grammar rule in `KinoticSQL.g4`, a domain record implementing `Statement`, a `@Component` implementing `StatementParser`, and a `@Component` implementing `StatementExecutor`. The `MigrationParser` and `MigrationExecutor` will pick them up automatically.
