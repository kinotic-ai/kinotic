package org.kinotic.auth.engines;

import org.kinotic.auth.api.expressions.*;
import org.kinotic.auth.compilers.GlobPattern;
import org.kinotic.auth.parsers.PolicyParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Compiles a {@link PolicyExpression} AST into a SpEL expression whose root object is a map with
 * {@code sub} (participant attributes) and {@code obj} (named arguments). Paths use property
 * navigation, which fails on an absent key so a missing attribute denies; {@code exists} indexes
 * the leaf instead, which reads an absent key as null.
 */
final class SpelCompiler {

    private SpelCompiler() {}

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
        List<String> segments = segments(comp.left());
        String left = String.join(".", segments);
        String condition = switch (comp.operator()) {
            case EQUALS -> left + " == " + operand(comp.right());
            case NOT_EQUALS -> left + " != " + operand(comp.right());
            case GREATER_THAN -> left + " > " + operand(comp.right());
            case LESS_THAN -> left + " < " + operand(comp.right());
            case GREATER_THAN_OR_EQUAL -> left + " >= " + operand(comp.right());
            case LESS_THAN_OR_EQUAL -> left + " <= " + operand(comp.right());
            case CONTAINS -> left + ".contains(" + operand(comp.right()) + ")";
            case IN -> "{" + ((ArrayValue) comp.right()).values().stream()
                    .map(SpelCompiler::literal).collect(Collectors.joining(", ")) + "}.contains(" + left + ")";
            case EXISTS -> String.join(".", segments.subList(0, segments.size() - 1))
                    + "['" + segments.get(segments.size() - 1) + "'] != null";
            case LIKE -> left + " matches " + spelString(GlobPattern.toRegex(((LiteralValue) comp.right()).asString()));
        };
        String ret;
        if (comp.operator() == ComparisonOperator.EXISTS) {
            ret = condition;
        } else {
            ret = guardedAgainstNull(condition, left, comp.right());
        }
        return ret;
    }

    // SpEL's type comparator orders null below every value, so an absent attribute would satisfy any
    // upper bound; requiring each path operand to be present first makes it deny instead.
    private static String guardedAgainstNull(String condition, String left, Operand right) {
        StringBuilder ret = new StringBuilder("(").append(left).append(" != null && ");
        if (right instanceof AttributePath path) {
            ret.append(String.join(".", segments(path))).append(" != null && ");
        }
        return ret.append(condition).append(")").toString();
    }

    private static List<String> segments(AttributePath path) {
        List<String> ret = new ArrayList<>();
        switch (path.root()) {
            case "participant" -> ret.add("sub");
            case "context" -> throw new PolicyParseException("context attributes are not supported by the SpEL engine");
            default -> {
                ret.add("obj");
                ret.add(path.root());
            }
        }
        ret.addAll(path.fields());
        return ret;
    }

    private static String operand(Operand operand) {
        return switch (operand) {
            case LiteralValue lit -> literal(lit);
            case AttributePath p -> String.join(".", segments(p));
            case ArrayValue ignored -> throw new PolicyParseException("Array values should be handled by the IN operator directly");
        };
    }

    private static String literal(LiteralValue lit) {
        return switch (lit.type()) {
            case STRING -> spelString(lit.asString());
            case INTEGER -> lit.asLong() + "L";
            case DECIMAL -> String.valueOf(lit.asDouble());
            case BOOLEAN -> String.valueOf(lit.asBoolean());
        };
    }

    private static String spelString(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
