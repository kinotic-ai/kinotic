package org.kinotic.sql.domain;

/**
 * A value passed to a function call.
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 *
 * @param value a {@link String}, a {@link Number}, or a {@link NamedParameter} supplied when the statement is executed
 */
public record ValueArgument(Object value) implements FunctionArgument {
}
