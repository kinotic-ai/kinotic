package org.kinotic.sql.parsers;

import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.AlterTableStatement;
import org.kinotic.sql.parser.KinoticSQLParser;
import org.springframework.stereotype.Component;

/**
 * Parses ALTER TABLE statements into AlterTableStatement objects.
 * Adds new fields to existing Elasticsearch indices.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
@Component
public class AlterTableStatementParser implements StatementParser {
    @Override
    public boolean supports(KinoticSQLParser.StatementContext ctx) {
        return ctx.alterTableStatement() != null;
    }

    @Override
    public Statement parse(KinoticSQLParser.StatementContext ctx) {
        KinoticSQLParser.AlterTableStatementContext alterCtx = ctx.alterTableStatement();
        String tableName = Identifiers.name(alterCtx.identifier(0));
        String columnName = Identifiers.name(alterCtx.identifier(1));
        return new AlterTableStatement(tableName, TypeParser.parseColumnType(columnName, alterCtx.type()));
    }
}