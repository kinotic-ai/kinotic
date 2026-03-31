package org.kinotic.auth.compilers;

import org.kinotic.auth.api.expressions.*;
import org.kinotic.auth.parsers.PolicyParseException;

/**
 * Compiles a {@link PolicyExpression} AST into a Cedar policy condition string.
 * <p>
 * The output is the {@code when} clause body of a Cedar policy. The calling code
 * wraps the result in a full Cedar policy:
 * <pre>
 * permit(
 *     principal,
 *     action == Action::"methodName",
 *     resource is ServiceMethod
 * ) when {
 *     [compiled condition here]
 * };
 * </pre>
 * <p>
 * Path mapping:
 * <ul>
 *     <li>{@code participant.*} → {@code principal.*} (Cedar principal entity attributes)</li>
 *     <li>All other roots → {@code resource.*} (Cedar resource entity attributes, mapped from method parameter names)</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * Input:  participant.roles contains 'finance' and order.amount < 50000
 * Output: principal.roles.contains("finance") && resource.order.amount < 50000
 * </pre>
 */
public class CedarCompiler {

    /**
     * Compiles a policy expression AST into a Cedar condition string.
     *
     * @param expression the policy expression AST
     * @return the Cedar condition string (the body of a {@code when} clause)
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
                    "!" + compileExpression(not.expression());
            case ComparisonExpression comp ->
                    compileComparison(comp);
        };
    }

    private static String compileComparison(ComparisonExpression comp) {
        String left = compilePath(comp.left());

        return switch (comp.operator()) {
            case EQUALS -> left + " == " + compileOperand(comp.right());
            case NOT_EQUALS -> left + " != " + compileOperand(comp.right());
            case GREATER_THAN -> left + " > " + compileOperand(comp.right());
            case LESS_THAN -> left + " < " + compileOperand(comp.right());
            case GREATER_THAN_OR_EQUAL -> left + " >= " + compileOperand(comp.right());
            case LESS_THAN_OR_EQUAL -> left + " <= " + compileOperand(comp.right());
            case CONTAINS -> {
                // Cedar: collection.contains(value)
                String value = compileOperand(comp.right());
                yield left + ".contains(" + value + ")";
            }
            case IN -> {
                // Cedar: [val1, val2].contains(resource.field)
                ArrayValue arr = (ArrayValue) comp.right();
                String set = "[" + String.join(", ", arr.values().stream()
                        .map(CedarCompiler::compileLiteral)
                        .toList()) + "]";
                yield set + ".contains(" + left + ")";
            }
            case EXISTS -> {
                // Cedar: resource.field has field — use has() on parent
                String parentPath = compilePath(new AttributePath(comp.left().root(),
                        comp.left().fields().subList(0, comp.left().fields().size() - 1)));
                String fieldName = comp.left().fields().getLast();
                yield parentPath + " has " + fieldName;
            }
            case LIKE -> {
                // Cedar: resource.field like "pattern"
                String pattern = ((LiteralValue) comp.right()).asString();
                // Cedar uses * for wildcards, same as our grammar
                yield left + " like \"" + pattern + "\"";
            }
        };
    }

    /**
     * Maps an attribute path to a Cedar path.
     * {@code participant.*} → {@code principal.*}
     * Everything else → {@code resource.*}
     */
    private static String compilePath(AttributePath path) {
        String cedarRoot = switch (path.root()) {
            case "participant" -> "principal";
            default -> "resource";
        };
        String fieldPath = path.fieldPath();
        if (fieldPath.isEmpty()) {
            return cedarRoot;
        }
        return cedarRoot + "." + fieldPath;
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
            case STRING -> "\"" + lit.asString().replace("\"", "\\\"") + "\"";
            case INTEGER -> String.valueOf(lit.asLong());
            case DECIMAL -> String.valueOf(lit.asDouble());
            case BOOLEAN -> String.valueOf(lit.asBoolean());
        };
    }
}
