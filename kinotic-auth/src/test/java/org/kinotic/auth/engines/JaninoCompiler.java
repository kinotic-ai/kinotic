package org.kinotic.auth.engines;

import org.kinotic.auth.api.expressions.*;
import org.kinotic.auth.parsers.PolicyParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Compiles a {@link PolicyExpression} AST into a Java boolean expression over {@code Map r} holding
 * {@code sub} and {@code obj}, delegating every operator to {@link PolicyFunctions} so a missing
 * attribute or type mismatch denies rather than throws.
 */
final class JaninoCompiler {

    private static final String F = PolicyFunctions.class.getName();

    private JaninoCompiler() {}

    static String compile(PolicyExpression expression) {
        return expr(expression);
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
            case IN -> call("in", left, "new Object[] {" + ((ArrayValue) comp.right()).values().stream()
                    .map(JaninoCompiler::literal).collect(Collectors.joining(", ")) + "}");
            case EXISTS -> call("exists", left);
            case LIKE -> call("like", left, literal((LiteralValue) comp.right()));
        };
    }

    private static String call(String function, String... args) {
        return F + "." + function + "(" + String.join(", ", args) + ")";
    }

    private static String path(AttributePath path) {
        List<String> segments = new ArrayList<>();
        switch (path.root()) {
            case "participant" -> segments.add("sub");
            case "context" -> throw new PolicyParseException("context attributes are not supported by the Janino engine");
            default -> {
                segments.add("obj");
                segments.add(path.root());
            }
        }
        segments.addAll(path.fields());
        return F + ".get(r, new String[] {" + segments.stream().map(JaninoCompiler::javaString).collect(Collectors.joining(", ")) + "})";
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
            case STRING -> javaString(lit.asString());
            case INTEGER -> "Long.valueOf(" + lit.asLong() + "L)";
            case DECIMAL -> "Double.valueOf(" + lit.asDouble() + ")";
            case BOOLEAN -> "Boolean." + (lit.asBoolean() ? "TRUE" : "FALSE");
        };
    }

    private static String javaString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
