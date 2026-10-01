package org.kinotic.sql.parsers;

import org.kinotic.sql.domain.OrderBy;
import org.kinotic.sql.domain.SortDirection;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.WhereClause;
import org.kinotic.sql.domain.statements.SelectStatement;
import org.kinotic.sql.parser.KinoticSQLParser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses SELECT statements into SelectStatement objects.
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 */
@Component
public class SelectStatementParser implements StatementParser {
    private final WhereClauseVisitor whereClauseVisitor = new WhereClauseVisitor();

    @Override
    public boolean supports(KinoticSQLParser.StatementContext ctx) {
        return ctx.selectStatement() != null;
    }

    @Override
    public Statement parse(KinoticSQLParser.StatementContext ctx) {
        KinoticSQLParser.SelectStatementContext selectCtx = ctx.selectStatement();

        // SELECT * leaves the projection empty: the whole document is read
        List<String> columns = new ArrayList<>();
        selectCtx.selectList().columnName().forEach(column -> columns.add(column.getText()));

        WhereClause whereClause = selectCtx.whereClause() != null ? whereClauseVisitor.visit(selectCtx.whereClause()) : null;

        List<OrderBy> orderBy = new ArrayList<>();
        for (KinoticSQLParser.OrderByContext term : selectCtx.orderBy()) {
            orderBy.add(new OrderBy(term.ID().getText(), term.DESC() != null ? SortDirection.DESC : SortDirection.ASC));
        }

        Integer limit = selectCtx.LIMIT() != null ? Integer.parseInt(selectCtx.INTEGER_LITERAL().getText()) : null;

        return new SelectStatement(selectCtx.ID().getText(), columns, whereClause, orderBy, limit);
    }
}
