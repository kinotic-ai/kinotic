package org.kinotic.auth.engines;

import org.kinotic.auth.compilers.GlobPattern;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Operator implementations shared by the candidate engines whose expression languages call out to
 * Java (Janino-generated code and Jakarta EL functions). Every operator denies on a missing
 * attribute or a type mismatch instead of throwing or coercing.
 */
public final class PolicyFunctions {

    private static final Map<String, Pattern> LIKE_PATTERNS = new ConcurrentHashMap<>();

    private PolicyFunctions() {}

    /** Walks nested maps along {@code path}, yielding null as soon as a segment is absent. */
    public static Object get(Map<String, Object> root, String[] path) {
        Object current = root;
        for (int i = 0; i < path.length && current != null; i++) {
            current = current instanceof Map<?, ?> map ? map.get(path[i]) : null;
        }
        return current;
    }

    public static boolean eq(Object a, Object b) {
        boolean ret;
        if (a instanceof Number x && b instanceof Number y) {
            ret = compare(x, y) == 0;
        } else {
            ret = a != null && a.equals(b);
        }
        return ret;
    }

    public static boolean ne(Object a, Object b) {
        return a != null && b != null && !eq(a, b);
    }

    public static boolean lt(Object a, Object b) {
        return a instanceof Number x && b instanceof Number y && compare(x, y) < 0;
    }

    public static boolean gt(Object a, Object b) {
        return a instanceof Number x && b instanceof Number y && compare(x, y) > 0;
    }

    public static boolean le(Object a, Object b) {
        return a instanceof Number x && b instanceof Number y && compare(x, y) <= 0;
    }

    public static boolean ge(Object a, Object b) {
        return a instanceof Number x && b instanceof Number y && compare(x, y) >= 0;
    }

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

    public static boolean in(Object value, Object[] options) {
        boolean ret = false;
        for (int i = 0; i < options.length && !ret; i++) {
            ret = eq(value, options[i]);
        }
        return ret;
    }

    public static boolean exists(Object value) {
        return value != null;
    }

    public static boolean like(Object value, String glob) {
        return value instanceof String s
                && LIKE_PATTERNS.computeIfAbsent(glob, g -> Pattern.compile(GlobPattern.toRegex(g))).matcher(s).matches();
    }

    private static int compare(Number x, Number y) {
        int ret;
        if (x instanceof Double || x instanceof Float || y instanceof Double || y instanceof Float) {
            ret = Double.compare(x.doubleValue(), y.doubleValue());
        } else {
            ret = Long.compare(x.longValue(), y.longValue());
        }
        return ret;
    }
}
