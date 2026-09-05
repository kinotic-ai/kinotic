package org.kinotic.auth.api.expressions;

/**
 * Marker interface for the right-hand side of a comparison.
 * Can be an {@link AttributePath}, {@link LiteralValue}, or {@link ArrayValue}.
 */
public sealed interface Operand permits AttributePath, LiteralValue, ArrayValue {}
