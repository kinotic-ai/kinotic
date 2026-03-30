package org.kinotic.auth.parsers;

/**
 * Token types produced by the {@link PolicyLexer}.
 * The grammar specification lives in {@code src/main/antlr/AbacPolicy.g4}.
 */
public enum PolicyTokenType {
    // Keywords
    AND, OR, NOT, IN, CONTAINS, EXISTS, LIKE,

    // Comparison operators
    EQ, NEQ, GT, GTE, LT, LTE,

    // Punctuation
    DOT, COMMA, LPAREN, RPAREN, LBRACKET, RBRACKET,

    // Literals
    STRING, INTEGER, DECIMAL, BOOLEAN,

    // Identifier
    IDENTIFIER,

    // End of input
    EOF
}
