package org.kinotic.sql.executor.executors;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.CreateDataStreamStatement;
import org.kinotic.sql.executor.StatementExecutor;
import org.kinotic.sql.executor.TypeMapper;
import org.springframework.stereotype.Component;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import lombok.RequiredArgsConstructor;

/**
 * Executes CREATE DATA STREAM statements against Elasticsearch.
 * Creates a data-stream-backing index template (pattern matching the stream name) with a managed
 * {@code @timestamp} date field, the supplied mappings, and an optional ILM lifecycle policy, then
 * materializes the data stream.
 * Created by Navíd Mitchell 🤝 Grok on 6/18/26.
 */
@Component
@RequiredArgsConstructor
public class CreateDataStreamStatementExecutor implements StatementExecutor<CreateDataStreamStatement, Void> {
    // Elasticsearch requires every data stream document to carry a date field named exactly "@timestamp".
    private static final String TIMESTAMP_FIELD = "@timestamp";

    private final ElasticsearchAsyncClient client;

    @Override
    public boolean supports(Statement statement) {
        return statement instanceof CreateDataStreamStatement;
    }

    @Override
    public CompletableFuture<Void> executeMigration(CreateDataStreamStatement statement) {
        Map<String, Property> properties = new LinkedHashMap<>();
        properties.put(TIMESTAMP_FIELD, Property.of(p -> p.date(d -> d)));
        statement.columns().forEach(column ->
            properties.put(column.name(), TypeMapper.mapType(column)));

        return client.indices().putIndexTemplate(t -> t
                .name(statement.streamName())
                .indexPatterns(List.of(statement.streamName()))
                .dataStream(ds -> ds)
                .template(te -> te
                        .settings(s -> {
                            if (statement.lifecyclePolicy() != null) {
                                s.lifecycle(l -> l.name(statement.lifecyclePolicy()));
                            }
                            return s;
                        })
                        .mappings(m -> m
                                .dynamic(DynamicMapping.Strict)
                                .properties(properties)
                        )
                )
        ).thenCompose(response -> client.indices().createDataStream(d -> d.name(statement.streamName())))
         .thenApply(response -> null);
    }

    @Override
    public CompletableFuture<Void> executeQuery(CreateDataStreamStatement statement, Map<String, Object> parameters) {
        throw new UnsupportedOperationException("CREATE DATA STREAM not supported as a named query");
    }
}
