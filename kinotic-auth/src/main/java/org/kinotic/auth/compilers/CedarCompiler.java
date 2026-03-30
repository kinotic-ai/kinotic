package org.kinotic.auth.compilers;

import org.kinotic.auth.api.expressions.*;

import java.util.stream.Collectors;

/**
 * Compiles a {@link PolicyExpression} AST into a Cedar policy condition string.
 * <p>
 * The output is a Cedar {@code when} clause body, suitable for embedding inside
 * a {@code permit(...) when { ... };} block.
 * <p>
 * Attribute paths are mapped as follows:
 * <ul>
 *   <li>{@code participant.*} maps to Cedar {@code principal.*}</li>
 *   <li>{@code context.*} maps to Cedar {@code context.*}</li>
 *   <li>Any other root (e.g., {@code entity.department}) maps to {@code resource.*}
 *       after stripping the application-level root identifier</li>
 * </ul>
 */
public class CedarCompiler {

    /**
     * Compiles a policy expression AST into a Cedar condition string.
     *
     * @param expression the policy expression AST
     * @return the Cedar condition expression (the body of a {@code when} clause)
     */
    public static String compile(PolicyExpression expression) {
        return compileExpression(expression);
    }

    private static String compileExpression(PolicyExpression expression) {
        return switch (expression) {
            case AndExpression and -> "(" + compileExpression(and.left()) + " && " + compileExpression(and.right()) + ")";
            case OrExpression or -> "(" + compileExpression(or.left()) + " || " + compileExpression(or.right()) + ")";
            case NotExpression not -> "!" + compileExpression(not.expression());
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
            case IN -> left + ".containsAny([" + compileArrayValues((ArrayValue) comp.right()) + "])";
            case CONTAINS -> left + ".contains(" + compileOperand(comp.right()) + ")";
            case EXISTS -> left + " has \"_\""; // Cedar uses 'has' for attribute existence
            case LIKE -> left + " like " + compileOperand(comp.right());
        };
    }

    private static String compilePath(AttributePath path) {
        String root = path.root();
        // participant maps to Cedar's principal; context maps directly; everything else becomes resource
        return switch (root) {
            case "participant" -> "principal." + path.fieldPath();
            case "context" -> path.toPathString();
            default -> {
                if (path.fields().isEmpty()) {
                    yield "resource";
                } else {
                    yield "resource." + path.fieldPath();
                }
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
                  .map(CedarCompiler::compileLiteral)
                  .collect(Collectors.joining(", "));
    }
}
