package org.kinotic.auth.api.expressions;

/**
 * The set of comparison operators supported by ABAC policy expressions.
 */
public enum ComparisonOperator {
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    LESS_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN_OR_EQUAL,
    IN,
    CONTAINS,
    EXISTS,
    LIKE
}
