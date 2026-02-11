package org.kinotic.structures.sql.executor.executors;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import lombok.RequiredArgsConstructor;
import org.kinotic.structures.sql.domain.Statement;
import org.kinotic.structures.sql.domain.statements.AlterTableStatement;
import org.kinotic.structures.sql.executor.StatementExecutor;
import org.kinotic.structures.sql.executor.TypeMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Executes ALTER TABLE statements against Elasticsearch.
 * Adds new fields to existing indices with proper type mapping.
 * Created by Navíd Mitchell 🤪🤝Grok on 3/31/25.
 */
@Component
@RequiredArgsConstructor
public class AlterTableStatementExecutor implements StatementExecutor<AlterTableStatement, Void> {
    private final ElasticsearchAsyncClient client;

    @Override
    public boolean supports(Statement statement) {
        return statement instanceof AlterTableStatement;
    }

    @Override
    public CompletableFuture<Void> executeMigration(AlterTableStatement statement) {
        return client.indices().putMapping(m -> m
                .index(statement.tableName())
                .properties(statement.column().name(), TypeMapper.mapType(statement.column()))
        ).thenApply(response -> null);
    }

    @Override
    public CompletableFuture<Void> executeQuery(AlterTableStatement statement, Map<String, Object> parameters) {
        throw new UnsupportedOperationException("ALTER TABLE not supported as a named query");
    }
}