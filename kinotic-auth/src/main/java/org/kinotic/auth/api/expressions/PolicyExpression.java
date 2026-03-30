package org.kinotic.auth.api.expressions;

/**
 * Base interface for all policy expression AST nodes.
 * The expression tree is produced by parsing an ABAC policy string
 * and consumed by compilation visitors that target Cedar, Elasticsearch DSL, etc.
 */
public sealed interface PolicyExpression
        permits AndExpression, OrExpression, NotExpression, ComparisonExpression {}
