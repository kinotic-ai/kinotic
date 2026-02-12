package org.kinotic.persistence.sql.parsers;

import org.kinotic.persistence.sql.domain.Statement;
import org.kinotic.persistence.sql.domain.WhereClause;
import org.kinotic.persistence.sql.domain.statements.DeleteStatement;
import org.kinotic.persistence.sql.parser.StructuresSQLParser;
import org.springframework.stereotype.Component;

/**
 * Parses DELETE statements into DeleteStatement objects.
 * Handles deletion of documents from an Elasticsearch index based on a WHERE clause.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
@Component
public class DeleteStatementParser implements StatementParser {
    private final WhereClauseVisitor whereClauseVisitor = new WhereClauseVisitor();

    @Override
    public boolean supports(StructuresSQLParser.StatementContext ctx) {
        return ctx.deleteStatement() != null;
    }

    @Override
    public Statement parse(StructuresSQLParser.StatementContext ctx) {
        StructuresSQLParser.DeleteStatementContext deleteContext = ctx.deleteStatement();
        String tableName = deleteContext.ID().getText();
        WhereClause whereClause = whereClauseVisitor.visit(deleteContext.whereClause());

        // Check for WITH REFRESH
        boolean refresh = deleteContext.WITH() != null && deleteContext.REFRESH() != null;

        return new DeleteStatement(tableName, whereClause, refresh);
    }
}