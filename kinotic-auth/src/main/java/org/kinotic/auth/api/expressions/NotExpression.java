package org.kinotic.auth.api.expressions;

/**
 * Logical negation of an expression.
 */
public record NotExpression(PolicyExpression expression) implements PolicyExpression {}
