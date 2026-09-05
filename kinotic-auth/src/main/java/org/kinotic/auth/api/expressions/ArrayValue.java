package org.kinotic.auth.api.expressions;

import java.util.List;

/**
 * An array of literal values, used as the right-hand side of an {@code in} expression.
 */
public record ArrayValue(List<LiteralValue> values) implements Operand {}
