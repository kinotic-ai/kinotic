package org.kinotic.auth.compilers;

/**
 * Translates the ABAC {@code like} glob syntax — {@code *} matches any sequence of characters and
 * every other character is literal — into a regular expression anchored to the whole value.
 */
public final class GlobPattern {

    private GlobPattern() {}

    public static String toRegex(String glob) {
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
        return regex.append("$").toString();
    }
}
