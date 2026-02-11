package org.kinotic.structures.sql.parsers;

import java.util.List;

import org.kinotic.structures.sql.domain.Statement;
import org.kinotic.structures.sql.domain.statements.CreateIndexTemplateStatement;
import org.kinotic.structures.sql.domain.statements.TemplatePart;
import org.kinotic.structures.sql.parser.StructuresSQLParser;
import org.springframework.stereotype.Component;

/**
 * Parses CREATE INDEX TEMPLATE statements into CreateIndexTemplateStatement objects.
 * Handles index template creation with patterns and component templates for Elasticsearch.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
@Component
public class CreateIndexTemplateStatementParser implements StatementParser {
    @Override
    public boolean supports(StructuresSQLParser.StatementContext ctx) {
        return ctx.createIndexTemplateStatement() != null;
    }

    @Override
    public Statement parse(StructuresSQLParser.StatementContext ctx) {
        StructuresSQLParser.CreateIndexTemplateStatementContext templateCtx = ctx.createIndexTemplateStatement();
        String templateName = templateCtx.ID().getText();
        String indexPattern = templateCtx.STRING(0).getText().replaceAll("'", "");
        String componentTemplate = templateCtx.STRING(1).getText().replaceAll("'", "");

        List<TemplatePart> parts = templateCtx.WITH() != null 
            ? TemplatePartParser.parseTemplateParts(templateCtx.templatePart())
            : List.of();

        return new CreateIndexTemplateStatement(templateName, indexPattern, componentTemplate, parts);
    }
}