package org.kinotic.auth.engines;

import org.kinotic.auth.api.expressions.*;
import org.kinotic.auth.parsers.PolicyParseException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Compiles a {@link PolicyExpression} AST into a Jakarta EL expression over the bean {@code r}, a
 * map holding {@code sub} and {@code obj}. Every operator goes through a {@code k:} function bound
 * to {@link PolicyFunctions}, because EL's own comparison operators coerce null to zero, which would
 * let a missing attribute satisfy a limit.
 */
final class ElCompiler {

    /**
     * EL function name for each {@link PolicyFunctions} operator. The Java names {@code eq}, {@code ne},
     * {@code lt}, {@code gt}, {@code le} and {@code ge} are EL's reserved operator words, so they cannot
     * be function names.
     */
    static final Map<String, String> EL_NAMES = Map.of(
            "eq", "isEqual", "ne", "isNotEqual", "lt", "isLess", "gt", "isGreater",
            "le", "isAtMost", "ge", "isAtLeast", "contains", "contains", "exists", "exists", "like", "like");

    private ElCompiler() {}

    static String compile(PolicyExpression expression) {
        return "${" + expr(expression) + "}";
    }

    private static String expr(PolicyExpression expression) {
        return switch (expression) {
            case AndExpression and -> "(" + expr(and.left()) + " && " + expr(and.right()) + ")";
            case OrExpression or -> "(" + expr(or.left()) + " || " + expr(or.right()) + ")";
            case NotExpression not -> "!" + expr(not.expression());
            case ComparisonExpression comp -> comparison(comp);
        };
    }

    private static String comparison(ComparisonExpression comp) {
        String left = path(comp.left());
        return switch (comp.operator()) {
            case EQUALS -> call("eq", left, operand(comp.right()));
            case NOT_EQUALS -> call("ne", left, operand(comp.right()));
            case GREATER_THAN -> call("gt", left, operand(comp.right()));
            case LESS_THAN -> call("lt", left, operand(comp.right()));
            case GREATER_THAN_OR_EQUAL -> call("ge", left, operand(comp.right()));
            case LESS_THAN_OR_EQUAL -> call("le", left, operand(comp.right()));
            case CONTAINS -> call("contains", left, operand(comp.right()));
            case IN -> "(" + ((ArrayValue) comp.right()).values().stream()
                    .map(value -> call("eq", left, literal(value))).collect(Collectors.joining(" || ")) + ")";
            case EXISTS -> call("exists", left);
            case LIKE -> call("like", left, literal((LiteralValue) comp.right()));
        };
    }

    private static String call(String function, String... args) {
        return "k:" + EL_NAMES.get(function) + "(" + String.join(", ", args) + ")";
    }

    private static String path(AttributePath path) {
        return switch (path.root()) {
            case "participant" -> "r.sub" + fieldSuffix(path.fields());
            case "context" -> throw new PolicyParseException("context attributes are not supported by the EL engine");
            default -> "r.obj." + path.root() + fieldSuffix(path.fields());
        };
    }

    private static String fieldSuffix(List<String> fields) {
        return fields.isEmpty() ? "" : "." + String.join(".", fields);
    }

    private static String operand(Operand operand) {
        return switch (operand) {
            case LiteralValue lit -> literal(lit);
            case AttributePath p -> path(p);
            case ArrayValue ignored -> throw new PolicyParseException("Array values should be handled by the IN operator directly");
        };
    }

    private static String literal(LiteralValue lit) {
        return switch (lit.type()) {
            case STRING -> "'" + lit.asString().replace("\\", "\\\\").replace("'", "\\'") + "'";
            case INTEGER -> String.valueOf(lit.asLong());
            case DECIMAL -> String.valueOf(lit.asDouble());
            case BOOLEAN -> String.valueOf(lit.asBoolean());
        };
    }
}
