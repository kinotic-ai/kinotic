package org.kinotic.sql.executor;

import org.kinotic.sql.domain.AllFields;
import org.kinotic.sql.domain.FieldReference;
import org.kinotic.sql.domain.FunctionArgument;
import org.kinotic.sql.domain.FunctionCall;
import org.kinotic.sql.domain.NamedParameter;
import org.kinotic.sql.domain.OrderBy;
import org.kinotic.sql.domain.Projection;
import org.kinotic.sql.domain.SelectExpression;
import org.kinotic.sql.domain.ValueArgument;
import org.kinotic.sql.domain.WhereClause;
import org.kinotic.sql.domain.statements.AggregateStatement;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Writes an {@link AggregateStatement} as the Elasticsearch SQL statement that runs it.
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 */
public class ElasticsearchSqlWriter {

    /**
     * The Elasticsearch SQL for the statement. The statement's table is read as the index it names; every string,
     * boolean and parameter is passed as a placeholder value.
     *
     * @param statement a resolved aggregate, whose table name is an index
     */
    public static ElasticsearchSql write(AggregateStatement statement) {
        // Identifiers, function names and numbers are written as the grammar restricts them, which leaves no quote
        // in them; every other value goes through a placeholder, so no text a statement's author wrote is quoted here
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ");

        StringJoiner projections = new StringJoiner(", ");
        for (Projection projection : statement.projections()) {
            String column = expression(projection.expression(), parameters);
            projections.add(projection.alias() != null ? column + " AS " + projection.alias() : column);
        }
        sql.append(projections).append(" FROM \"").append(statement.tableName()).append('"');

        if (statement.whereClause() != null) {
            sql.append(" WHERE ").append(condition(statement.whereClause(), parameters));
        }
        if (!statement.groupBy().isEmpty()) {
            StringJoiner groupBy = new StringJoiner(", ");
            statement.groupBy().forEach(expression -> groupBy.add(expression(expression, parameters)));
            sql.append(" GROUP BY ").append(groupBy);
        }
        if (!statement.orderBy().isEmpty()) {
            StringJoiner orderBy = new StringJoiner(", ");
            for (OrderBy term : statement.orderBy()) {
                orderBy.add(term.field() + " " + term.direction().name());
            }
            sql.append(" ORDER BY ").append(orderBy);
        }
        if (statement.limit() != null) {
            sql.append(" LIMIT ").append(statement.limit());
        }
        return new ElasticsearchSql(sql.toString(), parameters);
    }

    private static String expression(SelectExpression expression, List<Object> parameters) {
        return switch (expression) {
            case FieldReference field -> field.path();
            case FunctionCall call -> {
                StringJoiner arguments = new StringJoiner(", ", call.name() + "(", ")");
                call.arguments().forEach(argument -> arguments.add(argument(argument, parameters)));
                yield arguments.toString();
            }
        };
    }

    private static String argument(FunctionArgument argument, List<Object> parameters) {
        return switch (argument) {
            case SelectExpression expression -> expression(expression, parameters);
            case AllFields _ -> "*";
            case ValueArgument(Number number) -> number.toString();
            case ValueArgument(Object value) -> placeholder(value, parameters);
        };
    }

    private static String condition(WhereClause whereClause, List<Object> parameters) {
        return switch (whereClause) {
            case WhereClause.Condition condition -> {
                Object value = ParameterUtils.isReference(condition.getValue())
                        ? new NamedParameter(ParameterUtils.nameOf(condition.getValue()))
                        : QueryBuilder.literalValue(condition.getValue());
                yield condition.getField() + " " + condition.getOperator() + " " + placeholder(value, parameters);
            }
            case WhereClause.AndClause and -> "(" + condition(and.getLeft(), parameters) + " AND "
                    + condition(and.getRight(), parameters) + ")";
            case WhereClause.OrClause or -> "(" + condition(or.getLeft(), parameters) + " OR "
                    + condition(or.getRight(), parameters) + ")";
            default -> throw new IllegalStateException("Unsupported WHERE clause type " + whereClause.getClass().getSimpleName());
        };
    }

    private static String placeholder(Object value, List<Object> parameters) {
        parameters.add(value);
        return "?";
    }
}
