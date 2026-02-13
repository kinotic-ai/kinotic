package org.kinotic.structures.sql.domain.statements;

import org.kinotic.structures.sql.domain.Statement;
import org.kinotic.structures.sql.domain.WhereClause;

/**
 * Represents a DELETE statement in the DSL.
 * Deletes documents from an Elasticsearch index based on a WHERE clause.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public record DeleteStatement(String tableName,
                            WhereClause whereClause,
                            boolean refresh) implements Statement {
    public DeleteStatement(String tableName, WhereClause whereClause) {
        this(tableName, whereClause, false);
    }
}