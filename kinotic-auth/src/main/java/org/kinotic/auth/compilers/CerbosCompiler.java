package org.kinotic.auth.compilers;

import org.kinotic.auth.api.expressions.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Compiles a {@link PolicyExpression} AST into a Cerbos CEL condition string.
 * <p>
 * The output is a CEL expression suitable for use in a Cerbos resource policy condition:
 * <pre>
 * condition:
 *   match:
 *     expr: "P.attr.department == R.attr.department"
 * </pre>
 * <p>
 * Attribute paths are mapped as follows:
 * <ul>
 *   <li>{@code participant.*} maps to Cerbos {@code P.attr.*}</li>
 *   <li>{@code context.*} maps to Cerbos {@code request.auxData.jwt.*}</li>
 *   <li>Any other root (e.g., {@code entity.department}) maps to {@code R.attr.*}
 *       for entity definitions where the resource is the document</li>
 * </ul>
 * <p>
 * For service method arguments, a separate compilation mode maps parameter names
 * to positional array access: {@code order.amount} becomes {@code R.attr.args[0].amount}
 * after parameter name resolution.
 */
public class CerbosCompiler {

    /**
     * Compiles a policy expression AST into a CEL condition string for Cerbos.
     *
     * @param expression the policy expression AST
     * @return the CEL condition expression
     */
    public static String compile(PolicyExpression expression) {
        return compileExpression(expression);
    }

    private static String compileExpression(PolicyExpression expression) {
        return switch (expression) {
            case AndExpression and -> "(" + compileExpression(and.left()) + " && " + compileExpression(and.right()) + ")";
            case OrExpression or -> "(" + compileExpression(or.left()) + " || " + compileExpression(or.right()) + ")";
            case NotExpression not -> "!(" + compileExpression(not.expression()) + ")";
            case ComparisonExpression comp -> compileComparison(comp);
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
            case IN -> left + " in " + compileOperand(comp.right());
            case CONTAINS -> compileOperand(comp.right()) + " in " + left;
            case EXISTS -> "has(R.attr." + comp.left().fieldPath() + ")";
            case LIKE -> left + ".matches(" + compileOperand(comp.right()) + ")";
        };
    }

    private static String compilePath(AttributePath path) {
        String root = path.root();
        return switch (root) {
            case "participant" -> {
                if (path.fields().isEmpty()) {
                    yield "P.attr";
                } else {
                    yield "P.attr." + path.fieldPath();
                }
            }
            case "context" -> {
                if (path.fields().isEmpty()) {
                    yield "request.auxData.jwt";
                } else {
                    yield "request.auxData.jwt." + path.fieldPath();
                }
            }
            default -> {
                if (path.fields().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Path '" + root + "' must include at least one field (e.g., '"
                            + root + ".fieldName')");
                }
                yield "R.attr." + path.fieldPath();
            }
        };
    }

    private static String compileOperand(Operand operand) {
        return switch (operand) {
            case AttributePath path -> compilePath(path);
            case LiteralValue lit -> compileLiteral(lit);
            case ArrayValue arr -> "[" + compileArrayValues(arr) + "]";
            case null -> "true";
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

    private static String compileArrayValues(ArrayValue arr) {
        return arr.values().stream()
                  .map(CerbosCompiler::compileLiteral)
                  .collect(Collectors.joining(", "));
    }
}
