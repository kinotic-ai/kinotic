package org.kinotic.sql.executor.executors;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import lombok.RequiredArgsConstructor;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.DeleteStatement;
import org.kinotic.sql.executor.QueryBuilder;
import org.kinotic.sql.executor.StatementExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Executes DELETE statements against Elasticsearch.
 * Deletes documents from an index based on a WHERE clause.
 * Created by Navíd Mitchell 🤪🤝Grok on 3/31/25.
 */
@Component
@RequiredArgsConstructor
public class DeleteStatementExecutor implements StatementExecutor<DeleteStatement, Long> {
    private final ElasticsearchAsyncClient client;

    @Override
    public boolean supports(Statement statement) {
        return statement instanceof DeleteStatement;
    }

    @Override
    public CompletableFuture<Long> executeMigration(DeleteStatement statement) {
        return executeQuery(statement, null);
    }

    @Override
    public CompletableFuture<Long> executeQuery(DeleteStatement statement, Map<String, Object> parameters) {
        return client.deleteByQuery(d -> d
                .index(statement.tableName())
                .query(QueryBuilder.buildQuery(statement.whereClause(), parameters))
                .refresh(statement.refresh())
        ).thenApply(DeleteByQueryResponse::deleted);
    }
}