package org.kinotic.auth.spel;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The only functions a compiled policy may call, registered on the sandboxed evaluation context as
 * {@code #contains} and {@code #like}. Both deny on a missing value or a type mismatch.
 */
public final class SpelPolicyFunctions {

    private static final Map<String, Pattern> LIKE_PATTERNS = new ConcurrentHashMap<>();

    private SpelPolicyFunctions() {}

    /** Whether a collection holds {@code value}, or a string contains it as a substring. */
    public static boolean contains(Object collection, Object value) {
        boolean ret;
        if (collection instanceof Collection<?> c) {
            ret = c.contains(value);
        } else if (collection instanceof String s && value instanceof String v) {
            ret = s.contains(v);
        } else {
            ret = false;
        }
        return ret;
    }

    /** Whether a string matches a glob in which {@code *} matches any sequence of characters. */
    public static boolean like(Object value, String glob) {
        return value instanceof String s && LIKE_PATTERNS.computeIfAbsent(glob, SpelPolicyFunctions::globToRegex).matcher(s).matches();
    }

    // Anchored to the whole value; every character except '*' is literal.
    private static Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if ("\\.[]{}()+-^$|?".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return Pattern.compile(regex.append("$").toString());
    }
}
