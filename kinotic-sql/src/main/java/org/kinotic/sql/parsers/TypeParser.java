package org.kinotic.sql.parsers;

import org.kinotic.sql.domain.Column;
import org.kinotic.sql.domain.ColumnType;
import org.kinotic.sql.parser.KinoticSQLParser;

import java.util.List;

/**
 * Utility class for parsing column types from SQL grammar contexts.
 * Handles flat types, NOT INDEXED variants, and composite types (OBJECT, NESTED, UNION).
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public class TypeParser {

    public static Column parseColumnType(String name, KinoticSQLParser.TypeContext typeContext) {
        String baseType = typeContext.getChild(0).getText();

        // NOT INDEXED detection: for both flat and composite types, the last child token
        // text is "INDEXED" when NOT INDEXED is present.
        boolean indexed = !"INDEXED".equals(
            typeContext.getChild(typeContext.getChildCount() - 1).getText());

        ColumnType columnType = ColumnType.valueOf(baseType);

        return switch (columnType) {
            case OBJECT -> new Column(name, ColumnType.OBJECT, indexed,
                parseSubColumns(typeContext.columnDefinition()));
            case NESTED -> new Column(name, ColumnType.NESTED, true,
                parseSubColumns(typeContext.columnDefinition()));
            case UNION -> new Column(name, ColumnType.UNION, indexed,
                parseUnionVariants(typeContext.unionVariant()));
            default -> new Column(name, columnType, indexed);
        };
    }

    private static List<Column> parseSubColumns(List<KinoticSQLParser.ColumnDefinitionContext> defs) {
        return defs.stream()
                   .map(def -> parseColumnType(def.ID().getText(), def.type()))
                   .toList();
    }

    private static List<Column> parseUnionVariants(List<KinoticSQLParser.UnionVariantContext> variants) {
        return variants.stream()
                       .map(v -> new Column(v.ID().getText(), ColumnType.OBJECT, true,
                           parseSubColumns(v.columnDefinition())))
                       .toList();
    }
}
