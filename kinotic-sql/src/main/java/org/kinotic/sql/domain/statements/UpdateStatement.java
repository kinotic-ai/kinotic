package org.kinotic.sql.domain.statements;

import java.util.List;
import java.util.Map;

import org.kinotic.sql.domain.Expression;
import org.kinotic.sql.domain.TableStatement;
import org.kinotic.sql.domain.WhereClause;

/**
 * Represents an UPDATE statement in the DSL.
 * Updates documents in an Elasticsearch index with SET assignments and a WHERE clause.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 *
 * @param assignments e.g., {"status": LiteralExpression("active"), "age": BinaryExpression("age", "+", "1")}
 */
public record UpdateStatement(String tableName,
                              Map<String, Expression> assignments,
                              WhereClause whereClause,
                              boolean refresh) implements TableStatement {

    @Override
    public List<String> tableNames() {
        return List.of(tableName);
    }

    @Override
    public UpdateStatement withTableNames(List<String> tableNames) {
        return new UpdateStatement(TableStatement.single(tableNames), assignments, whereClause, refresh);
    }
}