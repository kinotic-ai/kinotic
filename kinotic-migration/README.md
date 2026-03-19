# kinotic-migration

A standalone Spring Boot application that applies SQL-dialect migrations against an Elasticsearch cluster before the main server starts.

## Overview

Elasticsearch does not enforce a schema on write, but production systems still require index mappings, templates, and seed data to exist before application code runs. `kinotic-migration` fills that gap by providing a controlled, versioned migration process that runs to completion against a target cluster before any other service is brought online.

The application is intentionally minimal. It starts up, connects directly to Elasticsearch using coordinates supplied through Spring configuration properties, applies every pending migration in version order, records each applied migration in a dedicated tracking index, then shuts itself down. There are no REST endpoints, no message bus connections, and no long-running threads — the process exits once the last migration completes.

Within the broader Kinotic architecture, `kinotic-migration` is the pre-flight step for the main server. It depends only on `kinotic-sql` for SQL parsing and execution, and on the Elasticsearch Java client for cluster connectivity. It deliberately omits the full Kinotic stack so that it can run as a lightweight, single-purpose tool.

Because the migration runner always executes on a single node and exits before any other component starts, migrations are never applied concurrently. This sequencing guarantee removes the need for distributed locking during the migration phase.

## Key Concepts

- **Migration file** — A `.sql` file placed in `src/main/resources/migrations/` following the naming convention `V<N>__<description>.sql`, where `N` is a positive integer version number. Each file may contain one or more SQL-dialect statements.
- **SQL dialect** — A Kinotic-specific SQL-like DSL (parsed by `kinotic-sql`) that maps SQL constructs such as `CREATE TABLE`, `ALTER TABLE`, `INSERT`, `UPDATE`, `DELETE`, `CREATE INDEX TEMPLATE`, `CREATE COMPONENT TEMPLATE`, and `REINDEX` to equivalent Elasticsearch API operations.
- **Migration** — The `org.kinotic.sql.domain.Migration` interface from `kinotic-sql`. Exposes the numeric version, the filename/logical name, and the lazily-parsed `MigrationContent`.
- **ResourceMigration** — The `org.kinotic.sql.domain.ResourceMigration` implementation used at runtime. It wraps a Spring `Resource`, extracts the version from the filename, and parses the SQL content on first access.
- **MigrationContent** — A record holding the ordered list of `Statement` objects produced by `MigrationParser` for a single migration file.
- **MigrationExecutor** — The `org.kinotic.sql.executor.MigrationExecutor` Spring component from `kinotic-sql`. Manages the `migration_history` Elasticsearch index, checks which versions have already been applied, executes pending statements in order, and records each applied migration.
- **SystemMigrator** — The class in this module (`org.kinotic.migration.SystemMigrator`) that resolves all `.sql` files from `classpath:migrations/*.sql`, compares them against the last applied version for the `structures_system` project, and delegates the pending subset to `MigrationExecutor`.
- **MigrationInitializer** — The Spring `@Component` (`org.kinotic.migration.MigrationInitializer`) that listens for `ApplicationReadyEvent`, triggers `SystemMigrator.execute()`, and then closes the application context to terminate the process.

## Configuration

All properties are bound under the `kinotic-migration` prefix and are declared in `MirationProperties`.

| Property | Type | Required | Default (development) | Description |
|---|---|---|---|---|
| `kinotic-migration.elasticScheme` | `String` | Yes | `http` | Transport scheme for the Elasticsearch connection (`http` or `https`) |
| `kinotic-migration.elasticHost` | `String` | Yes | `localhost` | Hostname or IP of the Elasticsearch node |
| `kinotic-migration.elasticPort` | `Integer` | Yes | `9200` | Port of the Elasticsearch node |
| `kinotic-migration.elasticUsername` | `String` | No | _(none)_ | Username for HTTP Basic authentication; omit to skip authentication |
| `kinotic-migration.elasticPassword` | `String` | No | _(none)_ | Password for HTTP Basic authentication; required when `elasticUsername` is set |

Auto-configuration class: `org.kinotic.sql_autoconfig.KinoticSqlAutoConfiguration` (provided by `kinotic-sql`) registers the `MigrationParser`, all `StatementParser` beans, and all `StatementExecutor` beans automatically.

The application runs with `spring.main.web-application-type=none`, so no HTTP server is started.

Logging level for `org.kinotic` defaults to `INFO` in production and `TRACE` in the `development` profile.

## Usage Example

### Running the migration

Build an executable JAR and run it against a target cluster:

```bash
./gradlew :kinotic-migration:bootJar

java -jar kinotic-migration/build/libs/kinotic-migration.jar \
  --kinotic-migration.elasticScheme=https \
  --kinotic-migration.elasticHost=my-es-host.example.com \
  --kinotic-migration.elasticPort=9200 \
  --kinotic-migration.elasticUsername=admin \
  --kinotic-migration.elasticPassword=secret
```

Properties may also be supplied via `application.yml`:

```yaml
kinotic-migration:
  elasticScheme: "https"
  elasticHost: "my-es-host.example.com"
  elasticPort: 9200
  elasticUsername: "admin"
  elasticPassword: "secret"
```

### Writing a migration file

Place `.sql` files in `src/main/resources/migrations/` following the `V<N>__<description>.sql` naming convention. The version `N` must be a positive integer. Files are applied in ascending version order; each file is applied exactly once per project.

```sql
-- src/main/resources/migrations/V2__add_audit_fields.sql

-- Add a column to an existing index
ALTER TABLE kinotic_project
    ADD COLUMN createdBy KEYWORD;

-- Create a new index if it does not already exist
CREATE TABLE IF NOT EXISTS kinotic_audit_event (
    id        KEYWORD,
    projectId KEYWORD,
    actor     KEYWORD,
    action    TEXT,
    timestamp DATE
);
```

The migration history is stored in the `migration_history` Elasticsearch index. Each applied migration is recorded with its version, project ID (`structures_system` for system migrations), filename, applied timestamp, and execution duration in milliseconds.

## Notes

- **Single-node execution model.** The application is designed to run once on a single node before any other service starts. Because it exits immediately after applying all pending migrations, migrations are never applied concurrently, eliminating the need for distributed coordination during this phase.
- **Idempotent by design.** `MigrationExecutor` checks the `migration_history` index before executing each migration. A migration that has already been recorded for the target project is skipped, so re-running the tool against a cluster that is already up to date is safe.
- **Version gaps are not an error.** Only migrations with a version strictly greater than the last recorded version are applied. Missing version numbers in the sequence are silently skipped; there is no gap-detection enforcement at the file-loading stage.
- **Duplicate version detection.** If two files on the classpath share the same version number, `MigrationExecutor` throws `IllegalStateException` before any statements are executed.
- **Note on the properties class name.** The class is named `MirationProperties` (one `g`) in the source — this is the authoritative spelling used in code and Spring property binding. The configuration prefix `kinotic-migration` is correctly spelled.
- **No full Kinotic stack activated.** This application does not activate the full Kinotic server stack. It activates only `kinotic-sql` via `KinoticSqlAutoConfiguration` and the local `MigrationElasticsearchConfig`. The `@EnableKinotic` annotation is absent by design.
- **Non-zero exit on failure.** Any exception thrown during migration causes `MigrationInitializer` to propagate it out of the event listener, resulting in a non-zero JVM exit code. This makes failures visible to orchestration tools (shell scripts, Kubernetes init containers, CI pipelines) that check exit codes.
- **Refresh semantics.** Each migration record written to `migration_history` uses `refresh=wait_for`, ensuring the record is visible to subsequent searches within the same execution before the next migration version is evaluated.
