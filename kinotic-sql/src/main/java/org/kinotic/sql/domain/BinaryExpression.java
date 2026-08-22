package org.kinotic.sql.domain;

/**
 * An operation applied to a field and a second operand, such as {@code age + 1}.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 *
 * @param left     the name of the field the operation reads
 * @param operator one of {@code +}, {@code -}, {@code *}, {@code /}, {@code ==}
 * @param right    the field name, literal, or {@code ?} on the right of the operator, as written
 */
public record BinaryExpression(String left, String operator, String right) implements Expression {
}
