package org.kinotic.persistence.sql.domain.statements;

import org.kinotic.persistence.sql.domain.Statement;

import java.util.List;

/**
 * Represents an INSERT statement in the DSL.
 * Inserts new documents into an Elasticsearch index with specified field values.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public record InsertStatement(String tableName,
                            List<String> columns,
                            List<Object> values,
                            boolean refresh) implements Statement {
    public InsertStatement(String tableName, List<String> columns, List<Object> values) {
        this(tableName, columns, values, false);
    }
} 