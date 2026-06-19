package org.kinotic.sql.parsers;

import java.util.List;

import org.kinotic.sql.domain.Column;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.CreateDataStreamStatement;
import org.kinotic.sql.parser.KinoticSQLParser;
import org.springframework.stereotype.Component;

/**
 * Parses CREATE DATA STREAM statements into CreateDataStreamStatement objects.
 * Handles data stream creation with field mappings and an optional LIFECYCLE policy reference.
 * Created by Navíd Mitchell 🤝 Claude on 6/18/26.
 */
@Component
public class CreateDataStreamStatementParser implements StatementParser {
    @Override
    public boolean supports(KinoticSQLParser.StatementContext ctx) {
        return ctx.createDataStreamStatement() != null;
    }

    @Override
    public Statement parse(KinoticSQLParser.StatementContext ctx) {
        KinoticSQLParser.CreateDataStreamStatementContext streamCtx = ctx.createDataStreamStatement();
        String streamName = streamCtx.ID().getText();

        List<Column> columns = streamCtx.columnDefinition().stream()
                .map(def -> TypeParser.parseColumnType(def.ID().getText(), def.type()))
                .toList();

        String lifecyclePolicy = streamCtx.dataStreamOption().stream()
                .filter(opt -> opt.LIFECYCLE() != null)
                .map(opt -> opt.STRING().getText().replaceAll("'", ""))
                .findFirst()
                .orElse(null);

        return new CreateDataStreamStatement(streamName, columns, lifecyclePolicy);
    }
}
