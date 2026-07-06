package org.kinotic.sql.executor.executors;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.kinotic.sql.domain.Column;
import org.kinotic.sql.domain.ColumnType;
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
 * {@code @timestamp} date field, the supplied mappings, and an optional native data stream lifecycle
 * retention period, then materializes the data stream.
 * Created by Navíd Mitchell 🤝 Claude on 6/18/26.
 */
@Component
@RequiredArgsConstructor
public class CreateDataStreamStatementExecutor implements StatementExecutor<CreateDataStreamStatement, Void> {
    // Elasticsearch requires every data stream document to carry a date field named exactly "@timestamp".
    private static final String TIMESTAMP_FIELD = "@timestamp";

    // Above the built-in template priority (200) so a stream named like a built-in pattern still wins.
    private static final long TEMPLATE_PRIORITY = 500L;

    private final ElasticsearchAsyncClient client;

    @Override
    public boolean supports(Statement statement) {
        return statement instanceof CreateDataStreamStatement;
    }

    @Override
    public CompletableFuture<Void> executeMigration(CreateDataStreamStatement statement) {
        validateTimeReference(statement);

        Map<String, Property> properties = new LinkedHashMap<>();
        properties.put(TIMESTAMP_FIELD, Property.of(p -> p.date(d -> d)));
        statement.columns().forEach(column ->
            properties.put(column.name(), TypeMapper.mapType(column)));

        return client.indices().putIndexTemplate(t -> t
                .name(statement.streamName())
                .indexPatterns(List.of(statement.streamName()))
                .dataStream(ds -> ds)
                .priority(TEMPLATE_PRIORITY)
                .template(te -> {
                    if (statement.dataRetention() != null) {
                        te.lifecycle(l -> l.dataRetention(r -> r.time(statement.dataRetention())));
                    }
                    return te.mappings(m -> m
                            .dynamic(DynamicMapping.Strict)
                            .properties(properties)
                    );
                })
        ).thenCompose(response -> client.indices().createDataStream(d -> d.name(statement.streamName())))
         .thenApply(response -> null);
    }

    @Override
    public CompletableFuture<Void> executeQuery(CreateDataStreamStatement statement, Map<String, Object> parameters) {
        throw new UnsupportedOperationException("CREATE DATA STREAM not supported as a named query");
    }

    /**
     * The time reference names a declared date column the application reads and writes directly; the
     * managed {@code @timestamp} is its Elasticsearch-required companion. The named column must therefore
     * exist and be a DATE.
     */
    private void validateTimeReference(CreateDataStreamStatement statement) {
        if (statement.timeReference() == null) {
            return;
        }
        Column reference = statement.columns().stream()
                .filter(column -> column.name().equals(statement.timeReference()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "TIME_REFERENCE '" + statement.timeReference()
                                + "' is not a declared column of data stream '" + statement.streamName() + "'"));
        if (reference.type() != ColumnType.DATE) {
            throw new IllegalArgumentException(
                    "TIME_REFERENCE column '" + reference.name() + "' must be of type DATE but was " + reference.type());
        }
    }
}
