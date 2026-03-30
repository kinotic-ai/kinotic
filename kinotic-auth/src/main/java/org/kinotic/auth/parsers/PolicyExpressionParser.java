package org.kinotic.auth.parsers;

import org.kinotic.auth.api.expressions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive descent parser for ABAC policy expressions.
 * Produces a {@link PolicyExpression} AST from a policy expression string.
 * <p>
 * The grammar specification lives in {@code src/main/antlr/AbacPolicy.g4}.
 * <p>
 * Operator precedence (highest to lowest):
 * <ol>
 *   <li>Comparisons (==, !=, {@literal <}, {@literal >}, {@literal <=}, {@literal >=}, in, contains, exists, like)</li>
 *   <li>NOT</li>
 *   <li>AND</li>
 *   <li>OR</li>
 * </ol>
 */
public class PolicyExpressionParser {

    private final List<PolicyToken> tokens;
    private int pos;

    private PolicyExpressionParser(List<PolicyToken> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    /**
     * Parses a policy expression string into an AST.
     *
     * @param expression the policy expression string
     * @return the parsed expression AST
     * @throws PolicyParseException if the expression is syntactically invalid
     */
    public static PolicyExpression parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new PolicyParseException("Policy expression must not be blank");
        }
        var lexer = new PolicyLexer(expression);
        var tokens = lexer.tokenize();
        var parser = new PolicyExpressionParser(tokens);
        PolicyExpression result = parser.parseOrExpression();
        parser.expect(PolicyTokenType.EOF, "Unexpected token after end of expression");
        return result;
    }

    // or_expr : and_expr ('or' and_expr)*
    private PolicyExpression parseOrExpression() {
        PolicyExpression left = parseAndExpression();
        while (check(PolicyTokenType.OR)) {
            advance();
            PolicyExpression right = parseAndExpression();
            left = new OrExpression(left, right);
        }
        return left;
    }

    // and_expr : not_expr ('and' not_expr)*
    private PolicyExpression parseAndExpression() {
        PolicyExpression left = parseNotExpression();
        while (check(PolicyTokenType.AND)) {
            advance();
            PolicyExpression right = parseNotExpression();
            left = new AndExpression(left, right);
        }
        return left;
    }

    // not_expr : 'not' not_expr | primary
    private PolicyExpression parseNotExpression() {
        if (check(PolicyTokenType.NOT)) {
            advance();
            PolicyExpression expr = parseNotExpression();
            return new NotExpression(expr);
        }
        return parsePrimary();
    }

    // primary : '(' or_expr ')' | comparison
    private PolicyExpression parsePrimary() {
        if (check(PolicyTokenType.LPAREN)) {
            advance();
            PolicyExpression expr = parseOrExpression();
            expect(PolicyTokenType.RPAREN, "Expected ')'");
            return expr;
        }
        return parseComparison();
    }

    // comparison : path (op (path | literal) | 'in' array | 'contains' literal | 'exists' | 'like' STRING)
    private PolicyExpression parseComparison() {
        AttributePath left = parsePath();

        // exists (unary)
        if (check(PolicyTokenType.EXISTS)) {
            advance();
            return new ComparisonExpression(left, ComparisonOperator.EXISTS, null);
        }

        // contains literal
        if (check(PolicyTokenType.CONTAINS)) {
            advance();
            LiteralValue literal = parseLiteral();
            return new ComparisonExpression(left, ComparisonOperator.CONTAINS, literal);
        }

        // in [array]
        if (check(PolicyTokenType.IN)) {
            advance();
            ArrayValue array = parseArray();
            return new ComparisonExpression(left, ComparisonOperator.IN, array);
        }

        // like 'pattern'
        if (check(PolicyTokenType.LIKE)) {
            advance();
            LiteralValue pattern = parseLiteral();
            if (pattern.type() != LiteralValue.LiteralType.STRING) {
                throw new PolicyParseException("Expected string literal after 'like' at position " + current().position());
            }
            return new ComparisonExpression(left, ComparisonOperator.LIKE, pattern);
        }

        // comparison operator
        ComparisonOperator op = parseComparisonOp();

        // Right-hand side: path or literal
        Operand right = tryParsePath();
        if (right == null) {
            right = parseLiteral();
        }
        return new ComparisonExpression(left, op, right);
    }

    private ComparisonOperator parseComparisonOp() {
        PolicyToken token = current();
        ComparisonOperator op = switch (token.type()) {
            case EQ -> ComparisonOperator.EQUALS;
            case NEQ -> ComparisonOperator.NOT_EQUALS;
            case GT -> ComparisonOperator.GREATER_THAN;
            case GTE -> ComparisonOperator.GREATER_THAN_OR_EQUAL;
            case LT -> ComparisonOperator.LESS_THAN;
            case LTE -> ComparisonOperator.LESS_THAN_OR_EQUAL;
            default -> throw new PolicyParseException(
                    "Expected comparison operator at position " + token.position() + ", got '" + token.text() + "'");
        };
        advance();
        return op;
    }

    private AttributePath parsePath() {
        PolicyToken rootToken = expect(PolicyTokenType.IDENTIFIER, "Expected identifier");
        String root = rootToken.text();
        var fields = new ArrayList<String>();
        while (check(PolicyTokenType.DOT)) {
            advance();
            PolicyToken fieldToken = expect(PolicyTokenType.IDENTIFIER, "Expected identifier after '.'");
            fields.add(fieldToken.text());
        }
        return new AttributePath(root, List.copyOf(fields));
    }

    /**
     * Tries to parse a path. Returns null if the current token is not an identifier
     * or if the identifier is followed by a keyword-like context (e.g. no dot follows).
     * This is used to distinguish between path and literal on the right-hand side.
     */
    private AttributePath tryParsePath() {
        if (!check(PolicyTokenType.IDENTIFIER)) {
            return null;
        }
        // Peek ahead: an identifier followed by a dot is definitely a path.
        // A bare identifier on the RHS is also a path (e.g., principal.role == resource.role)
        // We need at least one dot to distinguish from a keyword/identifier used as something else.
        // However, a bare identifier on the RHS makes sense as a single-segment path only
        // if it could be a root reference. We'll require at least one dot for RHS paths.
        if (pos + 1 < tokens.size() && tokens.get(pos + 1).type() == PolicyTokenType.DOT) {
            return parsePath();
        }
        return null;
    }

    private LiteralValue parseLiteral() {
        PolicyToken token = current();
        return switch (token.type()) {
            case STRING -> {
                advance();
                yield new LiteralValue(token.text(), LiteralValue.LiteralType.STRING);
            }
            case INTEGER -> {
                advance();
                yield new LiteralValue(Long.parseLong(token.text()), LiteralValue.LiteralType.INTEGER);
            }
            case DECIMAL -> {
                advance();
                yield new LiteralValue(Double.parseDouble(token.text()), LiteralValue.LiteralType.DECIMAL);
            }
            case BOOLEAN -> {
                advance();
                yield new LiteralValue(Boolean.parseBoolean(token.text()), LiteralValue.LiteralType.BOOLEAN);
            }
            default -> throw new PolicyParseException(
                    "Expected literal value at position " + token.position() + ", got '" + token.text() + "'");
        };
    }

    private ArrayValue parseArray() {
        expect(PolicyTokenType.LBRACKET, "Expected '['");
        var values = new ArrayList<LiteralValue>();
        values.add(parseLiteral());
        while (check(PolicyTokenType.COMMA)) {
            advance();
            values.add(parseLiteral());
        }
        expect(PolicyTokenType.RBRACKET, "Expected ']'");
        return new ArrayValue(List.copyOf(values));
    }

    private PolicyToken current() {
        return tokens.get(pos);
    }

    private boolean check(PolicyTokenType type) {
        return pos < tokens.size() && tokens.get(pos).type() == type;
    }

    private PolicyToken advance() {
        PolicyToken token = tokens.get(pos);
        pos++;
        return token;
    }

    private PolicyToken expect(PolicyTokenType type, String message) {
        if (!check(type)) {
            PolicyToken current = current();
            throw new PolicyParseException(message + " at position " + current.position() + ", got '" + current.text() + "'");
        }
        return advance();
    }
}
