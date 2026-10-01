package org.kinotic.sql.executor;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.kinotic.sql.domain.WhereClause;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility class for building Elasticsearch queries from SQL WHERE clauses.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public class QueryBuilder {

    // Mirrors the grammar's numberLiteral rule, so a literal that parsed as a number stays one here
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");

    public static Query buildQuery(WhereClause whereClause, Map<String, Object> parameters) {
        if (whereClause instanceof WhereClause.Condition condition) {
            String value = condition.getValue();
            String field = condition.getField();
            String operator = condition.getOperator();

            if (ParameterUtils.isReference(value)) {
                Object paramValue = ParameterUtils.resolve(ParameterUtils.nameOf(value), parameters);
                // Convert parameter to FieldValue
                FieldValue fieldValue;
                if (paramValue instanceof Number) {
                    fieldValue = FieldValue.of(((Number) paramValue).doubleValue());
                } else if (paramValue instanceof Boolean) {
                    fieldValue = FieldValue.of((Boolean) paramValue);
                } else {
                    fieldValue = FieldValue.of(paramValue.toString());
                }
                return buildComparisonQuery(field, operator, fieldValue);
            } else {
                FieldValue fieldValue = parseValue(value);
                return buildComparisonQuery(field, operator, fieldValue);
            }
        } else if (whereClause instanceof WhereClause.AndClause andClause) {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            boolQuery.filter(buildQuery(andClause.getLeft(), parameters));
            boolQuery.filter(buildQuery(andClause.getRight(), parameters));
            return Query.of(q -> q.bool(boolQuery.build()));
        } else if (whereClause instanceof WhereClause.OrClause orClause) {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            boolQuery.should(buildQuery(orClause.getLeft(), parameters));
            boolQuery.should(buildQuery(orClause.getRight(), parameters));
            boolQuery.minimumShouldMatch("1");
            return Query.of(q -> q.bool(boolQuery.build()));
        }
        throw new IllegalStateException("Unsupported WHERE clause type");
    }

    private static Query buildComparisonQuery(String field, String operator, FieldValue value) {
        switch (operator) {
            case "=":
                return Query.of(q -> q.term(t -> t.field(field).value(value)));
            case "!=":
                return Query.of(q -> q.bool(b -> b.mustNot(m -> m.term(t -> t.field(field).value(value)))));
            case "<":
            case ">":
            case "<=":
            case ">=":
                if (value._kind() == FieldValue.Kind.Boolean) {
                    throw new IllegalArgumentException("Boolean values can only be compared with = and != operators");
                }

                if (value._kind() == FieldValue.Kind.Double || value._kind() == FieldValue.Kind.Long) {
                    double numericValue = value._kind() == FieldValue.Kind.Double ? 
                        value.doubleValue() : value.longValue();

                    return Query.of(q -> q.range(r -> r.number(n -> {
                        n.field(field);

                        switch (operator) {
                            case "<": n.lt(numericValue); break;
                            case ">": n.gt(numericValue); break;
                            case "<=": n.lte(numericValue); break;
                            case ">=": n.gte(numericValue); break;
                        }

                        return n;
                    })));
                } else {
                    return Query.of(q -> q.range(r -> r.term(t -> {
                        t.field(field);

                        switch (operator) {
                            case "<": t.lt(value.stringValue()); break;
                            case ">": t.gt(value.stringValue()); break;
                            case "<=": t.lte(value.stringValue()); break;
                            case ">=": t.gte(value.stringValue()); break;
                        }

                        return t;
                    })));
                }
            default:
                throw new IllegalStateException("Unsupported operator: " + operator);
        }
    }

    private static FieldValue parseValue(String value) {
        Object literal = literalValue(value);
        return switch (literal) {
            // unboxed, since a boxed argument resolves to FieldValue.of(Object), whose value has no kind
            case Boolean bool -> FieldValue.of(bool.booleanValue());
            case Double number -> FieldValue.of(number.doubleValue());
            case Long number -> FieldValue.of(number.longValue());
            default -> FieldValue.of((String) literal);
        };
    }

    /**
     * The value a WHERE condition's literal stands for: a {@link String} for a quoted string, a {@link Boolean}, or a
     * {@link Long} or {@link Double} for a number.
     *
     * @param value the literal as a {@link WhereClause.Condition} holds it
     */
    static Object literalValue(String value) {
        Object ret;
        if (value.startsWith("'") && value.endsWith("'")) {
            ret = value.substring(1, value.length() - 1);
        } else if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            ret = Boolean.parseBoolean(value);
        } else if (NUMBER_PATTERN.matcher(value).matches()) {
            ret = value.indexOf('.') >= 0 ? (Object) Double.parseDouble(value) : (Object) Long.parseLong(value);
        } else {
            ret = value; // Fallback to string if not a number
        }
        return ret;
    }
}