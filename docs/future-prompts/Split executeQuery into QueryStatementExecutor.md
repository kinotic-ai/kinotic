# Split `executeQuery` off `StatementExecutor` into a `QueryStatementExecutor` capability interface

## Objective
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

## Out of scope (do NOT attempt here — these are the follow-on integration, not this refactor)
Wiring the persistence consumer is a separate, larger effort. Note the gaps for context, but
leave them:
1. **Return-type impedance.** `QueryExecutor.execute` returns `List<T>`/`Page<T>`; `executeQuery`
   returns `R` (`Void`/`Long`/`String`). The eventual contract's `R` should be designed against
   `QueryExecutor`'s shape — but not in this PR.
2. **No SELECT in `kinotic-sql`.** The grammar has no `SELECT` statement, so `SelectQueryExecutor`
   has nothing to delegate to yet. Adding SELECT (statement + parser + executor returning rows) is
   a separate feature.
3. **Param-shape adapter.** kinotic-sql takes `Map<String,Object>`; persistence carries
   `QueryContext` / `ParameterHolder` + `List<Object> queryParameters`. A bridge is needed when the
   consumer is actually wired.

Also do not touch the data-stream/ILM work (already merged via its own PR) and do not delete
`executeQuery` outright — the consumer above is planned.

## Build & verify (Claude Code cloud)
No grammar change, so no ANTLR regen needed. JDK 25 is required to compile:

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

The existing `DataStreamMigrationParserTest` and the other `kinotic-sql` tests must stay green.
A quick `grep` for `executeQuery` afterward should show it only on `QueryStatementExecutor` and
its three implementers.

## Git
Develop on a NEW branch (not the data-stream branch / PR #237). Behavior-preserving — keep it a
single focused PR.
