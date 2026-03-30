package org.kinotic.auth.parsers;

import org.antlr.v4.runtime.*;
import org.kinotic.auth.api.expressions.*;
import org.kinotic.auth.parser.AbacPolicyBaseVisitor;
import org.kinotic.auth.parser.AbacPolicyLexer;
import org.kinotic.auth.parser.AbacPolicyParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses ABAC policy expression strings into {@link PolicyExpression} ASTs
 * using the ANTLR-generated parser from {@code AbacPolicy.g4}.
 * <p>
 * Usage:
 * <pre>
 * PolicyExpression expr = PolicyExpressionParser.parse(
 *     "participant.department == entity.department and entity.status in ['active', 'pending']"
 * );
 * </pre>
 */
public class PolicyExpressionParser {

    private PolicyExpressionParser() {}

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

        AbacPolicyLexer lexer = new AbacPolicyLexer(CharStreams.fromString(expression));
        lexer.removeErrorListeners();
        lexer.addErrorListener(ThrowingErrorListener.INSTANCE);

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        AbacPolicyParser parser = new AbacPolicyParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(ThrowingErrorListener.INSTANCE);

        AbacPolicyParser.PolicyContext tree = parser.policy();
        return new PolicyExpressionVisitor().visit(tree);
    }

    /**
     * ANTLR error listener that throws {@link PolicyParseException} on syntax errors
     * instead of printing to stderr.
     */
    private static class ThrowingErrorListener extends BaseErrorListener {

        static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer,
                                Object offendingSymbol,
                                int line,
                                int charPositionInLine,
                                String msg,
                                RecognitionException e) {
            throw new PolicyParseException("Parse error at position " + charPositionInLine + ": " + msg);
        }
    }

    /**
     * Visitor that converts the ANTLR parse tree into our sealed {@link PolicyExpression} AST.
     */
    private static class PolicyExpressionVisitor extends AbacPolicyBaseVisitor<PolicyExpression> {

        @Override
        public PolicyExpression visitPolicy(AbacPolicyParser.PolicyContext ctx) {
            return visit(ctx.expression());
        }

        @Override
        public PolicyExpression visitAndExpr(AbacPolicyParser.AndExprContext ctx) {
            PolicyExpression left = visit(ctx.expression(0));
            PolicyExpression right = visit(ctx.expression(1));
            return new AndExpression(left, right);
        }

        @Override
        public PolicyExpression visitOrExpr(AbacPolicyParser.OrExprContext ctx) {
            PolicyExpression left = visit(ctx.expression(0));
            PolicyExpression right = visit(ctx.expression(1));
            return new OrExpression(left, right);
        }

        @Override
        public PolicyExpression visitNotExpr(AbacPolicyParser.NotExprContext ctx) {
            PolicyExpression expr = visit(ctx.expression());
            return new NotExpression(expr);
        }

        @Override
        public PolicyExpression visitParenExpr(AbacPolicyParser.ParenExprContext ctx) {
            return visit(ctx.expression());
        }

        @Override
        public PolicyExpression visitComparisonExpr(AbacPolicyParser.ComparisonExprContext ctx) {
            return visit(ctx.comparison());
        }

        @Override
        public PolicyExpression visitPathComparison(AbacPolicyParser.PathComparisonContext ctx) {
            AttributePath left = visitPathToAttributePath(ctx.left);
            ComparisonOperator op = visitComparisonOpToEnum(ctx.op);
            AttributePath right = visitPathToAttributePath(ctx.right);
            return new ComparisonExpression(left, op, right);
        }

        @Override
        public PolicyExpression visitLiteralComparison(AbacPolicyParser.LiteralComparisonContext ctx) {
            AttributePath left = visitPathToAttributePath(ctx.left);
            ComparisonOperator op = visitComparisonOpToEnum(ctx.op);
            LiteralValue right = visitLiteralToValue(ctx.right);
            return new ComparisonExpression(left, op, right);
        }

        @Override
        public PolicyExpression visitInComparison(AbacPolicyParser.InComparisonContext ctx) {
            AttributePath left = visitPathToAttributePath(ctx.left);
            ArrayValue right = visitArrayToValue(ctx.array());
            return new ComparisonExpression(left, ComparisonOperator.IN, right);
        }

        @Override
        public PolicyExpression visitContainsComparison(AbacPolicyParser.ContainsComparisonContext ctx) {
            AttributePath left = visitPathToAttributePath(ctx.left);
            LiteralValue right = visitLiteralToValue(ctx.right);
            return new ComparisonExpression(left, ComparisonOperator.CONTAINS, right);
        }

        @Override
        public PolicyExpression visitExistsComparison(AbacPolicyParser.ExistsComparisonContext ctx) {
            AttributePath left = visitPathToAttributePath(ctx.left);
            return new ComparisonExpression(left, ComparisonOperator.EXISTS, null);
        }

        @Override
        public PolicyExpression visitLikeComparison(AbacPolicyParser.LikeComparisonContext ctx) {
            AttributePath left = visitPathToAttributePath(ctx.left);
            String pattern = stripQuotes(ctx.right.getText());
            LiteralValue right = new LiteralValue(pattern, LiteralValue.LiteralType.STRING);
            return new ComparisonExpression(left, ComparisonOperator.LIKE, right);
        }

        // --- Helper methods that produce AST nodes without going through the visitor dispatch ---

        private AttributePath visitPathToAttributePath(AbacPolicyParser.PathContext ctx) {
            List<Token> identifiers = ctx.IDENTIFIER().stream()
                                                      .map(node -> (Token) node.getSymbol())
                                                      .toList();
            String root = identifiers.get(0).getText();
            List<String> fields = new ArrayList<>(identifiers.size() - 1);
            for (int i = 1; i < identifiers.size(); i++) {
                fields.add(identifiers.get(i).getText());
            }
            return new AttributePath(root, List.copyOf(fields));
        }

        private ComparisonOperator visitComparisonOpToEnum(AbacPolicyParser.ComparisonOpContext ctx) {
            if (ctx.EQ() != null) return ComparisonOperator.EQUALS;
            if (ctx.NEQ() != null) return ComparisonOperator.NOT_EQUALS;
            if (ctx.GT() != null) return ComparisonOperator.GREATER_THAN;
            if (ctx.GTE() != null) return ComparisonOperator.GREATER_THAN_OR_EQUAL;
            if (ctx.LT() != null) return ComparisonOperator.LESS_THAN;
            if (ctx.LTE() != null) return ComparisonOperator.LESS_THAN_OR_EQUAL;
            throw new PolicyParseException("Unknown comparison operator: " + ctx.getText());
        }

        private LiteralValue visitLiteralToValue(AbacPolicyParser.LiteralContext ctx) {
            if (ctx.STRING() != null) {
                return new LiteralValue(stripQuotes(ctx.STRING().getText()), LiteralValue.LiteralType.STRING);
            }
            if (ctx.INTEGER() != null) {
                return new LiteralValue(Long.parseLong(ctx.INTEGER().getText()), LiteralValue.LiteralType.INTEGER);
            }
            if (ctx.DECIMAL() != null) {
                return new LiteralValue(Double.parseDouble(ctx.DECIMAL().getText()), LiteralValue.LiteralType.DECIMAL);
            }
            if (ctx.BOOLEAN() != null) {
                return new LiteralValue(Boolean.parseBoolean(ctx.BOOLEAN().getText()), LiteralValue.LiteralType.BOOLEAN);
            }
            throw new PolicyParseException("Unknown literal: " + ctx.getText());
        }

        private ArrayValue visitArrayToValue(AbacPolicyParser.ArrayContext ctx) {
            List<LiteralValue> values = ctx.literal().stream()
                                           .map(this::visitLiteralToValue)
                                           .toList();
            return new ArrayValue(values);
        }

        private static String stripQuotes(String text) {
            // Remove surrounding single quotes from STRING tokens and unescape internal \'
            if (text.length() >= 2 && text.charAt(0) == '\'' && text.charAt(text.length() - 1) == '\'') {
                return text.substring(1, text.length() - 1).replace("\\'", "'");
            }
            return text;
        }
    }
}
