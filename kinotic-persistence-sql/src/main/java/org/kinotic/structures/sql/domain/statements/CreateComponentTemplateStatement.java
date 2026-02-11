package org.kinotic.structures.sql.domain.statements;

import org.kinotic.structures.sql.domain.Statement;

import java.util.List;

/**
 * Represents a CREATE COMPONENT TEMPLATE statement.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public record CreateComponentTemplateStatement(String templateName, List<TemplatePart> parts) implements Statement {
}