package org.kinotic.sql.domain;

/**
 * An operation applied to a field and a second operand, such as {@code age + 1}.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 *
 * @param left     the name of the field the operation reads
 * @param operator one of {@code +}, {@code -}, {@code *}, {@code /}
 * @param right    the literal value on the right of the operator, or the {@link NamedParameter} it reads
 */
public record BinaryExpression(String left, String operator, Object right) implements Expression {
}
