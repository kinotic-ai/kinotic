package org.kinotic.sql.executor.executors;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.InsertStatement;
import org.kinotic.sql.executor.StatementExecutor;
import org.springframework.stereotype.Component;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import lombok.RequiredArgsConstructor;

/**
 * Executes INSERT statements against Elasticsearch.
 * Handles insertion of documents with specified field values.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
@Component
@RequiredArgsConstructor
public class InsertStatementExecutor implements StatementExecutor<InsertStatement, Void> {

    private final ElasticsearchAsyncClient client;

    @Override
    public boolean supports(Statement statement) {
        return statement instanceof InsertStatement;
    }

    @Override
    public CompletableFuture<Void> executeMigration(InsertStatement statement) {
        return executeQuery(statement, null);
    }

    @Override
    public CompletableFuture<Void> executeQuery(InsertStatement statement, Map<String, Object> parameters) {
        return client.indices().getMapping(m -> m.index(statement.tableName()))
            .thenCompose(mapping -> {
                // Create a document with the specified values
                final Map<String, Object> document = new HashMap<>();
                
                if (statement.columns().isEmpty()) {
                    // If no columns specified, we need to validate against mapping
                    IndexMappingRecord indexMapping = mapping.get(statement.tableName());
                    if (indexMapping == null) {
                        throw new IllegalArgumentException("Index '" + statement.tableName() + "' does not exist");
                    }
                    Map<String, Property> properties = indexMapping.mappings().properties();
                    
                    // Validate we have the right number of values
                    if (statement.values().size() != properties.size()) {
                        throw new IllegalArgumentException("Number of values must match number of fields in index when no columns specified");
                    }
                    
                    // Add values in order of fields in mapping
                    int i = 0;
                    for (String field : properties.keySet()) {
                        document.put(field, statement.values().get(i++));
                    }
                } else {
                    // If columns are specified, just add the values directly
                    for (int i = 0; i < statement.columns().size(); i++) {
                        document.put(statement.columns().get(i), statement.values().get(i));
                    }
                }

                // Index the document with optional refresh. When an "id" column is provided,
                // use its value as the Elasticsearch _id so callers can look the document up
                // by that same id later (e.g. IamCredentialStore#findById).
                Object idValue = document.get("id");
                String documentId = idValue instanceof String s ? s : null;
                return client.index(i -> {
                    i.index(statement.tableName())
                     .document(document)
                     .refresh(statement.refresh() ? Refresh.True : Refresh.False);
                    if (documentId != null) {
                        i.id(documentId);
                    }
                    return i;
                }).thenApply(response -> null);
            });
    }
} 