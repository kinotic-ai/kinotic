package org.mindignited.structures.sql.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents an expression in SQL-like statements, such as SET clauses.
 * Supports literals (e.g., 'active', 30) and binary expressions (e.g., age + 1).
 * Reusable for future operations like SELECT.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
@Getter
@RequiredArgsConstructor
public abstract class Expression {
    // Base class for expressions in SET clauses or elsewhere

    @Getter
    @RequiredArgsConstructor
    public static class Literal extends Expression {
        private final String value; // e.g., "'active'", "30", "true", "?"
    }

    @Getter
    @RequiredArgsConstructor
    public static class BinaryExpression extends Expression {
        private final String left; // Field name or literal
        private final String operator; // +, -, *, /, ==
        private final String right; // Field name, literal, or ?
    }
}