package org.kinotic.auth.compilers;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.kinotic.auth.api.expressions.*;
import org.kinotic.auth.parsers.PolicyParseException;

import java.util.List;
import java.util.Map;

/**
 * Compiles a {@link PolicyExpression} AST into an Elasticsearch {@link Query}.
 * <p>
 * Participant attribute paths are resolved at compile time against a provided map of
 * participant attribute values. Resource attribute paths are mapped to document fields.
 * <p>
 * For example, given the expression:
 * <pre>
 * participant.department == entity.department and entity.status in ['active', 'pending']
 * </pre>
 * and a participant with {@code department = "finance"}, this produces:
 * <pre>
 * { "bool": { "filter": [
 *     { "term": { "department": "finance" } },
 *     { "terms": { "status": ["active", "pending"] } }
 * ]}}
 * </pre>
 */
public class EsQueryCompiler {

    /**
     * Compiles a policy expression AST into an Elasticsearch query.
     *
     * @param expression          the policy expression AST
     * @param participantAttributes the current participant's attributes, keyed by field path
     *                            (e.g., "department", "role", "level")
     * @return the Elasticsearch query
     * @throws PolicyParseException if the expression references unsupported patterns
     */
    public static Query compile(PolicyExpression expression, Map<String, Object> participantAttributes) {
        return compileExpression(expression, participantAttributes);
    }

    private static Query compileExpression(PolicyExpression expression, Map<String, Object> participantAttributes) {
        return switch (expression) {
            case AndExpression and -> {
                BoolQuery.Builder b = new BoolQuery.Builder();
                b.filter(compileExpression(and.left(), participantAttributes));
                b.filter(compileExpression(and.right(), participantAttributes));
                yield Query.of(q -> q.bool(b.build()));
            }
            case OrExpression or -> {
                BoolQuery.Builder b = new BoolQuery.Builder();
                b.should(compileExpression(or.left(), participantAttributes));
                b.should(compileExpression(or.right(), participantAttributes));
                b.minimumShouldMatch("1");
                yield Query.of(q -> q.bool(b.build()));
            }
            case NotExpression not -> {
                BoolQuery.Builder b = new BoolQuery.Builder();
                b.mustNot(compileExpression(not.expression(), participantAttributes));
                yield Query.of(q -> q.bool(b.build()));
            }
            case ComparisonExpression comp -> compileComparison(comp, participantAttributes);
        };
    }

    private static Query compileComparison(ComparisonExpression comp, Map<String, Object> participantAttributes) {
        String fieldName = resolveResourceField(comp.left());
        Operand right = comp.right();

        return switch (comp.operator()) {
            case EQUALS, CONTAINS -> {
                FieldValue value = toFieldValue(resolveOperandValue(right, participantAttributes));
                yield Query.of(q -> q.term(t -> t.field(fieldName).value(value)));
            }
            case NOT_EQUALS -> {
                FieldValue value = toFieldValue(resolveOperandValue(right, participantAttributes));
                yield Query.of(q -> q.bool(b -> b.mustNot(m -> m.term(t -> t.field(fieldName).value(value)))));
            }
            case GREATER_THAN -> buildRangeQuery(fieldName, "gt", resolveOperandValue(right, participantAttributes));
            case GREATER_THAN_OR_EQUAL -> buildRangeQuery(fieldName, "gte", resolveOperandValue(right, participantAttributes));
            case LESS_THAN -> buildRangeQuery(fieldName, "lt", resolveOperandValue(right, participantAttributes));
            case LESS_THAN_OR_EQUAL -> buildRangeQuery(fieldName, "lte", resolveOperandValue(right, participantAttributes));
            case IN -> {
                ArrayValue arr = (ArrayValue) right;
                List<FieldValue> values = arr.values().stream()
                                             .map(lit -> toFieldValue(resolveLiteralValue(lit)))
                                             .toList();
                yield Query.of(q -> q.terms(t -> t.field(fieldName).terms(tv -> tv.value(values))));
            }
            case EXISTS -> Query.of(q -> q.exists(e -> e.field(fieldName)));
            case LIKE -> {
                String pattern = ((LiteralValue) right).asString();
                yield Query.of(q -> q.wildcard(w -> w.field(fieldName).value(pattern)));
            }
        };
    }

    private static Query buildRangeQuery(String field, String op, Object value) {
        if (value instanceof Number num) {
            double numericValue = num.doubleValue();
            return Query.of(q -> q.range(r -> r.number(n -> {
                n.field(field);
                switch (op) {
                    case "lt"  -> n.lt(numericValue);
                    case "gt"  -> n.gt(numericValue);
                    case "lte" -> n.lte(numericValue);
                    case "gte" -> n.gte(numericValue);
                    default    -> throw new IllegalArgumentException("Unknown range operator: " + op);
                }
                return n;
            })));
        } else {
            String stringValue = value.toString();
            return Query.of(q -> q.range(r -> r.term(t -> {
                t.field(field);
                switch (op) {
                    case "lt"  -> t.lt(stringValue);
                    case "gt"  -> t.gt(stringValue);
                    case "lte" -> t.lte(stringValue);
                    case "gte" -> t.gte(stringValue);
                    default    -> throw new IllegalArgumentException("Unknown range operator: " + op);
                }
                return t;
            })));
        }
    }

    /**
     * Resolves an attribute path to an Elasticsearch document field name.
     * For resource/entity paths, strips the root and returns the field path.
     */
    private static String resolveResourceField(AttributePath path) {
        if ("participant".equals(path.root()) || "context".equals(path.root())) {
            throw new PolicyParseException(
                    "Cannot use '" + path.root() + "' path as a document field on the left-hand side. "
                    + "Path: " + path.toPathString());
        }
        return path.fieldPath();
    }

    /**
     * Resolves an operand to a concrete value.
     * Participant attribute paths are looked up in the provided attributes map.
     * Literals are returned directly.
     */
    private static Object resolveOperandValue(Operand operand, Map<String, Object> participantAttributes) {
        return switch (operand) {
            case LiteralValue lit -> resolveLiteralValue(lit);
            case AttributePath path -> {
                if ("participant".equals(path.root())) {
                    String key = path.fieldPath();
                    Object value = participantAttributes.get(key);
                    if (value == null) {
                        throw new PolicyParseException(
                                "Participant attribute '" + key + "' not found in participant attributes");
                    }
                    yield value;
                } else if ("context".equals(path.root())) {
                    throw new PolicyParseException("Context attribute resolution not yet supported: " + path.toPathString());
                } else {
                    throw new PolicyParseException(
                            "Cannot resolve resource path '" + path.toPathString() + "' as a value on the right-hand side. "
                            + "Cross-field comparisons are not supported in Elasticsearch query compilation.");
                }
            }
            case ArrayValue ignored ->
                    throw new PolicyParseException("Array values should be handled by the IN operator directly");
            case null -> throw new PolicyParseException("Missing right-hand operand");
        };
    }

    private static Object resolveLiteralValue(LiteralValue lit) {
        return lit.value();
    }

    private static FieldValue toFieldValue(Object value) {
        return switch (value) {
            case String s -> FieldValue.of(s);
            case Long l -> FieldValue.of(l);
            case Integer i -> FieldValue.of((long) i);
            case Double d -> FieldValue.of(d);
            case Boolean b -> FieldValue.of(b);
            default -> FieldValue.of(value.toString());
        };
    }
}
