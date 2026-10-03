package org.kinotic.idl.internal.directory;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.ComplexC3Type;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.ParameterDefinition;
import org.kinotic.idl.api.schema.PropertyDefinition;
import org.kinotic.idl.api.schema.ReferenceC3Type;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the authorization declarations of a service while its contract is created: the
 * {@link AuthzResourceC3Decorator} of the service and one {@link AuthzCheckC3Decorator} per function, from the
 * {@link AuthzResource} and {@link AuthzCheck} annotations and the function's converted parameters. A function
 * that derives no check and declares none fails the conversion, so a resource service never serves an
 * unchecked function.
 */
final class AuthzDecorators {

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
    static AuthzResourceC3Decorator resourceOf(Class<?> serviceInterface) {
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
     * The check of one function of a resource service.
     *
     * @param serviceInterface  the {@code @Publish} interface, named in errors
     * @param resource          the service's resource decorator
     * @param functionName      the function's name, whose leading verb derives the permission
     * @param specificMethod    the implementation's most specific method, which carries the {@link AuthzCheck}
     * @param parameters        the function's converted parameters, the ones a request carries
     * @param conversionContext the context the parameters were converted in, which resolves their references
     * @throws IllegalStateException when the function derives no check and declares none, or a declaration
     *                               references a parameter the function does not have
     */
    static AuthzCheckC3Decorator checkOf(Class<?> serviceInterface,
                                         AuthzResourceC3Decorator resource,
                                         String functionName,
                                         Method specificMethod,
                                         List<ParameterDefinition> parameters,
                                         ConversionContext conversionContext) {
        String type = resource.getResourceType();
        String parent = resource.getParent();
        String where = functionName + " on " + serviceInterface.getName();
        AuthzCheck declared = AnnotationUtils.findAnnotation(specificMethod, AuthzCheck.class);

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
            objectId = explicitObjectId ? declared.objectId()
                    : objectIdOf(checkedResource, type, parent, parameters, conversionContext);
            // a check made on the parent is about this type within it, as a derived create or listing is;
            // any other explicit resource is a permission of that resource itself
            permissionResource = checkedResource.equals(parent) ? type : checkedResource;
        } else if (CREATE_VERB.equals(leadingWord(functionName))) {
            checkedResource = requireParent(parent, where, "a create");
            objectId = parentId(parent, parameters, conversionContext);
            permissionResource = type;
        } else {
            String resourceId = resourceId(type, parameters, conversionContext);
            if (resourceId != null) {
                checkedResource = type;
                objectId = resourceId;
            } else {
                // no resource of this type is named, so the check is on the collection within the parent
                checkedResource = requireParent(parent, where, "a function naming no resource id");
                objectId = parentId(parent, parameters, conversionContext);
            }
            permissionResource = type;
        }
        if (objectId == null) {
            throw new IllegalStateException("The function " + where + " names no object to check;"
                                                    + " declare one with @AuthzCheck(objectId = ...)");
        }

        for (String template : List.of(checkedResource, objectId)) {
            for (String reference : AuthzUtil.templateReferences(template)) {
                String parameterName = AuthzUtil.referencedParameter(reference);
                if (parameterName == null) {
                    if (!SCOPE_REFERENCES.contains(reference)) {
                        throw new IllegalStateException("The template '" + template + "' of " + where
                                                                + " references the unknown scope value '" + reference + "'");
                    }
                } else if (parameter(parameters, parameterName) == null) {
                    throw new IllegalStateException("The template '" + template + "' of " + where
                                                            + " references '" + parameterName
                                                            + "', which is not a parameter the request carries");
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
                .setImplies(implies);
    }

    /**
     * The derived id of an object of {@code resource}: the platform's fixed id, the parent's id when the resource
     * is the parent, else a resource of the service's own type among the parameters.
     */
    private static String objectIdOf(String resource,
                                     String type,
                                     String parent,
                                     List<ParameterDefinition> parameters,
                                     ConversionContext conversionContext) {
        String ret;
        if (AuthzUtil.PLATFORM_TYPE.equals(resource)) {
            ret = AuthzUtil.PLATFORM_OBJECT_ID;
        } else if (resource.equals(parent)) {
            ret = parentId(parent, parameters, conversionContext);
        } else {
            ret = resourceId(type, parameters, conversionContext);
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
     * The template naming a resource of the service's own type among the parameters: the string parameter named
     * {@code id} or {@code <type>Id}, else the {@code id} property of the first object parameter that has one;
     * null when none does.
     */
    private static String resourceId(String type, List<ParameterDefinition> parameters, ConversionContext conversionContext) {
        String ret = null;
        String idName = camelCase(type) + "Id";
        for (ParameterDefinition parameter : parameters) {
            if (parameter.getType() instanceof StringC3Type
                    && (parameter.getName().equals("id") || parameter.getName().equals(idName))) {
                ret = "{" + parameter.getName() + "}";
                break;
            }
        }
        if (ret == null) {
            ret = propertyReference(parameters, "id", conversionContext);
        }
        return ret;
    }

    /**
     * The template naming the parent resource: the string parameter named {@code <parent>Id}, else that property
     * of the first object parameter that has it, else the caller's own scope for a parent the scope carries, else
     * the platform's fixed id for a parent that is the platform.
     */
    private static String parentId(String parent, List<ParameterDefinition> parameters, ConversionContext conversionContext) {
        String idName = camelCase(parent) + "Id";
        String ret = null;
        for (ParameterDefinition parameter : parameters) {
            if (parameter.getType() instanceof StringC3Type && parameter.getName().equals(idName)) {
                ret = "{" + parameter.getName() + "}";
                break;
            }
        }
        if (ret == null) {
            ret = propertyReference(parameters, idName, conversionContext);
        }
        if (ret == null && SCOPED_PARENTS.contains(parent)) {
            ret = "{" + AuthzUtil.SCOPE_REFERENCE_PREFIX + idName + "}";
        }
        if (ret == null && AuthzUtil.PLATFORM_TYPE.equals(parent)) {
            ret = AuthzUtil.PLATFORM_OBJECT_ID;
        }
        return ret;
    }

    /**
     * The template reaching the named property of the first object parameter that has it, or null.
     */
    private static String propertyReference(List<ParameterDefinition> parameters,
                                            String property,
                                            ConversionContext conversionContext) {
        String ret = null;
        for (ParameterDefinition parameter : parameters) {
            ObjectC3Type object = objectOf(parameter.getType(), conversionContext);
            if (object != null && hasProperty(object, property)) {
                ret = "{" + parameter.getName() + "." + property + "}";
                break;
            }
        }
        return ret;
    }

    /**
     * The object type a parameter converted to, resolving the reference the conversion context minted for it;
     * null for a parameter of any other kind.
     */
    private static ObjectC3Type objectOf(C3Type type, ConversionContext conversionContext) {
        ObjectC3Type ret = null;
        if (type instanceof ObjectC3Type object) {
            ret = object;
        } else if (type instanceof ReferenceC3Type reference) {
            for (ComplexC3Type complexType : conversionContext.getComplexC3Types()) {
                if (complexType instanceof ObjectC3Type object
                        && object.getQualifiedName().equals(reference.getQualifiedName())) {
                    ret = object;
                    break;
                }
            }
        }
        return ret;
    }

    private static boolean hasProperty(ObjectC3Type object, String name) {
        boolean ret = false;
        for (ObjectC3Type current = object; current != null && !ret; current = current.getParent()) {
            for (PropertyDefinition property : current.getProperties()) {
                if (property.getName().equals(name)) {
                    ret = true;
                    break;
                }
            }
        }
        return ret;
    }

    private static ParameterDefinition parameter(List<ParameterDefinition> parameters, String name) {
        ParameterDefinition ret = null;
        for (ParameterDefinition parameter : parameters) {
            if (parameter.getName().equals(name)) {
                ret = parameter;
                break;
            }
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
