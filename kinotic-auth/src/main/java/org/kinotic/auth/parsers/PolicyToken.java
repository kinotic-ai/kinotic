package org.kinotic.auth.parsers;

/**
 * A single token produced by the {@link PolicyLexer}.
 */
public record PolicyToken(PolicyTokenType type, String text, int position) {}
