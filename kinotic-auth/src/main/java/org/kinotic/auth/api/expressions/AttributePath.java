package org.kinotic.auth.api.expressions;

import java.util.List;

/**
 * A dotted attribute path such as {@code principal.department} or {@code order.amount}.
 * <p>
 * The first segment ({@link #root()}) identifies the source object (e.g. {@code principal},
 * {@code context}, or a method parameter name). The remaining segments ({@link #fields()})
 * are nested field accesses on that object.
 */
public record AttributePath(String root, List<String> fields) implements Operand {

    /**
     * Returns the full dotted path string, e.g. {@code "principal.department"}.
     */
    public String toPathString() {
        if (fields.isEmpty()) {
            return root;
        }
        return root + "." + String.join(".", fields);
    }

    /**
     * Returns just the field portion of the path (everything after the root),
     * e.g. for {@code order.address.city} this returns {@code "address.city"}.
     */
    public String fieldPath() {
        return String.join(".", fields);
    }
}
