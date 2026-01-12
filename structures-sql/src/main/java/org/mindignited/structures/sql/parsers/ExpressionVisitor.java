package org.mindignited.structures.sql.parsers;

import org.mindignited.structures.sql.domain.Expression;
import org.mindignited.structures.sql.parser.StructuresSQLBaseVisitor;
import org.mindignited.structures.sql.parser.StructuresSQLParser;

/**
 * Visitor for parsing SQL-like expressions (e.g., literals, binary expressions).
 * Reusable across statement parsers like UPDATE and DELETE.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public class ExpressionVisitor extends StructuresSQLBaseVisitor<Expression> {
    @Override
    public Expression visitExpression(StructuresSQLParser.ExpressionContext ctx) {
        if (ctx.PARAMETER() != null) {
            return new Expression.Literal(ctx.PARAMETER().getText());
        } else if (ctx.STRING() != null) {
            return new Expression.Literal(ctx.STRING().getText());
        } else if (ctx.INTEGER_LITERAL() != null) {
            return new Expression.Literal(ctx.INTEGER_LITERAL().getText());
        } else if (ctx.BOOLEAN_LITERAL() != null) {
            return new Expression.Literal(ctx.BOOLEAN_LITERAL().getText());
        } else if (ctx.operator() != null) {
            // Binary expression: ID operator expression
            String left = ctx.ID().getText();
            String operator = ctx.operator().getText();
            StructuresSQLParser.ExpressionContext rightCtx = ctx.expression();
            String right = rightCtx.PARAMETER() != null ? rightCtx.PARAMETER().getText()
                    : rightCtx.STRING() != null ? rightCtx.STRING().getText()
                    : rightCtx.INTEGER_LITERAL() != null ? rightCtx.INTEGER_LITERAL().getText()
                    : rightCtx.BOOLEAN_LITERAL() != null ? rightCtx.BOOLEAN_LITERAL().getText()
                    : rightCtx.ID().getText();
            return new Expression.BinaryExpression(left, operator, right);
        } else if (ctx.LPAREN() != null) {
            return visit(ctx.expression()); // Unwrap parentheses
        }
        throw new IllegalStateException("Invalid expression");
    }
}