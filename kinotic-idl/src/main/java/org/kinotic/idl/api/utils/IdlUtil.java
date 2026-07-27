package org.kinotic.idl.api.utils;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.idl.api.annotations.Name;
import org.springframework.core.MethodParameter;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static helpers shared across IDL conversion and its consumers.
 */
@Slf4j
public final class IdlUtil {

    private IdlUtil() {
    }

    /**
     * Resolves the functions a service contract declares: every user-declared method keyed by function name.
     * Overloading is not supported; when a name is declared more than once only one method is kept. Schema
     * generation and service registration must both use this rule, so a published schema never carries a
     * function the registry does not serve.
     *
     * @param serviceClass the service contract to introspect
     * @return the contract's functions keyed by name
     */
    public static Map<String, Method> serviceFunctions(Class<?> serviceClass) {
        Map<String, Method> ret = new LinkedHashMap<>();
        ReflectionUtils.doWithMethods(serviceClass, method -> {
            Method existing = ret.putIfAbsent(method.getName(), method);
            // a default method can be visited twice; only a genuinely different signature is an overload
            if (existing != null && !existing.equals(method)) {
                log.warn("{} has overloaded method {} overloading is not supported. \n {} will be ignored",
                         serviceClass.getName(),
                         method.getName(),
                         method.toGenericString());
            }
        }, ReflectionUtils.USER_DECLARED_METHODS);
        return ret;
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
