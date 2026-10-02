package org.kinotic.sql.parsers;

import org.kinotic.sql.parser.KinoticSQLParser;

import java.util.stream.Collectors;

/**
 * Reads the names a statement's identifiers stand for.
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 */
public class Identifiers {

    /**
     * The name an identifier stands for: as written, without the double quotes of a quoted one.
     */
    public static String name(KinoticSQLParser.IdentifierContext ctx) {
        String text = ctx.getText();
        return ctx.QUOTED_ID() != null ? text.substring(1, text.length() - 1) : text;
    }

    /**
     * The field a field path names, its parts joined by dots.
     */
    public static String path(KinoticSQLParser.FieldPathContext ctx) {
        return ctx.identifier().stream().map(Identifiers::name).collect(Collectors.joining("."));
    }
}
