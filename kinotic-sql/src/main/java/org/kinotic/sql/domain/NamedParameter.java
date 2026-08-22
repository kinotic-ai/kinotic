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
}
