package org.kinotic.structures.sql.domain.statements;

import org.kinotic.structures.sql.domain.Column;
import org.kinotic.structures.sql.domain.Statement;

/**
 * Represents an ALTER TABLE statement in the DSL.
 * Adds a new field to an existing Elasticsearch index.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public record AlterTableStatement(String tableName, Column column) implements Statement {
}