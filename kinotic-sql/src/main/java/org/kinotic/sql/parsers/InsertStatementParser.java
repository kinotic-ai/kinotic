package org.kinotic.sql.parsers;

import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.InsertStatement;
import org.kinotic.sql.parser.KinoticSQLParser;
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
    private final ValueVisitor valueVisitor = new ValueVisitor();

    @Override
    public boolean supports(KinoticSQLParser.StatementContext ctx) {
        return ctx.insertStatement() != null;
    }

    @Override
    public Statement parse(KinoticSQLParser.StatementContext ctx) {
        KinoticSQLParser.InsertStatementContext insertContext = ctx.insertStatement();
        
        String tableName = insertContext.tableName().getText();
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        // Parse column names if specified
        if (insertContext.LPAREN() != null) {
            insertContext.columnName().forEach(column -> columns.add(column.getText()));
        }

        // Parse values from valueList
        insertContext.valueList().value().forEach(value -> values.add(valueVisitor.visitValue(value)));

        // Check for WITH REFRESH
        boolean refresh = insertContext.WITH() != null && insertContext.REFRESH() != null;

        return new InsertStatement(tableName, columns, values, refresh);
    }
} 