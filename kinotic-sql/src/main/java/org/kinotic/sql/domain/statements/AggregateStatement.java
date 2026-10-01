package org.kinotic.sql.domain.statements;

import org.kinotic.sql.domain.OrderBy;
import org.kinotic.sql.domain.Projection;
import org.kinotic.sql.domain.SelectExpression;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.WhereClause;

import java.util.List;

/**
 * Represents a SELECT statement in the DSL that calls a function or groups its rows, which runs on Elasticsearch SQL.
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 *
 * @param tableName   the index the documents are read from
 * @param projections the columns each returned row holds, in order
 * @param whereClause the condition a document must match, or {@code null} to match every document
 * @param groupBy     what the rows are grouped by; empty when the statement does not group
 * @param orderBy     the fields or column aliases the rows are ordered by, in precedence order; empty when the
 *                    statement names none
 * @param limit       the most rows the statement returns, or {@code null} when it names no limit
 */
public record AggregateStatement(String tableName,
                                 List<Projection> projections,
                                 WhereClause whereClause,
                                 List<SelectExpression> groupBy,
                                 List<OrderBy> orderBy,
                                 Integer limit) implements Statement {
}
