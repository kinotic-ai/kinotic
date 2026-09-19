package org.kinotic.auth.casbin;

import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorBoolean;
import com.googlecode.aviator.runtime.type.AviatorObject;

import java.util.Map;

/**
 * AviatorScript function {@code like(value, pattern)} implementing the ABAC {@code like} operator:
 * a glob match where {@code *} matches any sequence of characters and every other character is
 * literal, anchored to the whole value (mirroring Cedar's {@code like}). Used because AviatorScript's
 * regex-match operator ({@code =~}) is not enabled on the default evaluator instance.
 */
final class LikeFunction extends AbstractFunction {

    @Override
    public String getName() {
        return "like";
    }

    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject value, AviatorObject pattern) {
        Object v = value.getValue(env);
        Object p = pattern.getValue(env);
        if (!(v instanceof String s) || !(p instanceof String glob)) {
            return AviatorBoolean.FALSE;
        }
        return AviatorBoolean.valueOf(s.matches(globToRegex(glob)));
    }

    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if ("\\.[]{}()+-^$|?/".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return regex.append("$").toString();
    }
}
