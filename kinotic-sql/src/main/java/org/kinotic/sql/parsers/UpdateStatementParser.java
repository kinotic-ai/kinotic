package org.kinotic.sql.parsers;

import org.kinotic.sql.domain.Expression;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.WhereClause;
import org.kinotic.sql.domain.statements.UpdateStatement;
import org.kinotic.sql.parser.KinoticSQLParser;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses UPDATE statements into UpdateStatement objects.
 * Handles SET assignments and complex WHERE clauses for Elasticsearch updates.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
@Component
public class UpdateStatementParser implements StatementParser {
    private final ExpressionVisitor expressionVisitor = new ExpressionVisitor();
    private final WhereClauseVisitor whereClauseVisitor = new WhereClauseVisitor();

    @Override
    public boolean supports(KinoticSQLParser.StatementContext ctx) {
        return ctx.updateStatement() != null;
    }

    @Override
    public Statement parse(KinoticSQLParser.StatementContext ctx) {
        KinoticSQLParser.UpdateStatementContext updateCtx = ctx.updateStatement();
        String tableName = updateCtx.ID().getText();

        Map<String, Expression> assignments = new LinkedHashMap<>();
        for (KinoticSQLParser.AssignmentContext assignment : updateCtx.assignment()) {
            String field = assignment.ID().getText();
            Expression expression = expressionVisitor.visit(assignment.expression());
            assignments.put(field, expression);
        }

        WhereClause whereClause = whereClauseVisitor.visit(updateCtx.whereClause());

        // Check for WITH REFRESH
        boolean refresh = updateCtx.WITH() != null && updateCtx.REFRESH() != null;

        return new UpdateStatement(tableName, assignments, whereClause, refresh);
    }
}