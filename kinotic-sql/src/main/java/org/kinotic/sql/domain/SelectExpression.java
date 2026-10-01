package org.kinotic.sql.domain;

/**
 * What an aggregate SELECT projects or groups by: a field, or a function call.
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 */
public sealed interface SelectExpression extends FunctionArgument permits FieldReference, FunctionCall {
}
