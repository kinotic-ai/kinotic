package org.kinotic.sql.executor;

import org.kinotic.sql.domain.NamedParameter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recognizes the {@link NamedParameter} references a statement carries and resolves them against the
 * values supplied for an execution, keyed by parameter name.
 * Created by Navíd Mitchell 🤝 Claude on 8/21/26.
 */
public class ParameterUtils {

    private static final String PREFIX = ":";

    /**
     * Whether the given statement text is a parameter reference rather than a literal.
     *
     * @param text the text as written in the statement
     */
    public static boolean isReference(String text) {
        return text.startsWith(PREFIX);
    }

    /**
     * The parameter name carried by a reference, such as {@code minAge} for {@code :minAge}.
     *
     * @param text a parameter reference as written in the statement
     */
    public static String nameOf(String text) {
        return text.substring(PREFIX.length());
    }

    /**
     * Returns the given value with every parameter reference in it replaced by its supplied value.
     * Object and array literals are walked, so a parameter nested inside one is resolved as well.
     *
     * @param value      a value materialized by the parser
     * @param parameters the values supplied for this execution, keyed by parameter name
     * @return the value with its parameters resolved, or the value itself when it carries none
     */
    public static Object bind(Object value, Map<String, Object> parameters) {
        Object ret;
        if (value instanceof NamedParameter(String name)) {
            ret = resolve(name, parameters);
        } else if (value instanceof Map<?, ?> map) {
            Map<String, Object> bound = new LinkedHashMap<>();
            map.forEach((key, entry) -> bound.put((String) key, bind(entry, parameters)));
            ret = bound;
        } else if (value instanceof List<?> list) {
            List<Object> bound = new ArrayList<>();
            list.forEach(entry -> bound.add(bind(entry, parameters)));
            ret = bound;
        } else {
            ret = value;
        }
        return ret;
    }

    /**
     * Returns the value supplied for the named parameter.
     *
     * @param name       the parameter name, without its {@code :} prefix
     * @param parameters the values supplied for this execution, keyed by parameter name
     * @throws IllegalStateException    if the statement uses parameters but none were supplied
     * @throws IllegalArgumentException if no value was supplied for this parameter
     */
    public static Object resolve(String name, Map<String, Object> parameters) {
        if (parameters == null) {
            throw new IllegalStateException("Statement uses parameter " + PREFIX + name
                                                    + " but no parameters were supplied");
        }
        Object ret = parameters.get(name);
        if (ret == null) {
            throw new IllegalArgumentException("Missing value for parameter " + PREFIX + name);
        }
        return ret;
    }
}
