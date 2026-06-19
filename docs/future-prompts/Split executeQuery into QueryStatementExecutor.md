# Split `executeQuery` into `QueryStatementExecutor` + add a SELECT (with grouping) statement

This is two intertwined pieces of work:
1. **Refactor:** move `executeQuery` off the base `StatementExecutor` into a narrower
   `QueryStatementExecutor` capability interface (behavior-preserving).
2. **Feature:** add a `SELECT` statement to `kinotic-sql` — with `GROUP BY` / aggregate functions,
   so it absorbs what the persistence `AGGREGATE` path does today. Column names are derived from the
   SELECT projection on every page (not cached, not carried in the cursor), so the executor stays
   stateless. This is what lets the persistence named-query subsystem run on `kinotic-sql` and retire
   the Vert.x `_sql` client + column cache.

## Objective (refactor)
In `kinotic-sql`, `StatementExecutor<T, R>` declares two operations:

```java
// kinotic-sql/src/main/java/org/kinotic/sql/executor/StatementExecutor.java
public interface StatementExecutor<T extends Statement, R> {
    boolean supports(Statement statement);
    CompletableFuture<R> executeMigration(T statement);
    CompletableFuture<R> executeQuery(T statement, Map<String, Object> parameters);
}
```

7 of the 10 implementations throw `UnsupportedOperationException` from `executeQuery`
(textbook **Refused Bequest**). Move `executeQuery` into a narrower capability interface
that only the query/DML statements implement, so DDL executors stop carrying a method they
refuse. This is a behavior-preserving refactor — no statement gains or loses runtime capability.

## Why this matters
`executeQuery` has **no caller today**, but it is not dead — it is the planned integration seam
for the persistence-layer named-query subsystem:

```java
// kinotic-persistence/.../internal/api/services/sql/DefaultQueryExecutorFactory.java:71-80
return switch (queryType) {                 // SqlQueryType: AGGREGATE, DELETE, INSERT, SELECT, UPDATE
    case AGGREGATE -> new AggregateQueryExecutor(...);   // only wired arm (ElasticVertxClient)
    case DELETE    -> throw new NotImplementedException("Delete not supported yet");
    case INSERT    -> throw new NotImplementedException("Insert not supported yet");
    case SELECT    -> throw new NotImplementedException("Select without aggregate not supported yet");
    case UPDATE    -> throw new NotImplementedException("Update not supported yet");
};
```

The plan is for these `NotImplementedException` arms (and the `SelectQueryExecutor` /
`UpdateQueryExecutor` stubs that currently `return null`) to delegate to the `kinotic-sql`
`executeQuery` logic behind the scenes. `SqlQueryType`'s members are exactly the DML/query
statements — so a `QueryStatementExecutor` for that subset maps 1:1 onto what the factory
dispatches, and gives the factory a precise type to resolve against.

## Current state — verify with fresh inspection before acting
Distribution of `executeQuery` across the 10 executors in
`kinotic-sql/src/main/java/org/kinotic/sql/executor/executors/`:

| Executor | `executeQuery` today | Reads `parameters`? |
|---|---|---|
| `UpdateStatementExecutor` | real logic | **yes** — `QueryBuilder.buildQuery(where, parameters)`, `buildScript(..., parameters)` (binds `?`) |
| `DeleteStatementExecutor` | real logic | **yes** — `QueryBuilder.buildQuery(where, parameters)` |
| `InsertStatementExecutor` | real logic | no — declares `parameters` but ignores it (`?` binding for INSERT is unimplemented) |
| `CreateTableStatementExecutor` | real logic | no — DDL; `executeMigration` delegates to it |
| `AlterTableStatementExecutor` | `throw UnsupportedOperationException` | — |
| `ReindexStatementExecutor` | `throw` (returns `R = String`) | — |
| `CreateComponentTemplateStatementExecutor` | `throw` | — |
| `CreateIndexTemplateStatementExecutor` | `throw` | — |
| `CreateDataStreamStatementExecutor` | `throw` | — |
| `CreateLifecyclePolicyStatementExecutor` | `throw` | — |

`MigrationExecutor` (the only current consumer) calls **only** `supports` + `executeMigration`
— see `executeStatement`/`findExecutor`. It is unaffected by this refactor.

## Target design
```java
// StatementExecutor — every executor; all MigrationExecutor needs
public interface StatementExecutor<T extends Statement, R> {
    boolean supports(Statement statement);
    CompletableFuture<R> executeMigration(T statement);
}

// QueryStatementExecutor — only statements that run as a parameterized named query
public interface QueryStatementExecutor<T extends Statement, R> extends StatementExecutor<T, R> {
    CompletableFuture<R> executeQuery(T statement, Map<String, Object> parameters);
}
```

Per-executor changes:
- **`UpdateStatementExecutor`, `DeleteStatementExecutor`, `InsertStatementExecutor`** → implement
  `QueryStatementExecutor`. They already have `executeMigration` delegating
  `return executeQuery(statement, null)`; leave that. (`INSERT`/`SELECT` are in `SqlQueryType`,
  so `Insert` stays query-capable even though its `?` binding is still a TODO.)
- **`CreateTableStatementExecutor`** → move the body from `executeQuery` **into** `executeMigration`
  (CREATE TABLE is DDL, not in `SqlQueryType`), drop `executeQuery`. Becomes plain `StatementExecutor`.
- **`AlterTable`, `Reindex`, `CreateComponentTemplate`, `CreateIndexTemplate`, `CreateDataStream`,
  `CreateLifecyclePolicy`** → delete the throwing `executeQuery`. Plain `StatementExecutor`.

`MigrationExecutor` keeps `List<StatementExecutor<?, ?>>` — `QueryStatementExecutor` extends it,
so all executors still register and dispatch. No change there.

## Feature: add SELECT (with grouping), absorbing AGGREGATE
The persistence factory dispatches `SqlQueryType {AGGREGATE, DELETE, INSERT, SELECT, UPDATE}`.
`kinotic-sql` already has INSERT/UPDATE/DELETE but **no SELECT and no AGGREGATE**. Per the decision,
add a single `SELECT` statement that supports `GROUP BY` + aggregate functions (COUNT/SUM/AVG/MIN/
MAX/…), so AGGREGATE is not a separate statement — it's SELECT with grouping.

Grammar/shape (verify ES SQL feature support as you go):
- Projections, the existing `whereClause`, `GROUP BY`, aggregate functions, `ORDER BY`,
  `LIMIT`/fetch size.
- Back it with the **typed** client `client.sql().query(...)` (9.x `ElasticsearchSqlAsyncClient`),
  which exposes `columns()` / `rows()` / `cursor()` plus `filter` / `params` / `timeZone` /
  `pageTimeout` / `fetchSize`. The existing Vert.x `DefaultElasticVertxClient` exists because an
  older Java client "was missing functionality"; confirm 9.x covers it (it appears to) so the
  named-query path can stop using raw `_sql` HTTP.

### Statelessness: derive column names from the statement (the crux)
ES `_sql` returns `columns` only on the **first** page; pages 2..N return `{rows, cursor}` with no
columns. `DefaultElasticVertxClient` bridges this with a node-local Caffeine cache keyed by cursor
(`DefaultElasticVertxClient.java:242-264`). That is both stateful **and** a clustering bug — a
follow-up page landing on another replica throws "Cursor has expired".

Do NOT port the cache, and do NOT invent a self-describing cursor token either. The only state
threaded across pages is the **ordered list of column names** — the row-mapping code only reads
`ElasticColumn.getName()`; `ElasticColumn.type` is parsed but never used (verify:
`DefaultElasticVertxClient.java:237,278`). And those names are fully determined by the SELECT
projection, which the executor has on every page. So recompute them from the parsed statement and
ignore ES's page-1 `columns` entirely:

```text
  page 1   ES→{columns,rows,cursor}     → ignore ES columns; names = projection of parsed SELECT
  page N   ES←{cursor}; ES→{rows,cursor} → names = projection of parsed SELECT   (recomputed, not stored)
```

ES returns columns in select-list order, so the parsed projection lines up positionally with each
row's values. The cursor handed back stays exactly what `CursorPage.getCursor()` returns today — the
**raw ES cursor string**. No token, no cache, no TTL, cluster-safe; the whole `columnsCache` goes away.

Two cases need a deterministic naming rule:
- **`SELECT *`** — expand to an explicit projection from the entity schema (the persistence adapter
  holds the `EntityDefinition`) *before* issuing the query, so ES returns exactly those columns in
  that order and there is no `*`-ordering to reverse-engineer.
- **Aggregates** — use the alias when present (`COUNT(*) AS cnt`), else the expression text as the
  column name. Deterministic from the statement either way.

### Contract impact
SELECT returns rows + a continuation, so the query contract's `R` must be page-shaped for it:
- UPDATE/DELETE → `CompletableFuture<Long>`; INSERT → id/void (unchanged).
- SELECT → `CompletableFuture<CursorPage<Map<String,Object>>>` (rows keyed by the derived column
  names; cursor is the raw ES cursor string).

`executeQuery` will also need a cursor input for SELECT (today it only takes
`Map<String,Object> parameters`) so it can issue the `{cursor}` continuation request. Decide whether
to pass a small args type or add a pageable/cursor parameter; keep kinotic-sql's dependency surface
minimal.

## Out of scope (follow-on — note for context, do not build here)
- **Full persistence rewiring.** Turning `SelectQueryExecutor`/`AggregateQueryExecutor` into thin
  adapters (`QueryContext` → kinotic-sql SELECT `executeQuery` → `CursorPage<Map>` → convert to `T`)
  and retiring `DefaultElasticVertxClient` + `columnsCache` from the named-query path is the payoff,
  but lands after this PR proves the SELECT executor + stateless cursor in `kinotic-sql`.
- **Param-shape adapter.** kinotic-sql takes `Map<String,Object>`; persistence carries
  `QueryContext`/`ParameterHolder` + `List<Object> queryParameters` — bridge it during rewiring.

Do not touch the data-stream/ILM work (already merged via its own PR) and do not delete
`executeQuery` outright — the consumer above is planned.

## Build & verify (Claude Code cloud)
The SELECT statement changes the grammar, so the ANTLR parser must be regenerated. The generated
parser is committed under `kinotic-sql/.../parser/` and is only regenerated when the task is invoked
explicitly:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :kinotic-sql:generateGrammarSource   # runs with JDK 21; rewrites the committed parser
```

JDK 25 is required to compile:

```bash
# JDK 25 if not present (Oracle CDN is allowlisted)
curl -sL "https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz" -o /tmp/jdk25.tar.gz
cd /tmp && tar xzf jdk25.tar.gz   # extracts to /tmp/jdk-25.<minor>, confirm the exact dir

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
CLAUDE_CLOUD_COMPILE=true ./gradlew :kinotic-sql:compileJava \
  -Porg.gradle.java.installations.paths=/tmp/jdk-25.0.3   # use the actual extracted dir

CLAUDE_CLOUD_COMPILE=true ./gradlew :kinotic-sql:test \
  -Porg.gradle.java.installations.paths=/tmp/jdk-25.0.3
```

The existing `DataStreamMigrationParserTest` and the other `kinotic-sql` tests must stay green; add
parser tests for SELECT (projections, WHERE, GROUP BY, ORDER BY, LIMIT). A quick `grep` for
`executeQuery` afterward should show it only on `QueryStatementExecutor` and its implementers
(Update, Delete, Insert, Select).

## Git
Develop on a NEW branch (not the data-stream branch / PR #237). Behavior-preserving — keep it a
single focused PR.
