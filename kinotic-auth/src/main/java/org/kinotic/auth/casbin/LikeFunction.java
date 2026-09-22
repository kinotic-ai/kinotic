package org.kinotic.auth.casbin;

import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorBoolean;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.kinotic.auth.compilers.GlobPattern;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * AviatorScript function {@code like(value, pattern)} implementing the ABAC {@code like} operator:
 * a glob match where {@code *} matches any sequence of characters and every other character is
 * literal, anchored to the whole value (mirroring Cedar's {@code like}). Used because AviatorScript's
 * regex-match operator ({@code =~}) is not enabled on the default evaluator instance.
 */
final class LikeFunction extends AbstractFunction {

    private final Map<String, Pattern> patterns = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "like";
    }

    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject value, AviatorObject pattern) {
        Object v = value.getValue(env);
        Object p = pattern.getValue(env);
        boolean matches = v instanceof String s && p instanceof String glob
                && patterns.computeIfAbsent(glob, g -> Pattern.compile(GlobPattern.toRegex(g))).matcher(s).matches();
        return AviatorBoolean.valueOf(matches);
    }
}
