package org.kinotic.sql.parsers;

import org.kinotic.sql.domain.NamedParameter;
import org.kinotic.sql.parser.KinoticSQLBaseVisitor;
import org.kinotic.sql.parser.KinoticSQLParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Visitor for parsing SQL-like literal values into the Java objects a document is built from.
 * Object literals become {@link Map}s and array literals {@link List}s, so composite columns
 * (OBJECT, NESTED, UNION) can be given their sub-document values directly.
 * A null literal yields null, and a parameter reference ({@code :name}) a {@link NamedParameter}
 * that is resolved when the statement is executed.
 * Created by Navíd Mitchell 🤝 Claude on 8/20/26.
 */
public class ValueVisitor extends KinoticSQLBaseVisitor<Object> {

    @Override
    public Object visitValue(KinoticSQLParser.ValueContext ctx) {
        Object ret;
        if (ctx.STRING() != null) {
            ret = unquote(ctx.STRING().getText());
        } else if (ctx.numberLiteral() != null) {
            ret = visitNumberLiteral(ctx.numberLiteral());
        } else if (ctx.BOOLEAN_LITERAL() != null) {
            ret = Boolean.parseBoolean(ctx.BOOLEAN_LITERAL().getText());
        } else if (ctx.objectLiteral() != null) {
            ret = visitObjectLiteral(ctx.objectLiteral());
        } else if (ctx.arrayLiteral() != null) {
            ret = visitArrayLiteral(ctx.arrayLiteral());
        } else if (ctx.namedParameter() != null) {
            ret = new NamedParameter(ctx.namedParameter().ID().getText());
        } else {
            ret = null; // NULL_LITERAL
        }
        return ret;
    }

    @Override
    public Object visitObjectLiteral(KinoticSQLParser.ObjectLiteralContext ctx) {
        // LinkedHashMap keeps the document field order the same as the literal was written in
        Map<String, Object> ret = new LinkedHashMap<>();
        for (KinoticSQLParser.ObjectFieldContext field : ctx.objectField()) {
            String name = fieldName(field);
            if (ret.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate field '" + name + "' in object literal: " + ctx.getText());
            }
            ret.put(name, visitValue(field.value()));
        }
        return ret;
    }

    @Override
    public Object visitArrayLiteral(KinoticSQLParser.ArrayLiteralContext ctx) {
        // ArrayList, not List.of: a parameter placeholder contributes a null element
        List<Object> ret = new ArrayList<>();
        for (KinoticSQLParser.ValueContext value : ctx.value()) {
            ret.add(visitValue(value));
        }
        return ret;
    }

    @Override
    public Object visitNumberLiteral(KinoticSQLParser.NumberLiteralContext ctx) {
        String text = ctx.getText();
        Object ret;
        if (ctx.DECIMAL_LITERAL() != null) {
            ret = Double.parseDouble(text);
        } else {
            long value = Long.parseLong(text);
            // Boxed in each branch: a ternary over Integer and Long numerically promotes both to long
            if ((int) value == value) {
                ret = (int) value;
            } else {
                ret = value;
            }
        }
        return ret;
    }

    private String fieldName(KinoticSQLParser.ObjectFieldContext ctx) {
        return ctx.STRING() != null ? unquote(ctx.STRING().getText()) : ctx.ID().getText();
    }

    private String unquote(String text) {
        return text.substring(1, text.length() - 1);
    }
}
