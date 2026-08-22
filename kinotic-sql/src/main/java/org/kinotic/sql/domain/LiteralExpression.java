package org.kinotic.sql.domain;

/**
 * A literal value written directly into a statement, such as {@code 'active'}, {@code 30},
 * or an object literal like <code>{ city: 'Springfield' }</code>.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 *
 * @param value the literal as the Java type it will be stored as: String, Integer, Long, Double,
 *              Boolean, a {@link java.util.Map} for an object literal, or a {@link java.util.List}
 *              for an array literal
 */
public record LiteralExpression(Object value) implements Expression {
}
