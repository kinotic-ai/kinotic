package org.kinotic.sql.domain;

/**
 * One column an aggregate SELECT returns.
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 *
 * @param expression what the column holds
 * @param alias      the column's name in each returned row, or {@code null} to keep the name Elasticsearch gives it
 */
public record Projection(SelectExpression expression, String alias) {
}
