package org.kinotic.auth.api.expressions;

/**
 * Logical OR of two expressions.
 */
public record OrExpression(PolicyExpression left, PolicyExpression right) implements PolicyExpression {}
