package org.mindignited.structures.sql.domain;

/**
 * Represents a column definition in a CREATE TABLE statement.
 * Includes the column name, its data type, and whether it should be indexed.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public record Column(String name, ColumnType type, boolean indexed) {
    
    /**
     * Creates a column that is indexed (default behavior).
     */
    public Column(String name, ColumnType type) {
        this(name, type, true);
    }
}