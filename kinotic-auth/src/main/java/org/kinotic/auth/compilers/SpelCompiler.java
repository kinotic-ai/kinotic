package org.kinotic.auth.compilers;

import org.kinotic.auth.api.expressions.*;
import org.kinotic.auth.parsers.PolicyParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Compiles a {@link PolicyExpression} AST into a SpEL expression over a root map holding {@code sub}
 * (participant attributes) and {@code obj} (the method's named arguments).
 * <p>
 * The output uses only property navigation, indexing, operators, literals and the two registered
 * functions {@code #contains} and {@code #like} — never method invocation, type references,
 * constructors or bean references — so it evaluates on a sandboxed context that forbids all of those.
 * <p>
 * Path mapping:
 * <ul>
 *     <li>{@code participant.*} → {@code sub.*}</li>
 *     <li>All other roots → {@code obj.<root>.*}</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * Input:  participant.roles contains 'finance' and order.amount < 50000
 * Output: ((sub.roles != null && #contains(sub.roles, 'finance')) && (obj.order.amount != null && obj.order.amount < 50000L))
 * </pre>
 * Every path operand is guarded against null: SpEL orders null below every value, so an absent
 * attribute would otherwise satisfy any upper bound or inequality.
 */
public class SpelCompiler {

    /**
     * Compiles a policy expression AST into a SpEL expression.
     *
     * @param expression the policy expression AST
     * @return the SpEL expression
     * @throws PolicyParseException if the expression contains unsupported patterns
     */
    public static String compile(PolicyExpression expression) {
        return compileExpression(expression);
    }

    private static String compileExpression(PolicyExpression expression) {
        return switch (expression) {
            case AndExpression and ->
                    "(" + compileExpression(and.left()) + " && " + compileExpression(and.right()) + ")";
            case OrExpression or ->
                    "(" + compileExpression(or.left()) + " || " + compileExpression(or.right()) + ")";
            case NotExpression not ->
                    "!(" + compileExpression(not.expression()) + ")";
            case ComparisonExpression comp ->
                    compileComparison(comp);
        };
    }

    private static String compileComparison(ComparisonExpression comp) {
        List<String> segments = segments(comp.left());
        String left = String.join(".", segments);

        String condition = switch (comp.operator()) {
            case EQUALS -> left + " == " + compileOperand(comp.right());
            case NOT_EQUALS -> left + " != " + compileOperand(comp.right());
            case GREATER_THAN -> left + " > " + compileOperand(comp.right());
            case LESS_THAN -> left + " < " + compileOperand(comp.right());
            case GREATER_THAN_OR_EQUAL -> left + " >= " + compileOperand(comp.right());
            case LESS_THAN_OR_EQUAL -> left + " <= " + compileOperand(comp.right());
            case CONTAINS -> "#contains(" + left + ", " + compileOperand(comp.right()) + ")";
            case IN -> "(" + ((ArrayValue) comp.right()).values().stream()
                    .map(value -> left + " == " + compileLiteral(value))
                    .collect(Collectors.joining(" || ")) + ")";
            // Indexing the leaf reads an absent key as null instead of failing the property lookup.
            case EXISTS -> String.join(".", segments.subList(0, segments.size() - 1))
                    + "['" + segments.get(segments.size() - 1) + "'] != null";
            case LIKE -> "#like(" + left + ", " + compileLiteral((LiteralValue) comp.right()) + ")";
        };

        String ret;
        if (comp.operator() == ComparisonOperator.EXISTS) {
            ret = condition;
        } else {
            ret = guardedAgainstNull(condition, left, comp.right());
        }
        return ret;
    }

    // SpEL orders null below every value, so without this guard an absent attribute satisfies any
    // upper bound or inequality and the policy fails open.
    private static String guardedAgainstNull(String condition, String left, Operand right) {
        StringBuilder ret = new StringBuilder("(").append(left).append(" != null && ");
        if (right instanceof AttributePath path) {
            ret.append(String.join(".", segments(path))).append(" != null && ");
        }
        return ret.append(condition).append(")").toString();
    }

    /**
     * Maps an attribute path to its SpEL segments. {@code participant.*} → {@code sub.*}; every other
     * root is nested under {@code obj} by its own name, matching the named-argument map the engine
     * evaluates against.
     */
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

    private static String compileOperand(Operand operand) {
        return switch (operand) {
            case LiteralValue lit -> compileLiteral(lit);
            case AttributePath path -> String.join(".", segments(path));
            case ArrayValue ignored ->
                    throw new PolicyParseException("Array values should be handled by the IN operator directly");
        };
    }

    private static String compileLiteral(LiteralValue lit) {
        return switch (lit.type()) {
            // A quote inside a SpEL string literal is written as two quotes; SpEL has no other escapes.
            case STRING -> "'" + lit.asString().replace("'", "''") + "'";
            case INTEGER -> lit.asLong() + "L";
            case DECIMAL -> String.valueOf(lit.asDouble());
            case BOOLEAN -> String.valueOf(lit.asBoolean());
        };
    }
}
