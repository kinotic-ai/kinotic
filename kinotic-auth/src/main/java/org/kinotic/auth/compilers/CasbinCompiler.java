package org.kinotic.auth.compilers;

import org.kinotic.auth.api.expressions.*;
import org.kinotic.auth.parsers.PolicyParseException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Compiles a {@link PolicyExpression} AST into an AviatorScript condition string for the jCasbin
 * engine — the body evaluated by {@code eval(p.cond)} in the ABAC matcher.
 * <p>
 * Path mapping:
 * <ul>
 *     <li>{@code participant.*} → {@code r.sub.*} (request subject: participant attributes)</li>
 *     <li>All other roots → {@code r.obj.<root>.*} (request object: the named method argument)</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * Input:  participant.roles contains 'finance' and order.amount < 50000
 * Output: (r.sub.roles != nil && include(r.sub.roles, "finance")) && (r.obj.order.amount != nil && r.obj.order.amount < 50000)
 * </pre>
 * Every path operand is guarded against {@code nil}: AviatorScript orders {@code nil} below every
 * value and treats it as unequal to everything, so an absent attribute would otherwise satisfy any
 * upper bound or inequality.
 */
public class CasbinCompiler {

    /**
     * Compiles a policy expression AST into an AviatorScript condition string.
     *
     * @param expression the policy expression AST
     * @return the AviatorScript condition evaluated by {@code eval(p.cond)}
     * @throws PolicyParseException if the expression contains unsupported patterns
     */
    public static String compile(PolicyExpression expression) {
        return compileExpression(expression);
    }

    private static String compileExpression(PolicyExpression expression) {
        return switch (expression) {
            case AndExpression and ->
                    compileExpression(and.left()) + " && " + compileExpression(and.right());
            case OrExpression or ->
                    "(" + compileExpression(or.left()) + " || " + compileExpression(or.right()) + ")";
            case NotExpression not ->
                    "!(" + compileExpression(not.expression()) + ")";
            case ComparisonExpression comp ->
                    compileComparison(comp);
        };
    }

    private static String compileComparison(ComparisonExpression comp) {
        String left = compilePath(comp.left());

        String condition = switch (comp.operator()) {
            case EQUALS -> left + " == " + compileOperand(comp.right());
            case NOT_EQUALS -> left + " != " + compileOperand(comp.right());
            case GREATER_THAN -> left + " > " + compileOperand(comp.right());
            case LESS_THAN -> left + " < " + compileOperand(comp.right());
            case GREATER_THAN_OR_EQUAL -> left + " >= " + compileOperand(comp.right());
            case LESS_THAN_OR_EQUAL -> left + " <= " + compileOperand(comp.right());
            // AviatorScript built-in: include(sequence, element)
            case CONTAINS -> "include(" + left + ", " + compileOperand(comp.right()) + ")";
            case IN -> compileIn(left, (ArrayValue) comp.right());
            // A missing map key resolves to nil, so presence is a non-nil check.
            case EXISTS -> left + " != nil";
            // Custom like(value, glob) function ('*' matches any sequence); see LikeFunction.
            case LIKE -> "like(" + left + ", " + compileLiteral((LiteralValue) comp.right()) + ")";
        };

        String ret;
        if (comp.operator() == ComparisonOperator.EXISTS) {
            ret = condition;
        } else {
            ret = guardedAgainstNil(condition, left, comp.right());
        }
        return ret;
    }

    // AviatorScript orders nil below every value and treats it as unequal to everything, so without
    // this guard an absent attribute satisfies any upper bound or inequality and the policy fails open.
    private static String guardedAgainstNil(String condition, String left, Operand right) {
        StringBuilder ret = new StringBuilder("(").append(left).append(" != nil && ");
        if (right instanceof AttributePath path) {
            ret.append(compilePath(path)).append(" != nil && ");
        }
        return ret.append(condition).append(")").toString();
    }

    private static String compileIn(String left, ArrayValue array) {
        if (array.values().isEmpty()) {
            return "false";
        }
        return "(" + array.values().stream()
                .map(value -> left + " == " + compileLiteral(value))
                .collect(Collectors.joining(" || ")) + ")";
    }

    /**
     * Maps an attribute path to an AviatorScript path. {@code participant.*} → {@code r.sub.*};
     * every other root is nested under {@code r.obj} by its own name, matching the named-argument
     * object the engine evaluates against.
     */
    private static String compilePath(AttributePath path) {
        return switch (path.root()) {
            case "participant" -> "r.sub" + fieldSuffix(path.fields());
            case "context" -> throw new PolicyParseException(
                    "context attributes are not supported by the jCasbin engine");
            default -> "r.obj." + path.root() + fieldSuffix(path.fields());
        };
    }

    private static String fieldSuffix(List<String> fields) {
        return fields.isEmpty() ? "" : "." + String.join(".", fields);
    }

    private static String compileOperand(Operand operand) {
        return switch (operand) {
            case LiteralValue lit -> compileLiteral(lit);
            case AttributePath path -> compilePath(path);
            case ArrayValue ignored ->
                    throw new PolicyParseException("Array values should be handled by the IN operator directly");
        };
    }

    private static String compileLiteral(LiteralValue lit) {
        return switch (lit.type()) {
            case STRING -> "\"" + lit.asString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            case INTEGER -> String.valueOf(lit.asLong());
            case DECIMAL -> String.valueOf(lit.asDouble());
            case BOOLEAN -> String.valueOf(lit.asBoolean());
        };
    }
}
