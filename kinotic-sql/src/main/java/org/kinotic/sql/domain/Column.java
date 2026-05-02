package org.kinotic.sql.domain;

import java.util.List;

/**
 * Represents a column definition in a CREATE TABLE statement.
 * Includes the column name, its data type, whether it should be indexed,
 * and optional sub-columns for composite types (OBJECT, NESTED, UNION).
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public record Column(String name, ColumnType type, boolean indexed, List<Column> subColumns) {

    /**
     * Creates a column that is indexed (default behavior).
     */
    public Column(String name, ColumnType type) {
        this(name, type, true, List.of());
    }

    /**
     * Creates a column with an explicit indexed flag.
     */
    public Column(String name, ColumnType type, boolean indexed) {
        this(name, type, indexed, List.of());
    }
}
