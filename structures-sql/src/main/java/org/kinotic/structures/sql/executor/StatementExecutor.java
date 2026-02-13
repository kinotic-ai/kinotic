package org.kinotic.structures.sql.executor;

import org.kinotic.structures.sql.domain.Statement;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for executing SQL-like statements against Elasticsearch.
 * Provides methods for migration execution and named query execution.
 * Created by Navíd Mitchell 🤪🤝Grok on 3/31/25.
 */
public interface StatementExecutor<T extends Statement, R> {
    boolean supports(Statement statement);

    // For migrations (async, returns a value)
    CompletableFuture<R> executeMigration(T statement);

    // For named queries
    CompletableFuture<R> executeQuery(T statement, Map<String, Object> parameters);
}