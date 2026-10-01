package org.kinotic.sql.domain;

/**
 * An argument of a {@link FunctionCall}: a field or nested call, every field ({@code *}), or a value.
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 */
public sealed interface FunctionArgument permits SelectExpression, AllFields, ValueArgument {
}
