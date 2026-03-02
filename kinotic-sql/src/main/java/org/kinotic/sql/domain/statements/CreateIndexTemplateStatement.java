package org.kinotic.sql.domain.statements;

import org.kinotic.sql.domain.Statement;

import java.util.List;

/**
 * Represents a CREATE INDEX TEMPLATE statement.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public record CreateIndexTemplateStatement(
    String templateName,
    String indexPattern,
    String componentTemplate,
    List<TemplatePart> parts
) implements Statement {
}