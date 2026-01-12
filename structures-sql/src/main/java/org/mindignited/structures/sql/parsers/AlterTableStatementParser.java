package org.mindignited.structures.sql.parsers;

import org.mindignited.structures.sql.domain.Statement;
import org.mindignited.structures.sql.domain.statements.AlterTableStatement;
import org.mindignited.structures.sql.parser.StructuresSQLParser;
import org.springframework.stereotype.Component;

/**
 * Parses ALTER TABLE statements into AlterTableStatement objects.
 * Adds new fields to existing Elasticsearch indices.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
@Component
public class AlterTableStatementParser implements StatementParser {
    @Override
    public boolean supports(StructuresSQLParser.StatementContext ctx) {
        return ctx.alterTableStatement() != null;
    }

    @Override
    public Statement parse(StructuresSQLParser.StatementContext ctx) {
        StructuresSQLParser.AlterTableStatementContext alterCtx = ctx.alterTableStatement();
        String tableName = alterCtx.ID(0).getText();
        String columnName = alterCtx.ID(1).getText();
        return new AlterTableStatement(tableName, TypeParser.parseColumnType(columnName, alterCtx.type()));
    }
}