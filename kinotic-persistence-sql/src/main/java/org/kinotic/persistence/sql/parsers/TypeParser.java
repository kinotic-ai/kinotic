package org.kinotic.persistence.sql.parsers;

import org.kinotic.persistence.sql.domain.Column;
import org.kinotic.persistence.sql.domain.ColumnType;
import org.kinotic.persistence.sql.parser.StructuresSQLParser;

/**
 * Utility class for parsing column types from SQL grammar contexts.
 * Handles both regular types and NOT INDEXED variants.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public class TypeParser {
    
    /**
     * Parses a type context and returns a complete Column with name, type, and indexed flag.
     * Handles NOT INDEXED modifiers by checking the number of children in the context.
     * 
     * @param name The column name
     * @param typeContext The type context from the parser
     * @return A complete Column with name, type, and indexed flag
     */
    public static Column parseColumnType(String name, StructuresSQLParser.TypeContext typeContext) {
        // Get the base type name
        String baseType = typeContext.getChild(0).getText();
        
        // Check if NOT INDEXED is present by looking at the number of children
        // If there are 3 children, it means we have: TYPE NOT INDEXED
        boolean indexed = typeContext.getChildCount() == 1;
        
        ColumnType columnType = ColumnType.valueOf(baseType);
        return new Column(name, columnType, indexed);
    }
} 