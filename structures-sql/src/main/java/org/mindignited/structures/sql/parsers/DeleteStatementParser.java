package org.mindignited.structures.sql.parsers;

import org.mindignited.structures.sql.domain.Statement;
import org.mindignited.structures.sql.domain.WhereClause;
import org.mindignited.structures.sql.domain.statements.DeleteStatement;
import org.mindignited.structures.sql.parser.StructuresSQLParser;
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