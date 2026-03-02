package org.kinotic.sql.domain.statements;

import org.kinotic.sql.domain.Column;

/**
 * Template part for column mappings.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public class ColumnTemplatePart implements TemplatePart {
    private final Column column;

    public ColumnTemplatePart(Column column) {
        this.column = column;
    }

    public Column column() {
        return column;
    }
} 