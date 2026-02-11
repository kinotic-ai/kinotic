package org.kinotic.persistence.sql.domain;

/**
 * Represents the supported data types for columns in the SQL DSL.
 * These types map directly to Elasticsearch field types.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public enum ColumnType {
    TEXT,
    KEYWORD,
    INTEGER,
    LONG,
    FLOAT,
    DOUBLE,
    BOOLEAN,
    DATE,
    JSON,
    BINARY,
    GEO_POINT,
    GEO_SHAPE,
    UUID,
    DECIMAL;

    @Override
    public String toString() {
        return name();
    }
} 