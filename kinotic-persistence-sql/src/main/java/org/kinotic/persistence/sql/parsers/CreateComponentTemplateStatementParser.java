package org.kinotic.persistence.sql.parsers;

import org.kinotic.persistence.sql.domain.Statement;
import org.kinotic.persistence.sql.domain.statements.CreateComponentTemplateStatement;
import org.kinotic.persistence.sql.parser.StructuresSQLParser;
import org.springframework.stereotype.Component;

/**
 * Parses CREATE COMPONENT TEMPLATE statements into CreateComponentTemplateStatement objects.
 * Handles component template creation for Elasticsearch.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
@Component
public class CreateComponentTemplateStatementParser implements StatementParser {
    @Override
    public boolean supports(StructuresSQLParser.StatementContext ctx) {
        return ctx.createComponentTemplateStatement() != null;
    }

    @Override
    public Statement parse(StructuresSQLParser.StatementContext ctx) {
        var templateCtx = ctx.createComponentTemplateStatement();
        var templateName = templateCtx.ID().getText();
        var parts = TemplatePartParser.parseTemplateParts(templateCtx.templatePart());
        return new CreateComponentTemplateStatement(templateName, parts);
    }
}