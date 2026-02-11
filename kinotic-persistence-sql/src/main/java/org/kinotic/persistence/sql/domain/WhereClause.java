package org.kinotic.persistence.sql.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents a WHERE clause in SQL-like statements.
 * Supports conditions (e.g., name == 'John'), AND/OR combinations, and nested clauses with parentheses.
 * Reusable for future operations like SELECT or DELETE.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
@Getter
@RequiredArgsConstructor
public abstract class WhereClause {
    // Base class for WHERE clause structure

    @Getter
    @RequiredArgsConstructor
    public static class Condition extends WhereClause {
        private final String field;
        private final String operator; // ==, !=, <, >, <=, >=
        private final String value; // ? or literal value
    }

    @Getter
    @RequiredArgsConstructor
    public static class AndClause extends WhereClause {
        private final WhereClause left;
        private final WhereClause right;
    }

    @Getter
    @RequiredArgsConstructor
    public static class OrClause extends WhereClause {
        private final WhereClause left;
        private final WhereClause right;
    }
}