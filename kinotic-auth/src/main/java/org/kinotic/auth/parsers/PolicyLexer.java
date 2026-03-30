package org.kinotic.auth.parsers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hand-written lexer for ABAC policy expressions.
 * Produces a list of {@link PolicyToken}s from a policy expression string.
 * The grammar specification lives in {@code src/main/antlr/AbacPolicy.g4}.
 */
public class PolicyLexer {

    private static final Map<String, PolicyTokenType> KEYWORDS = Map.ofEntries(
            Map.entry("and", PolicyTokenType.AND),
            Map.entry("or", PolicyTokenType.OR),
            Map.entry("not", PolicyTokenType.NOT),
            Map.entry("in", PolicyTokenType.IN),
            Map.entry("contains", PolicyTokenType.CONTAINS),
            Map.entry("exists", PolicyTokenType.EXISTS),
            Map.entry("like", PolicyTokenType.LIKE),
            Map.entry("true", PolicyTokenType.BOOLEAN),
            Map.entry("false", PolicyTokenType.BOOLEAN)
    );

    private final String input;
    private int pos;

    public PolicyLexer(String input) {
        this.input = input;
        this.pos = 0;
    }

    public List<PolicyToken> tokenize() {
        var tokens = new ArrayList<PolicyToken>();
        while (pos < input.length()) {
            skipWhitespace();
            if (pos >= input.length()) {
                break;
            }

            int start = pos;
            char c = input.charAt(pos);

            // Two-character operators
            if (pos + 1 < input.length()) {
                String twoChar = input.substring(pos, pos + 2);
                PolicyTokenType twoCharType = switch (twoChar) {
                    case "==" -> PolicyTokenType.EQ;
                    case "!=" -> PolicyTokenType.NEQ;
                    case ">=" -> PolicyTokenType.GTE;
                    case "<=" -> PolicyTokenType.LTE;
                    default -> null;
                };
                if (twoCharType != null) {
                    tokens.add(new PolicyToken(twoCharType, twoChar, start));
                    pos += 2;
                    continue;
                }
            }

            // Single-character tokens
            PolicyTokenType singleType = switch (c) {
                case '>' -> PolicyTokenType.GT;
                case '<' -> PolicyTokenType.LT;
                case '.' -> PolicyTokenType.DOT;
                case ',' -> PolicyTokenType.COMMA;
                case '(' -> PolicyTokenType.LPAREN;
                case ')' -> PolicyTokenType.RPAREN;
                case '[' -> PolicyTokenType.LBRACKET;
                case ']' -> PolicyTokenType.RBRACKET;
                default -> null;
            };
            if (singleType != null) {
                tokens.add(new PolicyToken(singleType, String.valueOf(c), start));
                pos++;
                continue;
            }

            // String literal
            if (c == '\'') {
                tokens.add(readString(start));
                continue;
            }

            // Number literal (integer or decimal)
            if (Character.isDigit(c)) {
                tokens.add(readNumber(start));
                continue;
            }

            // Identifier or keyword
            if (Character.isLetter(c) || c == '_') {
                tokens.add(readIdentifierOrKeyword(start));
                continue;
            }

            throw new PolicyParseException("Unexpected character '" + c + "' at position " + start);
        }

        tokens.add(new PolicyToken(PolicyTokenType.EOF, "", pos));
        return tokens;
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private PolicyToken readString(int start) {
        pos++; // skip opening quote
        var sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '\\' && pos + 1 < input.length() && input.charAt(pos + 1) == '\'') {
                sb.append('\'');
                pos += 2;
            } else if (c == '\'') {
                pos++; // skip closing quote
                return new PolicyToken(PolicyTokenType.STRING, sb.toString(), start);
            } else {
                sb.append(c);
                pos++;
            }
        }
        throw new PolicyParseException("Unterminated string literal starting at position " + start);
    }

    private PolicyToken readNumber(int start) {
        boolean hasDecimal = false;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '.' && !hasDecimal && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1))) {
                hasDecimal = true;
                pos++;
            } else if (Character.isDigit(c)) {
                pos++;
            } else {
                break;
            }
        }
        String text = input.substring(start, pos);
        return new PolicyToken(hasDecimal ? PolicyTokenType.DECIMAL : PolicyTokenType.INTEGER, text, start);
    }

    private PolicyToken readIdentifierOrKeyword(int start) {
        while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
            pos++;
        }
        String text = input.substring(start, pos);
        PolicyTokenType keywordType = KEYWORDS.get(text.toLowerCase());
        if (keywordType != null) {
            return new PolicyToken(keywordType, text, start);
        }
        return new PolicyToken(PolicyTokenType.IDENTIFIER, text, start);
    }
}
