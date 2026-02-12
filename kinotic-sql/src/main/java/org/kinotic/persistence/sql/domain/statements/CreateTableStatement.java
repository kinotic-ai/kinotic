package org.kinotic.persistence.sql.domain.statements;

import java.util.List;

import org.kinotic.persistence.sql.domain.Column;
import org.kinotic.persistence.sql.domain.Statement;

/**
 * Represents a CREATE TABLE statement in the DSL.
 * Creates a new Elasticsearch index with specified field mappings.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public record CreateTableStatement(String tableName,
                                 List<Column> columns,
                                 boolean ifNotExists) implements Statement {
    public CreateTableStatement(String tableName, List<Column> columns) {
        this(tableName, columns, false);
    }
}