package org.kinotic.sql.parsers;

import org.kinotic.sql.domain.BinaryExpression;
import org.kinotic.sql.domain.Expression;
import org.kinotic.sql.domain.LiteralExpression;
import org.kinotic.sql.domain.ParameterExpression;
import org.kinotic.sql.parser.KinoticSQLBaseVisitor;
import org.kinotic.sql.parser.KinoticSQLParser;

/**
 * Visitor for parsing SQL-like expressions (e.g., literals, binary expressions).
 * Reusable across statement parsers like UPDATE and DELETE.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public class ExpressionVisitor extends KinoticSQLBaseVisitor<Expression> {
    private final ValueVisitor valueVisitor = new ValueVisitor();

    @Override
    public Expression visitExpression(KinoticSQLParser.ExpressionContext ctx) {
        Expression ret;
        if (ctx.value() != null) {
            ret = ctx.value().PARAMETER() != null
                    ? new ParameterExpression()
                    : new LiteralExpression(valueVisitor.visitValue(ctx.value()));
        } else if (ctx.operator() != null) {
            // Binary expression: ID operator expression
            KinoticSQLParser.ExpressionContext rightCtx = ctx.expression();
            // The right operand is kept as written since it is spliced into the update script as source
            String right = rightCtx.value() != null ? rightCtx.value().getText() : rightCtx.ID().getText();
            ret = new BinaryExpression(ctx.ID().getText(), ctx.operator().getText(), right);
        } else if (ctx.LPAREN() != null) {
            ret = visit(ctx.expression()); // Unwrap parentheses
        } else {
            throw new IllegalStateException("Invalid expression: " + ctx.getText());
        }
        return ret;
    }
}
