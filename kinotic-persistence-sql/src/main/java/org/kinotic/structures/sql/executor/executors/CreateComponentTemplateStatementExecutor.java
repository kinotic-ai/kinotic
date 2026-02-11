package org.kinotic.structures.sql.executor.executors;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.kinotic.structures.sql.domain.Statement;
import org.kinotic.structures.sql.domain.statements.ColumnTemplatePart;
import org.kinotic.structures.sql.domain.statements.CreateComponentTemplateStatement;
import org.kinotic.structures.sql.domain.statements.SettingTemplatePart;
import org.kinotic.structures.sql.executor.StatementExecutor;
import org.kinotic.structures.sql.executor.TypeMapper;
import org.springframework.stereotype.Component;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import lombok.RequiredArgsConstructor;

/**
 * Executes CREATE COMPONENT TEMPLATE statements against Elasticsearch.
 * Creates component templates with settings and mappings.
 * Created by Navíd Mitchell 🤪🤝Grok on 3/31/25.
 */
@Component
@RequiredArgsConstructor
public class CreateComponentTemplateStatementExecutor implements StatementExecutor<CreateComponentTemplateStatement, Void> {
    private final ElasticsearchAsyncClient client;

    @Override
    public boolean supports(Statement statement) {
        return statement instanceof CreateComponentTemplateStatement;
    }

    @Override
    public CompletableFuture<Void> executeMigration(CreateComponentTemplateStatement statement) {
        Map<String, Property> properties = new HashMap<>();
        Map<String, String> settings = new HashMap<>();

        statement.parts().forEach(part -> {
            if (part instanceof ColumnTemplatePart columnPart) {
                properties.put(columnPart.column().name(), TypeMapper.mapType(columnPart.column()));
            } else if (part instanceof SettingTemplatePart settingPart) {
                settings.put(settingPart.name(), settingPart.value());
            }
        });

        return client.cluster().putComponentTemplate(t -> t
                .name(statement.templateName())
                .template(te -> te
                        .settings(s -> {
                            settings.forEach((key, value) -> {
                                switch (key) {
                                    case "NUMBER_OF_SHARDS" -> s.numberOfShards(value);
                                    case "NUMBER_OF_REPLICAS" -> s.numberOfReplicas(value);
                                }
                            });
                            return s;
                        })
                        .mappings(m -> properties.isEmpty() ? m : m.properties(properties))
                )
        ).thenApply(response -> null);
    }

    @Override
    public CompletableFuture<Void> executeQuery(CreateComponentTemplateStatement statement, Map<String, Object> parameters) {
        throw new UnsupportedOperationException("CREATE COMPONENT TEMPLATE not supported as a named query");
    }
}