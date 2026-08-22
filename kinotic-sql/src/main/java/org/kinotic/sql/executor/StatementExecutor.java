package org.kinotic.sql.executor;

import org.kinotic.sql.domain.Statement;

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

    /**
     * Executes a statement, supplying the values its {@code :name} parameters refer to.
     *
     * @param statement  the statement to execute
     * @param parameters the values the statement's parameters refer to, keyed by parameter name
     */
    CompletableFuture<R> executeQuery(T statement, Map<String, Object> parameters);
}