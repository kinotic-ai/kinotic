package org.kinotic.sql.domain.statements;

import org.kinotic.sql.domain.OrderBy;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.WhereClause;

import java.util.List;

/**
 * Represents a SELECT statement in the DSL.
 * Reads the documents of an Elasticsearch index that match a WHERE clause, whole or projected to the listed columns.
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 *
 * @param tableName   the index the documents are read from
 * @param columns     the top-level fields each document is projected to, or an empty list for the whole document
 *                    ({@code SELECT *})
 * @param whereClause the condition a document must match, or {@code null} to match every document
 * @param orderBy     the fields the documents are ordered by, in precedence order; empty when the statement
 *                    names none
 * @param limit       the most documents the statement returns, or {@code null} when it names no limit
 */
public record SelectStatement(String tableName,
                              List<String> columns,
                              WhereClause whereClause,
                              List<OrderBy> orderBy,
                              Integer limit) implements Statement {
}
