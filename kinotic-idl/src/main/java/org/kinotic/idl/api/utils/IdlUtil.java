package org.kinotic.idl.api.utils;

import org.apache.commons.lang3.StringUtils;
import org.kinotic.idl.api.annotations.Name;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static helpers shared across IDL conversion and its consumers.
 */
public final class IdlUtil {

    private IdlUtil() {
    }

    /**
     * Resolves the functions a service interface declares: every user-declared method keyed by function name.
     * A method that overrides an inherited one contributes a single function, resolved against the most
     * specific declaration. Schema generation and service registration must both use this rule, so a published
     * schema never carries a function the registry does not serve.
     *
     * @param serviceInterface the service interface to introspect
     * @return the interface's functions keyed by name
     * @throws IllegalStateException if the interface overloads a function name, which a service cannot express
     *                               because a function is addressed by name alone
     */
    public static Map<String, Method> serviceFunctions(Class<?> serviceInterface) {
        Map<String, Method> ret = new LinkedHashMap<>();
        // doWithMethods walks the interface's own methods before its super interfaces, so the first method
        // kept for a name is the most specific declaration
        ReflectionUtils.doWithMethods(serviceInterface, method -> {
            Method existing = ret.putIfAbsent(method.getName(), method);
            // Class.getDeclaredMethods promises no order, so keeping either signature would let a recompile
            // silently swap which one the schema advertises and the registry serves
            if (existing != null && !sameSignature(existing, method, serviceInterface)) {
                throw new IllegalStateException(serviceInterface.getName() + " overloads function "
                                                        + method.getName()
                                                        + ", which is not supported because a function is addressed by name alone."
                                                        + "\n " + existing.toGenericString()
                                                        + "\n " + method.toGenericString());
            }
        }, ReflectionUtils.USER_DECLARED_METHODS);
        return ret;
    }

    /**
     * Whether two same named methods are the same function of the given service interface, rather than an
     * overload of one another.
     */
    private static boolean sameSignature(Method first, Method second, Class<?> serviceInterface) {
        boolean ret = first.getParameterCount() == second.getParameterCount();
        // both sides bind their type variables against serviceInterface, so an override that substitutes them
        // (create(T) redeclared as create(EntityDefinition)) compares equal, as does a method visited twice
        for (int i = 0; ret && i < first.getParameterCount(); i++) {
            ret = ResolvableType.forMethodParameter(first, i, serviceInterface).toClass()
                                .equals(ResolvableType.forMethodParameter(second, i, serviceInterface).toClass());
        }
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
