package org.kinotic.auth.api.expressions;

/**
 * Logical AND of two expressions.
 */
public record AndExpression(PolicyExpression left, PolicyExpression right) implements PolicyExpression {}
