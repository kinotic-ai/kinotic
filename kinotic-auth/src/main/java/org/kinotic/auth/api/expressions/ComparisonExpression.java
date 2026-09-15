package org.kinotic.auth.api.expressions;

/**
 * A single comparison between a left-hand path and a right-hand operand.
 */
public record ComparisonExpression(AttributePath left,
                                   ComparisonOperator operator,
                                   Operand right) implements PolicyExpression {}
