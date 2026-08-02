package org.kinotic.idl.api.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
     * Resolves the functions a service interface declares: every user-declared method keyed by function name.
     * Overloading is not supported; when a name is declared more than once only one method is kept. Schema
     * generation and service registration must both use this rule, so a published schema never carries a
     * function the registry does not serve.
     *
     * @param serviceInterface the service interface to introspect
     * @return the interface's functions keyed by name
     */
    public static Map<String, Method> serviceFunctions(Class<?> serviceInterface) {
        Map<String, Method> ret = new LinkedHashMap<>();
        ReflectionUtils.doWithMethods(serviceInterface, method -> {
            Method existing = ret.putIfAbsent(method.getName(), method);
            // a default method can be visited twice; only a genuinely different signature is an overload
            if (existing != null && !existing.equals(method)) {
                log.warn("{} has overloaded method {} overloading is not supported. \n {} will be ignored",
                         serviceInterface.getName(),
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

    /**
     * Converts a camel case identifier to a display title: the words split apart and each capitalized, so
     * {@code findByRepoFullName} becomes {@code "Find By Repo Full Name"} and {@code ProjectService} becomes
     * {@code "Project Service"}. An acronym stays one word ({@code OidcConfig} becomes {@code "Oidc Config"},
     * {@code CRIResolver} becomes {@code "CRI Resolver"}).
     *
     * @param name the identifier to humanize
     * @return the identifier as a capitalized phrase
     */
    public static String titleCase(String name) {
        StringBuilder ret = new StringBuilder();
        for (String word : StringUtils.splitByCharacterTypeCamelCase(name)) {
            if (!ret.isEmpty()) {
                ret.append(' ');
            }
            ret.append(StringUtils.capitalize(word));
        }
        return ret.toString();
    }

    /**
     * Converts a camel case identifier to a sentence: the words split apart with only the first capitalized,
     * so {@code createApplicationIfNotExist} becomes {@code "Create application if not exist"}. An acronym
     * keeps its case ({@code findByCRI} becomes {@code "Find by CRI"}).
     *
     * @param name the identifier to humanize
     * @return the identifier as a sentence
     */
    public static String sentenceCase(String name) {
        StringBuilder ret = new StringBuilder();
        for (String word : StringUtils.splitByCharacterTypeCamelCase(name)) {
            if (ret.isEmpty()) {
                ret.append(StringUtils.capitalize(word));
            } else {
                ret.append(' ');
                // an all-caps word is an acronym (CRI, OIDC) and keeps its case
                ret.append(StringUtils.isAllUpperCase(word) ? word : StringUtils.uncapitalize(word));
            }
        }
        return ret.toString();
    }
}
