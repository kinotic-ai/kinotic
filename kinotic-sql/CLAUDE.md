# kinotic-sql — AI Reference

> For narrative documentation see [README.md](./README.md).

## Build & Test Commands

```bash
./gradlew :kinotic-sql:build      # compile
./gradlew :kinotic-sql:test       # run unit tests
./gradlew :kinotic-sql:check      # build + test + lint
```

## Hard Rules

Directives for AI working on this module (sourced from architectural constraints in the Notes section and code):

- Never edit files in `org.kinotic.sql.parser` directly. All changes to the ANTLR4-generated lexer, parser, listener, and visitor files must be made in `src/main/antlr/KinoticSQL.g4` and regenerated via `./gradlew :kinotic-sql:generateGrammarSource`.
- Never place `KinoticSqlAutoConfiguration` under a package that would be picked up by Spring component scanning. It must live in `org.kinotic.sql_autoconfig` and be declared only in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Always add a new `@Component` implementing `StatementParser` and a matching `@Component` implementing `StatementExecutor` when introducing a new statement type — the `MigrationParser` and `MigrationExecutor` discover them via component scanning and will not handle the new type otherwise.
- Never mutate migration version numbers after they have been applied. Version uniqueness is enforced at runtime; duplicate or non-positive versions throw immediately before any migration is applied.
- Do not configure shard/replica counts inside `CREATE TABLE` statements. Default values (3 primary shards, 2 replicas) are hard-coded in `CreateTableStatementExecutor`. Use `CREATE COMPONENT TEMPLATE` and `CREATE INDEX TEMPLATE` when per-index shard/replica configuration is required.
- Never set `dynamic: true` or `dynamic: false` on indices managed by this module. `CreateTableStatementExecutor` enforces `dynamic: strict` on all indices, so schema changes must go through `ALTER TABLE` migrations rather than ad-hoc field additions.

## Package Structure

| Package | Responsibility |
|---|---|
| `org.kinotic.sql` | Spring library configuration entry point (`KinoticSqlLibrary`) |
| `org.kinotic.sql.domain` | Immutable domain records and value types: `Migration`, `MigrationContent`, `Statement`, `Column`, `ColumnType`, `Expression`, `WhereClause` |
| `org.kinotic.sql.domain.statements` | Concrete `Statement` implementations, one per SQL statement type, plus `TemplatePart`, `ColumnTemplatePart`, `SettingTemplatePart` |
| `org.kinotic.sql.parser` | **ANTLR4-generated files only** — `KinoticSQLLexer`, `KinoticSQLParser`, `KinoticSQLVisitor`, `KinoticSQLListener`, and their base implementations |
| `org.kinotic.sql.parsers` | Hand-written ANTLR visitor glue: `MigrationParser`, one `StatementParser` per statement type, `TypeParser`, `ExpressionVisitor`, `WhereClauseVisitor`, `TemplatePartParser` |
| `org.kinotic.sql.executor` | `MigrationExecutor`, `StatementExecutor` interface, `QueryBuilder`, `TypeMapper` |
| `org.kinotic.sql.executor.executors` | One `StatementExecutor` implementation per statement type |
| `org.kinotic.sql_autoconfig` | `KinoticSqlAutoConfiguration` — must not be component-scanned; registered via Spring auto-configuration metadata |

## Operation Flow

1. A caller provides a list of `Migration` objects (e.g., built from classpath resources as `ResourceMigration` instances) and a `projectId` to `MigrationExecutor`.
2. `MigrationExecutor` ensures the `migration_history` index exists, sorts migrations by version, and iterates them in order.
3. For each migration, it queries `migration_history` to check whether that version has already been applied for the project.
4. For unapplied migrations, `getContent()` is called on the `Migration`, triggering lazy parsing: `MigrationParser` feeds the SQL through the ANTLR4 lexer and parser, then the `MigrationVisitor` dispatches each statement context to the appropriate `StatementParser`, producing a `MigrationContent` with a list of `Statement` objects.
5. Each `Statement` is passed to `MigrationExecutor.executeStatement`, which finds the matching `StatementExecutor` and calls `executeMigration`, returning a `CompletableFuture`.
6. Statements within a migration are chained sequentially. Upon completion, the migration version is recorded in `migration_history` with its `projectId`, name, and duration.

## Public API

| Class / Interface | Package | Role |
|---|---|---|
| `MigrationExecutor` | `org.kinotic.sql.executor` | Primary orchestration bean; call `ensureMigrationIndexExists()` then `executeSystemMigrations` or `executeProjectMigrations` |
| `Migration` | `org.kinotic.sql.domain` | Interface to implement (or use `ResourceMigration`) when supplying migrations |
| `ResourceMigration` | `org.kinotic.sql.domain` | Concrete `Migration` backed by a Spring `Resource`; extracts version from filename automatically |
| `MigrationParser` | `org.kinotic.sql.parsers` | Spring bean; parses a `Resource`, `String`, or `byte[]` of KinoticSQL into `MigrationContent` |
| `MigrationContent` | `org.kinotic.sql.domain` | Record holding the ordered `List<Statement>` for one migration file |
| `StatementExecutor` | `org.kinotic.sql.executor` | Interface to implement when adding support for a new statement type |
| `StatementParser` | `org.kinotic.sql.parsers` | Interface to implement when adding support for a new statement type in the parser |
| `QueryBuilder` | `org.kinotic.sql.executor` | Static utility; converts a `WhereClause` and parameter map to an Elasticsearch `Query` |
| `TypeMapper` | `org.kinotic.sql.executor` | Static utility; maps a `Column` (name + `ColumnType` + indexed flag) to an Elasticsearch `Property` |
| `KinoticSqlLibrary` | `org.kinotic.sql` | `@Configuration` class; import directly in tests or custom configurations that need the library without relying on auto-configuration |

## Module Dependencies

| Dependency | Reason |
|---|---|
| `spring-boot-starter` | Application context, component scanning, configuration-properties binding, `Resource` abstraction used by `ResourceMigration` |
| `co.elastic.clients:elasticsearch-java` | Elasticsearch Java client used by all `StatementExecutor` implementations and `MigrationExecutor` |
| `co.elastic.clients:elasticsearch-rest5-client` | Low-level REST transport for the Elasticsearch Java client |
| `org.antlr:antlr4-runtime` | Runtime support for the ANTLR4-generated `KinoticSQLLexer` and `KinoticSQLParser` |
| `com.fasterxml.jackson.core:jackson-annotations`, `tools.jackson.core:jackson-core`, `tools.jackson.core:jackson-databind` | JSON serialization required by the Elasticsearch Java client |
| `io.freefair.lombok` (build-time) | Reduces boilerplate in domain classes and executor implementations |
| `org.antlr:antlr4` (build-time, `antlr` configuration) | ANTLR4 tool used by the Gradle `generateGrammarSource` task to regenerate the parser from `KinoticSQL.g4` |
