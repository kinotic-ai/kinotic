package org.kinotic.auth.compilers;

import org.kinotic.auth.api.expressions.*;
import org.kinotic.auth.parsers.PolicyParseException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Compiles a {@link PolicyExpression} AST into a CEL (Common Expression Language) boolean expression
 * over one variable, {@code r}: a map holding the participant attributes under {@code sub} and the
 * method's named arguments under {@code obj}.
 * <p>
 * Path mapping mirrors {@link CasbinCompiler}:
 * <ul>
 *     <li>{@code participant.*} → {@code r.sub.*}</li>
 *     <li>All other roots → {@code r.obj.<root>.*}</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * Input:  participant.roles contains 'finance' and order.amount < 50000
 * Output: "finance" in r.sub.roles && r.obj.order.amount < 50000
 * </pre>
 */
public class CelCompiler {

    /**
     * Compiles a policy expression AST into a CEL boolean expression.
     *
     * @param expression the policy expression AST
     * @return the CEL expression
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

        return switch (comp.operator()) {
            case EQUALS -> left + " == " + compileOperand(comp.right());
            case NOT_EQUALS -> left + " != " + compileOperand(comp.right());
            case GREATER_THAN -> left + " > " + compileOperand(comp.right());
            case LESS_THAN -> left + " < " + compileOperand(comp.right());
            case GREATER_THAN_OR_EQUAL -> left + " >= " + compileOperand(comp.right());
            case LESS_THAN_OR_EQUAL -> left + " <= " + compileOperand(comp.right());
            // CEL list membership: element in list
            case CONTAINS -> compileOperand(comp.right()) + " in " + left;
            case IN -> left + " in [" + ((ArrayValue) comp.right()).values().stream()
                    .map(CelCompiler::compileLiteral)
                    .collect(Collectors.joining(", ")) + "]";
            // has() tests key presence on a map without evaluating the value.
            case EXISTS -> "has(" + left + ")";
            // RE2 regex match; the glob is anchored so '*' spans the whole value.
            case LIKE -> left + ".matches(" + celString(globToRegex(((LiteralValue) comp.right()).asString())) + ")";
        };
    }

    private static String compilePath(AttributePath path) {
        return switch (path.root()) {
            case "participant" -> "r.sub" + fieldSuffix(path.fields());
            case "context" -> throw new PolicyParseException(
                    "context attributes are not supported by the CEL engine");
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
            case STRING -> celString(lit.asString());
            case INTEGER -> String.valueOf(lit.asLong());
            case DECIMAL -> String.valueOf(lit.asDouble());
            case BOOLEAN -> String.valueOf(lit.asBoolean());
        };
    }

    private static String celString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if ("\\.[]{}()+-^$|?".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return regex.append("$").toString();
    }
}
