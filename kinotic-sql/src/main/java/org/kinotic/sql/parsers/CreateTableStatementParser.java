package org.kinotic.sql.parsers;

import java.util.ArrayList;
import java.util.List;

import org.kinotic.sql.domain.Column;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.CreateTableStatement;
import org.kinotic.sql.parser.StructuresSQLParser;
import org.springframework.stereotype.Component;

/**
 * Parses CREATE TABLE statements into CreateTableStatement objects.
 * Handles creation of Elasticsearch indices with field mappings.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
@Component
public class CreateTableStatementParser implements StatementParser {

    @Override
    public boolean supports(StructuresSQLParser.StatementContext ctx) {
        return ctx.createTableStatement() != null;
    }

    @Override
    public Statement parse(StructuresSQLParser.StatementContext ctx) {
        StructuresSQLParser.CreateTableStatementContext createContext = ctx.createTableStatement();
        String tableName = createContext.ID().getText();
        List<Column> columns = new ArrayList<>();

        // Parse column definitions
        for (StructuresSQLParser.ColumnDefinitionContext columnDef : createContext.columnDefinition()) {
            String name = columnDef.ID().getText();
            columns.add(TypeParser.parseColumnType(name, columnDef.type()));
        }

        // Check for IF NOT EXISTS
        boolean ifNotExists = createContext.IF() != null && createContext.NOT() != null && createContext.EXISTS() != null;

        return new CreateTableStatement(tableName, columns, ifNotExists);
    }
}