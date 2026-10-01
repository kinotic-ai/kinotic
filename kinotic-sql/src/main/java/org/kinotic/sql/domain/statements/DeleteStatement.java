package org.kinotic.sql.domain.statements;

import java.util.List;

import org.kinotic.sql.domain.TableStatement;
import org.kinotic.sql.domain.WhereClause;

/**
 * Represents a DELETE statement in the DSL.
 * Deletes documents from an Elasticsearch index based on a WHERE clause.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public record DeleteStatement(String tableName,
                            WhereClause whereClause,
                            boolean refresh) implements TableStatement {

    @Override
    public List<String> tableNames() {
        return List.of(tableName);
    }

    @Override
    public DeleteStatement withTableNames(List<String> tableNames) {
        return new DeleteStatement(TableStatement.single(tableNames), whereClause, refresh);
    }
}