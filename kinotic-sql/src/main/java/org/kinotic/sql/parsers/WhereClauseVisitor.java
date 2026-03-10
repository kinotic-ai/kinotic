package org.kinotic.sql.parsers;

import org.kinotic.sql.domain.WhereClause;
import org.kinotic.sql.parser.KinoticSQLParser;
import org.kinotic.sql.parser.KinoticSQLBaseVisitor;

/**
 * Visitor for parsing SQL-like WHERE clauses (e.g., conditions, AND/OR combinations).
 * Reusable across statement parsers like UPDATE and DELETE.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public class WhereClauseVisitor extends KinoticSQLBaseVisitor<WhereClause> {
    @Override
    public WhereClause visitWhereClause(KinoticSQLParser.WhereClauseContext ctx) {
        if (ctx.condition() != null) {
            String field = ctx.condition().ID().getText();
            String operator = ctx.condition().comparisonOperator().getText();
            String value = ctx.condition().PARAMETER() != null ? ctx.condition().PARAMETER().getText()
                    : ctx.condition().STRING() != null ? ctx.condition().STRING().getText()
                    : ctx.condition().INTEGER_LITERAL() != null ? ctx.condition().INTEGER_LITERAL().getText()
                    : ctx.condition().BOOLEAN_LITERAL().getText();
            return new WhereClause.Condition(field, operator, value);
        } else if (ctx.LPAREN() != null) {
            return visit(ctx.whereClause(0)); // Unwrap parentheses
        } else if (ctx.AND() != null) {
            return new WhereClause.AndClause(visit(ctx.whereClause(0)), visit(ctx.whereClause(1)));
        } else if (ctx.OR() != null) {
            return new WhereClause.OrClause(visit(ctx.whereClause(0)), visit(ctx.whereClause(1)));
        }
        throw new IllegalStateException("Invalid WHERE clause");
    }
}