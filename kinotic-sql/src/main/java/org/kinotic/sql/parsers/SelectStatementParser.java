package org.kinotic.sql.parsers;

import org.kinotic.sql.domain.AllFields;
import org.kinotic.sql.domain.FieldReference;
import org.kinotic.sql.domain.FunctionArgument;
import org.kinotic.sql.domain.FunctionCall;
import org.kinotic.sql.domain.NamedParameter;
import org.kinotic.sql.domain.OrderBy;
import org.kinotic.sql.domain.Projection;
import org.kinotic.sql.domain.SelectExpression;
import org.kinotic.sql.domain.SortDirection;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.ValueArgument;
import org.kinotic.sql.domain.WhereClause;
import org.kinotic.sql.domain.statements.AggregateStatement;
import org.kinotic.sql.domain.statements.SelectStatement;
import org.kinotic.sql.parser.KinoticSQLParser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses SELECT statements into SelectStatement objects, or into AggregateStatement objects when the SELECT list or
 * GROUP BY calls a function or the statement groups its rows.
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 */
@Component
public class SelectStatementParser implements StatementParser {
    private final WhereClauseVisitor whereClauseVisitor = new WhereClauseVisitor();
    private final ValueVisitor valueVisitor = new ValueVisitor();

    @Override
    public boolean supports(KinoticSQLParser.StatementContext ctx) {
        return ctx.selectStatement() != null;
    }

    @Override
    public Statement parse(KinoticSQLParser.StatementContext ctx) {
        KinoticSQLParser.SelectStatementContext selectCtx = ctx.selectStatement();

        List<Projection> projections = new ArrayList<>();
        for (KinoticSQLParser.SelectItemContext item : selectCtx.selectList().selectItem()) {
            projections.add(new Projection(selectExpression(item.selectExpression()), item.identifier() != null ? Identifiers.name(item.identifier()) : null));
        }
        List<SelectExpression> groupBy = new ArrayList<>();
        selectCtx.selectExpression().forEach(expression -> groupBy.add(selectExpression(expression)));

        WhereClause whereClause = selectCtx.whereClause() != null ? whereClauseVisitor.visit(selectCtx.whereClause()) : null;
        List<OrderBy> orderBy = new ArrayList<>();
        for (KinoticSQLParser.OrderByContext term : selectCtx.orderBy()) {
            orderBy.add(new OrderBy(Identifiers.path(term.fieldPath()), term.DESC() != null ? SortDirection.DESC : SortDirection.ASC));
        }
        Integer limit = selectCtx.LIMIT() != null ? Integer.parseInt(selectCtx.INTEGER_LITERAL().getText()) : null;
        String tableName = Identifiers.name(selectCtx.identifier());

        Statement ret;
        if (!groupBy.isEmpty() || projections.stream().anyMatch(projection -> projection.expression() instanceof FunctionCall)) {
            if (projections.isEmpty()) {
                throw new IllegalArgumentException("SELECT * FROM " + tableName + " cannot group its rows");
            }
            ret = new AggregateStatement(tableName, projections, whereClause, groupBy, orderBy, limit);
        } else {
            // SELECT * leaves the projection empty: the whole document is read
            List<String> columns = new ArrayList<>();
            for (Projection projection : projections) {
                String path = ((FieldReference) projection.expression()).path();
                if (projection.alias() != null || path.contains(".")) {
                    throw new IllegalArgumentException("SELECT FROM " + tableName + " lists top-level fields, so "
                                                               + item(projection) + " is not allowed; a SELECT that calls a "
                                                               + "function can name sub-fields and aliases");
                }
                columns.add(path);
            }
            ret = new SelectStatement(tableName, columns, whereClause, orderBy, limit);
        }
        return ret;
    }

    private static String item(Projection projection) {
        String path = ((FieldReference) projection.expression()).path();
        return projection.alias() != null ? path + " AS " + projection.alias() : path;
    }

    private SelectExpression selectExpression(KinoticSQLParser.SelectExpressionContext ctx) {
        SelectExpression ret;
        if (ctx.functionCall() != null) {
            KinoticSQLParser.FunctionCallContext call = ctx.functionCall();
            List<FunctionArgument> arguments = new ArrayList<>();
            if (call.MULTIPLY() != null) {
                arguments.add(new AllFields());
            }
            call.functionArgument().forEach(argument -> arguments.add(functionArgument(argument)));
            ret = new FunctionCall(Identifiers.name(call.identifier()), arguments);
        } else {
            ret = new FieldReference(Identifiers.path(ctx.fieldPath()));
        }
        return ret;
    }

    private FunctionArgument functionArgument(KinoticSQLParser.FunctionArgumentContext ctx) {
        FunctionArgument ret;
        if (ctx.selectExpression() != null) {
            ret = selectExpression(ctx.selectExpression());
        } else if (ctx.STRING() != null) {
            String quoted = ctx.STRING().getText();
            ret = new ValueArgument(quoted.substring(1, quoted.length() - 1));
        } else if (ctx.numberLiteral() != null) {
            ret = new ValueArgument(valueVisitor.visitNumberLiteral(ctx.numberLiteral()));
        } else {
            ret = new ValueArgument(new NamedParameter(Identifiers.name(ctx.namedParameter().identifier())));
        }
        return ret;
    }
}
