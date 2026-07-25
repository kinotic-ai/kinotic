package org.kinotic.idl.api.utils;

import org.kinotic.idl.api.annotations.Name;
import org.springframework.core.MethodParameter;

/**
 * Static helpers shared across IDL conversion and its consumers.
 */
public final class IdlUtil {

    private IdlUtil() {
    }

    /**
     * Returns the name a method parameter carries in C3 contracts: the {@link Name} annotation's value when
     * present, otherwise the compiled parameter name. Argument binders for named wire formats must use the same
     * rule, so the emitted schema and the runtime binding cannot drift.
     *
     * @param methodParameter the parameter to name
     * @return the parameter's contract name
     */
    public static String parameterName(MethodParameter methodParameter) {
        Name nameAnnotation = methodParameter.getParameterAnnotation(Name.class);
        return nameAnnotation != null ? nameAnnotation.value() : methodParameter.getParameter().getName();
    }
}
