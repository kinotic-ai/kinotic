package org.kinotic.persistence.sql.parsers;

import org.kinotic.persistence.sql.domain.Statement;
import org.kinotic.persistence.sql.domain.statements.InsertStatement;
import org.kinotic.persistence.sql.parser.StructuresSQLParser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses INSERT statements into InsertStatement objects.
 * Handles insertion of new documents into an Elasticsearch index.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
@Component
public class InsertStatementParser implements StatementParser {

    @Override
    public boolean supports(StructuresSQLParser.StatementContext ctx) {
        return ctx.insertStatement() != null;
    }

    @Override
    public Statement parse(StructuresSQLParser.StatementContext ctx) {
        StructuresSQLParser.InsertStatementContext insertContext = ctx.insertStatement();
        
        String tableName = insertContext.tableName().getText();
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        // Parse column names if specified
        if (insertContext.LPAREN() != null) {
            insertContext.columnName().forEach(column -> columns.add(column.getText()));
        }

        // Parse values from valueList
        insertContext.valueList().value().forEach(value -> {
            if (value.STRING() != null) {
                // Remove quotes from string literals
                values.add(value.STRING().getText().substring(1, value.STRING().getText().length() - 1));
            } else if (value.INTEGER_LITERAL() != null) {
                values.add(Integer.parseInt(value.INTEGER_LITERAL().getText()));
            } else if (value.BOOLEAN_LITERAL() != null) {
                values.add(Boolean.parseBoolean(value.BOOLEAN_LITERAL().getText()));
            } else if (value.PARAMETER() != null) {
                values.add(null); // Parameter will be set later
            }
        });

        // Check for WITH REFRESH
        boolean refresh = insertContext.WITH() != null && insertContext.REFRESH() != null;

        return new InsertStatement(tableName, columns, values, refresh);
    }
} 