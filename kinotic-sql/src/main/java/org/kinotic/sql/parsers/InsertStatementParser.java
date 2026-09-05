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

        boolean refresh = false;
        String routing = null;
        String documentId = null;
        for (KinoticSQLParser.InsertOptionContext option : insertContext.insertOption()) {
            if (option.REFRESH() != null) {
                refresh = true;
            } else if (option.ROUTING() != null) {
                routing = unquote(option.STRING().getText());
            } else {
                documentId = unquote(option.STRING().getText());
            }
        }

        return new InsertStatement(tableName, columns, values, refresh, routing, documentId);
    }

    /** Strips the single quotes the STRING token carries. */
    private static String unquote(String literal) {
        return literal.substring(1, literal.length() - 1);
    }
} 