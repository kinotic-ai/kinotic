package org.kinotic.sql.domain;

/**
 * A reference to a value supplied when the statement is executed, written {@code :name}.
 * Appears wherever a value can: an INSERT value, a SET assignment, a WHERE comparison, or nested
 * inside an object or array literal.
 * Created by Navíd Mitchell 🤝 Claude on 8/21/26.
 *
 * @param name the name the value is supplied under
 */
public record NamedParameter(String name) implements Expression {

    /**
     * The character a parameter reference starts with.
     */
    public static final String PREFIX = ":";

    /**
     * Whether the given statement text is a parameter reference rather than a literal.
     *
     * @param text the text as written in the statement
     */
    public static boolean isReference(String text) {
        return text.startsWith(PREFIX);
    }

    /**
     * The parameter name carried by a reference, such as {@code minAge} for {@code :minAge}.
     *
     * @param text a parameter reference as written in the statement
     */
    public static String nameOf(String text) {
        return text.substring(PREFIX.length());
    }
}
