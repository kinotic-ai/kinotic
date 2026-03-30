package org.kinotic.auth.compilers;

import org.kinotic.auth.api.expressions.*;

import java.util.List;
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
            case NotExpression not -> "!(" + compileExpression(not.expression()) + ")";
            case ComparisonExpression comp -> compileComparison(comp);
        };
    }

    private static String compileComparison(ComparisonExpression comp) {
        // EXISTS requires the original AttributePath to build the correct Cedar 'has' expression
        if (comp.operator() == ComparisonOperator.EXISTS) {
            return compileCedarExists(comp.left());
        }

        String left = compilePath(comp.left());

        return switch (comp.operator()) {
            case EQUALS -> left + " == " + compileOperand(comp.right());
            case NOT_EQUALS -> left + " != " + compileOperand(comp.right());
            case GREATER_THAN -> left + " > " + compileOperand(comp.right());
            case LESS_THAN -> left + " < " + compileOperand(comp.right());
            case GREATER_THAN_OR_EQUAL -> left + " >= " + compileOperand(comp.right());
            case LESS_THAN_OR_EQUAL -> left + " <= " + compileOperand(comp.right());
            // Cedar set membership: [values].contains(field) — the array is on the left calling .contains()
            case IN -> "[" + compileArrayValues((ArrayValue) comp.right()) + "].contains(" + left + ")";
            case CONTAINS -> left + ".contains(" + compileOperand(comp.right()) + ")";
            case EXISTS -> throw new IllegalStateException("EXISTS handled above");
            case LIKE -> left + " like " + compileOperand(comp.right());
        };
    }

    /**
     * Compiles an {@code exists} check to Cedar's {@code has} operator.
     * Cedar syntax: {@code entity has "attribute"} — e.g., {@code resource has "approvedBy"}.
     * For nested paths like {@code entity.address.city}, this produces {@code resource.address has "city"}.
     */
    private static String compileCedarExists(AttributePath path) {
        List<String> fields = path.fields();
        if (fields.isEmpty()) {
            throw new IllegalArgumentException(
                    "'exists' requires a field path (e.g., '" + path.root() + ".fieldName'), "
                    + "got bare identifier '" + path.root() + "'");
        }
        String lastField = fields.getLast();
        String parentCedar;
        if (fields.size() == 1) {
            parentCedar = switch (path.root()) {
                case "participant" -> "principal";
                case "context" -> "context";
                default -> "resource";
            };
        } else {
            parentCedar = compilePath(new AttributePath(path.root(), fields.subList(0, fields.size() - 1)));
        }
        return parentCedar + " has \"" + lastField + "\"";
    }

    private static String compilePath(AttributePath path) {
        String root = path.root();
        // participant maps to Cedar's principal; context maps directly; everything else becomes resource
        return switch (root) {
            case "participant" -> path.fields().isEmpty() ? "principal" : "principal." + path.fieldPath();
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
