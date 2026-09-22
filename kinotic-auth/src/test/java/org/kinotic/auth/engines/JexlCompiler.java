package org.kinotic.auth.engines;

import org.kinotic.auth.api.expressions.*;
import org.kinotic.auth.compilers.GlobPattern;
import org.kinotic.auth.parsers.PolicyParseException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Compiles a {@link PolicyExpression} AST into a JEXL expression over the variable {@code r}, a map
 * holding {@code sub} (participant attributes) and {@code obj} (named arguments). JEXL's {@code =~}
 * operator covers both collection membership and regex matching.
 */
final class JexlCompiler {

    private JexlCompiler() {}

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
        String condition = switch (comp.operator()) {
            case EQUALS -> left + " == " + operand(comp.right());
            case NOT_EQUALS -> left + " != " + operand(comp.right());
            case GREATER_THAN -> left + " > " + operand(comp.right());
            case LESS_THAN -> left + " < " + operand(comp.right());
            case GREATER_THAN_OR_EQUAL -> left + " >= " + operand(comp.right());
            case LESS_THAN_OR_EQUAL -> left + " <= " + operand(comp.right());
            case CONTAINS -> operand(comp.right()) + " =~ " + left;
            case IN -> left + " =~ [" + ((ArrayValue) comp.right()).values().stream()
                    .map(JexlCompiler::literal).collect(Collectors.joining(", ")) + "]";
            case EXISTS -> left + " != null";
            case LIKE -> left + " =~ " + jexlString(GlobPattern.toRegex(((LiteralValue) comp.right()).asString()));
        };
        String ret;
        if (comp.operator() == ComparisonOperator.EXISTS) {
            ret = condition;
        } else {
            ret = guardedAgainstNull(condition, left, comp.right());
        }
        return ret;
    }

    // JEXL treats null as unequal to everything and equal to null, so an absent attribute would
    // satisfy != and a two-path ==; requiring each path operand to be present first makes it deny.
    private static String guardedAgainstNull(String condition, String left, Operand right) {
        StringBuilder ret = new StringBuilder("(").append(left).append(" != null && ");
        if (right instanceof AttributePath path) {
            ret.append(path(path)).append(" != null && ");
        }
        return ret.append(condition).append(")").toString();
    }

    private static String path(AttributePath path) {
        return switch (path.root()) {
            case "participant" -> "r.sub" + fieldSuffix(path.fields());
            case "context" -> throw new PolicyParseException("context attributes are not supported by the JEXL engine");
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
            case STRING -> jexlString(lit.asString());
            case INTEGER -> String.valueOf(lit.asLong());
            case DECIMAL -> String.valueOf(lit.asDouble());
            case BOOLEAN -> String.valueOf(lit.asBoolean());
        };
    }

    private static String jexlString(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
