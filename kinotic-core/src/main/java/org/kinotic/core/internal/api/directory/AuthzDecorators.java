package org.kinotic.core.internal.api.directory;

import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.core.api.security.Participant;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.idl.api.utils.IdlUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the authorization declarations of a published service, its {@link AuthzResourceC3Decorator} and one
 * {@link AuthzCheckC3Decorator} per function, from its {@link AuthzResource} and {@link AuthzCheck} annotations
 * and the shape of its functions. Service registration validates with it, so a resource service whose
 * declarations do not resolve never starts, and the service directory publishes what it derives.
 */
public final class AuthzDecorators {

    private static final Pattern LEADING_WORD = Pattern.compile("^[a-z]+");
    private static final Set<String> VIEW_VERBS = Set.of("find", "get", "count", "search", "list");
    private static final Set<String> EDIT_VERBS = Set.of("save", "update", "set");
    private static final Set<String> DELETE_VERBS = Set.of("delete", "remove");
    private static final String CREATE_VERB = "create";
    private static final Set<String> SCOPE_REFERENCES = Set.of("@organizationId", "@applicationId", "@tenantId");
    private static final Set<String> SCOPED_PARENTS = Set.of("organization", "application", "tenant");

    private AuthzDecorators() {
    }

    /**
     * The resource decorator of a service, or null when the service declares no {@link AuthzResource}.
     *
     * @throws IllegalStateException when the declared type or parent is not an identifier
     */
    public static AuthzResourceC3Decorator resourceOf(Class<?> serviceInterface) {
        AuthzResource resource = AnnotationUtils.findAnnotation(serviceInterface, AuthzResource.class);
        AuthzResourceC3Decorator ret = null;
        if (resource != null) {
            if (!AuthzUtil.isIdentifier(resource.value())) {
                throw new IllegalStateException("@AuthzResource on " + serviceInterface.getName()
                                                        + " names the type '" + resource.value()
                                                        + "', which is not a lowercase identifier");
            }
            if (!resource.parent().isEmpty() && !AuthzUtil.isIdentifier(resource.parent())) {
                throw new IllegalStateException("@AuthzResource on " + serviceInterface.getName()
                                                        + " names the parent '" + resource.parent()
                                                        + "', which is not a lowercase identifier");
            }
            ret = new AuthzResourceC3Decorator()
                    .setResourceType(resource.value())
                    .setParent(resource.parent().isEmpty() ? null : resource.parent());
        }
        return ret;
    }

    /**
     * The check of every function of a resource service, keyed by function name. A service that declares no
     * {@link AuthzResource} has none.
     *
     * @param serviceInterface the {@code @Publish} interface
     * @param implementation   the implementing class, whose overrides may carry the {@link AuthzCheck}
     * @throws IllegalStateException when a function derives no check and declares none, or a declaration
     *                               references a parameter the function does not have
     */
    public static Map<String, AuthzCheckC3Decorator> checksOf(Class<?> serviceInterface, Class<?> implementation) {
        Map<String, AuthzCheckC3Decorator> ret = new LinkedHashMap<>();
        AuthzResourceC3Decorator resource = resourceOf(serviceInterface);
        if (resource != null) {
            for (Map.Entry<String, Method> function : IdlUtil.serviceFunctions(serviceInterface).entrySet()) {
                // the override decides annotations, as DefaultSchemaFactory reads @McpTool, so a check declared on
                // an inherited CRUD function's override is honored
                Method specificMethod = BridgeMethodResolver.findBridgedMethod(
                        ClassUtils.getMostSpecificMethod(function.getValue(), implementation));
                ret.put(function.getKey(), checkOf(serviceInterface, resource, function.getValue(), specificMethod));
            }
        }
        return ret;
    }

    /**
     * Attaches the derived decorators to the converted definition of a resource service, so the directory
     * carries them; a service that declares no resource is left as it is.
     */
    public static void apply(Class<?> serviceInterface, Class<?> implementation, ServiceDefinition definition) {
        AuthzResourceC3Decorator resource = resourceOf(serviceInterface);
        if (resource != null) {
            definition.setDecorators(withDecorator(definition.getDecorators(), resource));
            Map<String, AuthzCheckC3Decorator> checks = checksOf(serviceInterface, implementation);
            for (FunctionDefinition function : definition.getFunctions()) {
                AuthzCheckC3Decorator check = checks.get(function.getName());
                if (check != null) {
                    function.setDecorators(withDecorator(function.getDecorators(), check));
                }
            }
        }
    }

    private static List<C3Decorator> withDecorator(List<C3Decorator> decorators, C3Decorator decorator) {
        List<C3Decorator> ret = decorators == null ? new ArrayList<>() : new ArrayList<>(decorators);
        ret.add(decorator);
        return ret;
    }

    private static AuthzCheckC3Decorator checkOf(Class<?> serviceInterface,
                                                 AuthzResourceC3Decorator resource,
                                                 Method interfaceMethod,
                                                 Method specificMethod) {
        String type = resource.getResourceType();
        String parent = resource.getParent();
        String functionName = interfaceMethod.getName();
        String where = functionName + " on " + serviceInterface.getName();
        AuthzCheck declared = AnnotationUtils.findAnnotation(specificMethod, AuthzCheck.class);
        List<FunctionParameter> parameters = parametersOf(interfaceMethod, specificMethod);

        String permission = declared != null && !declared.permission().isEmpty()
                ? declared.permission() : derivedPermission(functionName);
        if (permission == null) {
            throw new IllegalStateException("The function " + where + " derives no permission from its name;"
                                                    + " declare one with @AuthzCheck(permission = ...)");
        }
        if (!AuthzUtil.isIdentifier(permission)) {
            throw new IllegalStateException("The permission '" + permission + "' of " + where
                                                    + " is not a lowercase identifier");
        }

        String checkedResource;
        String objectId;
        String permissionResource;
        boolean explicitResource = declared != null && !declared.resource().isEmpty();
        boolean explicitObjectId = declared != null && !declared.objectId().isEmpty();
        if (explicitResource || explicitObjectId) {
            checkedResource = explicitResource ? declared.resource() : type;
            objectId = explicitObjectId ? declared.objectId() : objectIdOf(checkedResource, type, parent, parameters);
            // a check made on the parent is about this type within it, as a derived create or listing is;
            // any other explicit resource is a permission of that resource itself
            permissionResource = checkedResource.equals(parent) ? type : checkedResource;
        } else if (CREATE_VERB.equals(leadingWord(functionName))) {
            checkedResource = requireParent(parent, where, "a create");
            objectId = parentId(parent, parameters);
            permissionResource = type;
        } else {
            String resourceId = resourceId(type, parameters);
            if (resourceId != null) {
                checkedResource = type;
                objectId = resourceId;
            } else {
                // no resource of this type is named, so the check is on the collection within the parent
                checkedResource = requireParent(parent, where, "a function naming no resource id");
                objectId = parentId(parent, parameters);
            }
            permissionResource = type;
        }
        if (objectId == null) {
            throw new IllegalStateException("The function " + where + " names no object to check;"
                                                    + " declare one with @AuthzCheck(objectId = ...)");
        }

        Map<String, Integer> bodyIndexes = new LinkedHashMap<>();
        for (String template : List.of(checkedResource, objectId)) {
            for (String reference : AuthzUtil.templateReferences(template)) {
                String parameterName = AuthzUtil.referencedParameter(reference);
                if (parameterName == null) {
                    if (!SCOPE_REFERENCES.contains(reference)) {
                        throw new IllegalStateException("The template '" + template + "' of " + where
                                                                + " references the unknown scope value '" + reference + "'");
                    }
                } else {
                    FunctionParameter parameter = bodyParameter(parameters, parameterName);
                    if (parameter == null) {
                        throw new IllegalStateException("The template '" + template + "' of " + where
                                                                + " references '" + parameterName
                                                                + "', which is not a parameter the request carries");
                    }
                    bodyIndexes.put(parameterName, parameter.bodyIndex());
                }
            }
        }

        List<String> implies = declared != null ? List.of(declared.implies()) : List.of();
        for (String implied : implies) {
            if (!AuthzUtil.isIdentifier(implied)) {
                throw new IllegalStateException("The implied permission '" + implied + "' of " + where
                                                        + " is not a lowercase identifier");
            }
        }

        return new AuthzCheckC3Decorator()
                .setResource(checkedResource)
                .setObjectId(objectId)
                .setPermissionResource(permissionResource)
                .setPermission(permission)
                .setImplies(implies)
                .setParameterBodyIndexes(bodyIndexes);
    }

    /**
     * The derived id of an object of {@code resource}: the platform's fixed id, the parent's id when the resource
     * is the parent, else a resource of the service's own type among the parameters.
     */
    private static String objectIdOf(String resource, String type, String parent, List<FunctionParameter> parameters) {
        String ret;
        if (AuthzUtil.PLATFORM_TYPE.equals(resource)) {
            ret = AuthzUtil.PLATFORM_OBJECT_ID;
        } else if (resource.equals(parent)) {
            ret = parentId(parent, parameters);
        } else {
            ret = resourceId(type, parameters);
        }
        return ret;
    }

    private static String requireParent(String parent, String where, String what) {
        if (parent == null) {
            throw new IllegalStateException("The function " + where + " is " + what
                                                    + ", which is checked on the parent, but its @AuthzResource declares none");
        }
        return parent;
    }

    private static String derivedPermission(String functionName) {
        String verb = leadingWord(functionName);
        String ret;
        if (VIEW_VERBS.contains(verb)) {
            ret = AuthzUtil.CAN_VIEW;
        } else if (EDIT_VERBS.contains(verb) || CREATE_VERB.equals(verb)) {
            ret = AuthzUtil.CAN_EDIT;
        } else if (DELETE_VERBS.contains(verb)) {
            ret = AuthzUtil.CAN_DELETE;
        } else {
            ret = null;
        }
        return ret;
    }

    private static String leadingWord(String functionName) {
        Matcher matcher = LEADING_WORD.matcher(functionName);
        return matcher.find() ? matcher.group() : "";
    }

    /**
     * The template naming a resource of the service's own type among the parameters: the {@code id} or
     * {@code <type>Id} parameter, else the id of the first {@link Identifiable} parameter; null when none does.
     */
    private static String resourceId(String type, List<FunctionParameter> parameters) {
        String ret = null;
        String idName = camelCase(type) + "Id";
        for (FunctionParameter parameter : parameters) {
            if (parameter.bodyIndex() >= 0 && parameter.type() == String.class
                    && (parameter.name().equals("id") || parameter.name().equals(idName))) {
                ret = "{" + parameter.name() + "}";
                break;
            }
        }
        if (ret == null) {
            for (FunctionParameter parameter : parameters) {
                if (parameter.bodyIndex() >= 0 && Identifiable.class.isAssignableFrom(parameter.type())) {
                    ret = "{" + parameter.name() + ".id}";
                    break;
                }
            }
        }
        return ret;
    }

    /**
     * The template naming the parent resource: the {@code <parent>Id} parameter, else that property of the
     * first object parameter that has it, else the caller's own scope for a parent the scope carries.
     */
    private static String parentId(String parent, List<FunctionParameter> parameters) {
        String idName = camelCase(parent) + "Id";
        String ret = null;
        for (FunctionParameter parameter : parameters) {
            if (parameter.bodyIndex() >= 0 && parameter.type() == String.class && parameter.name().equals(idName)) {
                ret = "{" + parameter.name() + "}";
                break;
            }
        }
        if (ret == null) {
            for (FunctionParameter parameter : parameters) {
                if (parameter.bodyIndex() >= 0 && hasReadableProperty(parameter.type(), idName)) {
                    ret = "{" + parameter.name() + "." + idName + "}";
                    break;
                }
            }
        }
        if (ret == null && SCOPED_PARENTS.contains(parent)) {
            ret = "{" + AuthzUtil.SCOPE_REFERENCE_PREFIX + idName + "}";
        }
        if (ret == null && AuthzUtil.PLATFORM_TYPE.equals(parent)) {
            ret = AuthzUtil.PLATFORM_OBJECT_ID;
        }
        return ret;
    }

    private static boolean hasReadableProperty(Class<?> type, String property) {
        boolean ret = false;
        if (!BeanUtils.isSimpleValueType(type)) {
            PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(type, property);
            ret = descriptor != null && descriptor.getReadMethod() != null;
        }
        return ret;
    }

    private static FunctionParameter bodyParameter(List<FunctionParameter> parameters, String name) {
        FunctionParameter ret = null;
        for (FunctionParameter parameter : parameters) {
            if (parameter.bodyIndex() >= 0 && parameter.name().equals(name)) {
                ret = parameter;
                break;
            }
        }
        return ret;
    }

    /**
     * The function's parameters with their request body positions: a {@link Participant} parameter is supplied
     * by the platform rather than the request, so it has none, exactly as the argument resolvers bind them.
     */
    private static List<FunctionParameter> parametersOf(Method interfaceMethod, Method specificMethod) {
        List<FunctionParameter> ret = new ArrayList<>();
        int bodyIndex = 0;
        for (int i = 0; i < interfaceMethod.getParameterCount(); i++) {
            // names come from the interface method, as the schema and named-argument binding read them
            String name = IdlUtil.parameterName(new MethodParameter(interfaceMethod, i));
            Class<?> type = specificMethod.getParameterTypes()[i];
            boolean fromRequest = !Participant.class.isAssignableFrom(type);
            ret.add(new FunctionParameter(name, type, fromRequest ? bodyIndex++ : -1));
        }
        return ret;
    }

    private static String camelCase(String snakeCase) {
        StringBuilder ret = new StringBuilder();
        boolean upper = false;
        for (char c : snakeCase.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                ret.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return ret.toString();
    }

}
