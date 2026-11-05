package org.kinotic.idl.internal.directory;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.annotations.AuthzRole;
import org.kinotic.idl.api.annotations.AuthzUnchecked;
import org.kinotic.idl.api.directory.AuthzPermissionResolver;
import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.schema.AnyC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.ComplexC3Type;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.ParameterDefinition;
import org.kinotic.idl.api.schema.PropertyDefinition;
import org.kinotic.idl.api.schema.ReferenceC3Type;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzPermissionChoice;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzRoleDeclaration;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the authorization declarations of a service while its definition is created: the
 * {@link AuthzResourceC3Decorator} of the service and one {@link AuthzCheckC3Decorator} per function, from
 * what the service declares, the {@link AuthzResource}, {@link AuthzCheck} and {@link AuthzUnchecked} annotations of a {@code @Publish}
 * interface or the decorators a runtime's definition declares, and the function's parameters. A function that
 * derives no check and declares none fails the conversion, so a resource service never serves an unchecked
 * function.
 */
final class AuthzDecorators {

    private static final Pattern LEADING_WORD = Pattern.compile("^[a-z]+");
    private static final Set<String> READ_VERBS = Set.of("find", "get", "count", "search", "list");
    private static final Set<String> EDIT_VERBS = Set.of("save", "update", "set");
    private static final Set<String> DELETE_VERBS = Set.of("delete", "remove");
    private static final String CREATE_VERB = "create";
    private static final Set<String> SCOPED_PARENTS = Set.of(AuthzConstants.ORGANIZATION_TYPE, AuthzConstants.APPLICATION_TYPE,
                                                             AuthzConstants.TENANT_TYPE);

    private AuthzDecorators() {
    }

    /**
     * The resource decorator of a service, or null when the service declares no {@link AuthzResource}.
     *
     * @throws IllegalStateException as {@link #resourceOf(String, AuthzResourceC3Decorator)} does
     */
    static AuthzResourceC3Decorator resourceOf(Class<?> serviceInterface) {
        AuthzResource resource = AnnotationUtils.findAnnotation(serviceInterface, AuthzResource.class);
        return resource == null ? null : resourceOf(serviceInterface.getName(), declarationOf(resource));
    }

    /**
     * Whether a service declaring no resource marks every function unchecked with {@link AuthzUnchecked}.
     *
     * @throws IllegalStateException when the service declares a resource too
     */
    static boolean uncheckedService(Class<?> serviceInterface) {
        boolean ret = AnnotationUtils.findAnnotation(serviceInterface, AuthzUnchecked.class) != null;
        if (ret && AnnotationUtils.findAnnotation(serviceInterface, AuthzResource.class) != null) {
            throw new IllegalStateException("@AuthzUnchecked on " + serviceInterface.getName()
                                                    + " marks every function unchecked beside the @AuthzResource its functions are checked on;"
                                                    + " mark the functions instead");
        }
        return ret;
    }

    /**
     * Whether a function declares anything about its check: an {@link AuthzCheck} or an {@link AuthzUnchecked}
     * of its own, which a service declaring no resource has no place for.
     */
    static boolean declaresCheck(Method interfaceMethod) {
        return AnnotationUtils.findAnnotation(interfaceMethod, AuthzCheck.class) != null
                || AnnotationUtils.findAnnotation(interfaceMethod, AuthzUnchecked.class) != null;
    }

    /**
     * The mark a contract carries for a function served unchecked, which tells it from a function no contract
     * covers.
     */
    static AuthzCheckC3Decorator unchecked() {
        return new AuthzCheckC3Decorator().setUnchecked(true);
    }

    /**
     * The mark of a function declared unchecked, which declares nothing else about its check.
     *
     * @param where    the function, named in errors
     * @param declared the declaration carrying the mark
     * @throws IllegalStateException when the declaration carries a check beside the mark
     */
    static AuthzCheckC3Decorator uncheckedOf(String where, AuthzCheckC3Decorator declared) {
        List<String> implies = declared.getImplies() == null ? List.of() : declared.getImplies();
        if (present(declared.getPermission()) != null || present(declared.getResource()) != null
                || present(declared.getResourceId()) != null || present(declared.getPermissionResolver()) != null
                || !implies.isEmpty() || declared.isConsistent()) {
            throw new IllegalStateException("The function " + where + " is declared unchecked beside a check;"
                                                    + " an unchecked function has none");
        }
        return unchecked();
    }

    /**
     * The resource decorator a service's declaration resolves to.
     *
     * @param declarer the service, named in errors
     * @param declared what it declares
     * @throws IllegalStateException when the declared type, parent or permission is not an identifier, a
     *                               declared role is not named after the type or bundles nothing, or a service
     *                               of another type than {@code entity_definition} is declared acting across
     *                               tenants
     */
    static AuthzResourceC3Decorator resourceOf(String declarer, AuthzResourceC3Decorator declared) {
        String where = "@AuthzResource on " + declarer;
        String type = declared.getResourceType();
        List<AuthzRoleDeclaration> declaredRoles = declared.getRoles() == null ? List.of() : declared.getRoles();
        if (!AuthzUtil.isIdentifier(type)) {
            throw new IllegalStateException(where + " names the type '" + type + "', which is not a lowercase identifier");
        }
        String parent = present(declared.getParent());
        if (parent != null && !AuthzUtil.isIdentifier(parent)) {
            throw new IllegalStateException(where + " names the parent '" + parent + "', which is not a lowercase identifier");
        }
        String permission = present(declared.getPermission());
        if (permission != null && !AuthzUtil.isIdentifier(permission)) {
            throw new IllegalStateException(where + " names the permission '" + permission + "', which is not a lowercase identifier");
        }
        List<AuthzRoleDeclaration> roles = new ArrayList<>();
        for (AuthzRoleDeclaration role : declaredRoles) {
            roles.add(roleOf(declarer, type, role));
        }
        // only a definition's rows are checked within a tenant, so only a service of that type acts across them
        if (declared.isAcrossTenants() && !AuthzConstants.ENTITY_DEFINITION_TYPE.equals(type)) {
            throw new IllegalStateException(where + " acts across tenants, which only a service of the type '"
                                                    + AuthzConstants.ENTITY_DEFINITION_TYPE + "' does");
        }
        return new AuthzResourceC3Decorator()
                .setResourceType(type)
                .setParent(parent)
                .setResourceId(present(declared.getResourceId()))
                .setPermission(permission)
                .setRoles(List.copyOf(roles))
                .setAcrossTenants(declared.isAcrossTenants());
    }

    private static AuthzResourceC3Decorator declarationOf(AuthzResource resource) {
        List<AuthzRoleDeclaration> roles = new ArrayList<>();
        for (AuthzRole role : resource.roles()) {
            roles.add(new AuthzRoleDeclaration().setId(role.id()).setPermissions(List.of(role.permissions())));
        }
        return new AuthzResourceC3Decorator()
                .setResourceType(resource.value())
                .setParent(resource.parent())
                .setResourceId(resource.resourceId())
                .setPermission(resource.permission())
                .setRoles(roles)
                .setAcrossTenants(resource.acrossTenants());
    }

    private static AuthzCheckC3Decorator declarationOf(AuthzCheck check) {
        return new AuthzCheckC3Decorator()
                .setPermission(check.permission())
                .setResource(check.resource())
                .setResourceId(check.resourceId())
                .setImplies(List.of(check.implies()))
                .setPermissionResolver(check.permissionResolver())
                .setConsistent(check.consistent());
    }

    // A declared value: null for one left out, which a declaration from an annotation leaves empty
    private static String present(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    // A declared role is named <type>.<level> as a built-in role is, and bundles at least one permission; whether
    // the permissions exist on the type is known only once every service's checks are, so the generator decides it
    private static AuthzRoleDeclaration roleOf(String declarer, String type, AuthzRoleDeclaration role) {
        String where = "@AuthzRole '" + role.getId() + "' on " + declarer;
        String prefix = type + ".";
        if (role.getId() == null || !role.getId().startsWith(prefix) || !AuthzUtil.isIdentifier(role.getId().substring(prefix.length()))) {
            throw new IllegalStateException(where + " is not named '" + prefix + "<level>' with a lowercase identifier as the level");
        }
        if (role.getPermissions().isEmpty()) {
            throw new IllegalStateException(where + " bundles no permission");
        }
        for (String permission : role.getPermissions()) {
            if (!AuthzUtil.isIdentifier(permission)) {
                throw new IllegalStateException(where + " bundles '" + permission + "', which is not a lowercase identifier");
            }
        }
        return new AuthzRoleDeclaration().setId(role.getId()).setPermissions(List.copyOf(role.getPermissions()));
    }

    /**
     * The check of one function of a resource service, or the unchecked mark for a function declared unchecked.
     *
     * @param serviceInterface  the {@code @Publish} interface, named in errors
     * @param resource          the service's resource decorator
     * @param functionName      the function's name, whose leading verb derives the permission
     * @param interfaceMethod   the interface's most specific declaration of the function, which carries the
     *                          {@link AuthzCheck} or {@link AuthzUnchecked}, its own or one inherited from a
     *                          super interface
     * @param parameters        the function's converted parameters, the ones a request carries
     * @param conversionContext the context the parameters were converted in, which resolves their references
     * @param resolvers         the permission resolvers a check may name, by name
     * @throws IllegalStateException as {@link #checkOf(String, AuthzResourceC3Decorator, String, AuthzCheckC3Decorator, List, ConversionContext, Map)} does
     */
    static AuthzCheckC3Decorator checkOf(Class<?> serviceInterface,
                                         AuthzResourceC3Decorator resource,
                                         String functionName,
                                         Method interfaceMethod,
                                         List<ParameterDefinition> parameters,
                                         ConversionContext conversionContext,
                                         Map<String, AuthzPermissionResolver> resolvers) {
        // the check is the interface's: its redeclaration of an inherited function carries it, which
        // the implementation's method, inherited from a base class outside that interface, would not reach
        AuthzCheck check = AnnotationUtils.findAnnotation(interfaceMethod, AuthzCheck.class);
        AuthzCheckC3Decorator declared = check == null ? null : declarationOf(check);
        if (AnnotationUtils.findAnnotation(interfaceMethod, AuthzUnchecked.class) != null) {
            declared = (declared == null ? new AuthzCheckC3Decorator() : declared).setUnchecked(true);
        }
        return checkOf(serviceInterface.getName(), resource, functionName, declared, parameters, conversionContext, resolvers);
    }

    /**
     * The check of one function of a resource service, or the unchecked mark for a function declared unchecked.
     *
     * @param declarer          the service, named in errors
     * @param resource          the service's resource decorator
     * @param functionName      the function's name, whose leading verb derives the permission
     * @param declared          what the function declares about its check, or null for nothing
     * @param parameters        the function's parameters, the ones a request carries
     * @param conversionContext the context the parameters were converted in, which resolves their references
     * @param resolvers         the permission resolvers a check may name, by name
     * @throws IllegalStateException when the function derives no check and declares none, a declaration
     *                               references a parameter the function does not have, or it names a permission
     *                               resolver beside a permission, one not in {@code resolvers}, one requiring
     *                               no permission, or one reading a parameter the function does not have
     */
    static AuthzCheckC3Decorator checkOf(String declarer,
                                         AuthzResourceC3Decorator resource,
                                         String functionName,
                                         AuthzCheckC3Decorator declared,
                                         List<ParameterDefinition> parameters,
                                         ConversionContext conversionContext,
                                         Map<String, AuthzPermissionResolver> resolvers) {
        String where = functionName + " on " + declarer;
        AuthzCheckC3Decorator ret;
        if (declared != null && declared.isUnchecked()) {
            ret = uncheckedOf(where, declared);
        } else {
            String permissionResolver = declared == null ? null : present(declared.getPermissionResolver());
            List<String> implies = declared == null || declared.getImplies() == null ? List.of() : declared.getImplies();
            List<AuthzPermissionChoice> possiblePermissions = List.of();
            if (permissionResolver != null) {
                if (present(declared.getPermission()) != null || !implies.isEmpty()) {
                    throw new IllegalStateException("The function " + where + " declares a permission resolver beside a permission;"
                                                            + " the resolver decides the permission");
                }
                possiblePermissions = possiblePermissionsOf(where, resolvers.get(permissionResolver), permissionResolver, parameters);
            }
            // a resolver decides the permission per request
            String permission = permissionResolver == null ? permissionOf(where, resource, functionName, declared) : null;
            CheckedResource checked = checkedResourceOf(where, resource, functionName, declared, parameters, conversionContext);
            requireKnownReferences(where, checked.resourceId(), parameters);
            for (String implied : implies) {
                if (!AuthzUtil.isIdentifier(implied)) {
                    throw new IllegalStateException("The implied permission '" + implied + "' of " + where
                                                            + " is not a lowercase identifier");
                }
            }
            ret = new AuthzCheckC3Decorator()
                    .setResource(checked.resource())
                    .setResourceId(checked.resourceId())
                    .setPermissionResource(checked.permissionResource())
                    .setPermission(permission)
                    .setImplies(implies)
                    .setPermissionResolver(permissionResolver)
                    .setPossiblePermissions(possiblePermissions)
                    .setConsistent(declared != null && declared.isConsistent())
                    .setAcrossTenants(resource.isAcrossTenants());
        }
        return ret;
    }

    // The function's own permission, else the one its service requires of every function, else its verb's
    private static String permissionOf(String where, AuthzResourceC3Decorator resource, String functionName, AuthzCheckC3Decorator declared) {
        String ret = declared == null ? null : present(declared.getPermission());
        if (ret == null) {
            ret = resource.getPermission() != null ? resource.getPermission() : derivedPermission(functionName);
        }
        if (ret == null) {
            throw new IllegalStateException("The function " + where + " derives no permission from its name;"
                                                    + " declare one with @AuthzCheck(permission = ...)");
        }
        if (!AuthzUtil.isIdentifier(ret)) {
            throw new IllegalStateException("The permission '" + ret + "' of " + where + " is not a lowercase identifier");
        }
        return ret;
    }

    // The resource a function is checked on, from what it declares, else the object its service names, else the
    // parent for a create or a function naming no resource of the service's type, else that resource
    private static CheckedResource checkedResourceOf(String where,
                                                     AuthzResourceC3Decorator resource,
                                                     String functionName,
                                                     AuthzCheckC3Decorator declared,
                                                     List<ParameterDefinition> parameters,
                                                     ConversionContext conversionContext) {
        String type = resource.getResourceType();
        String parent = resource.getParent();
        String declaredResource = declared == null ? null : present(declared.getResource());
        String declaredResourceId = declared == null ? null : present(declared.getResourceId());
        CheckedResource ret;
        if (declaredResource != null || declaredResourceId != null) {
            String checked = declaredResource != null ? declaredResource : type;
            String resourceId = declaredResourceId != null ? declaredResourceId
                    : resourceIdOf(checked, type, parent, parameters, conversionContext);
            // a check made on the parent is about this type within it, as a derived create or listing is;
            // any other explicit resource is a permission of that resource itself
            ret = new CheckedResource(checked, resourceId, checked.equals(parent) ? type : checked);
        } else if (resource.getResourceId() != null) {
            // the service names the object its functions act on; what a function's arguments carry, a role, a
            // member, a machine, is what it acts with, not a resource of the service's type
            ret = new CheckedResource(type, resource.getResourceId(), type);
        } else if (CREATE_VERB.equals(leadingWord(functionName))) {
            ret = new CheckedResource(requireParent(parent, where, "a create"), parentId(parent, parameters, conversionContext), type);
        } else {
            String ownId = resourceId(type, parameters, conversionContext);
            if (ownId != null) {
                ret = new CheckedResource(type, ownId, type);
            } else {
                // no resource of this type is named, so the check is on the collection within the parent
                ret = new CheckedResource(requireParent(parent, where, "a function naming no resource id"),
                                          parentId(parent, parameters, conversionContext),
                                          type);
            }
        }
        if (ret.resourceId() == null) {
            throw new IllegalStateException("The function " + where + " names no resource to check;"
                                                    + " declare one with @AuthzCheck(resourceId = ...)");
        }
        // a type is a constant: which resource of it a function acts on is named by the id alone
        if (!AuthzUtil.isIdentifier(ret.resource())) {
            throw new IllegalStateException("The checked resource's type '" + ret.resource() + "' of " + where
                                                    + " is not a lowercase identifier");
        }
        return ret;
    }

    // Every reference of the resource id names the caller's scope or a parameter the request carries
    private static void requireKnownReferences(String where, String resourceId, List<ParameterDefinition> parameters) {
        for (String reference : AuthzUtil.templateReferences(resourceId)) {
            String parameterName = AuthzUtil.referencedParameter(reference);
            if (parameterName == null) {
                if (!AuthzConstants.SCOPE_REFERENCES.contains(reference)) {
                    throw new IllegalStateException("The template '" + resourceId + "' of " + where
                                                            + " references the unknown scope value '" + reference + "'");
                }
            } else if (parameter(parameters, parameterName) == null) {
                throw new IllegalStateException("The template '" + resourceId + "' of " + where
                                                        + " references '" + parameterName
                                                        + "', which is not a parameter the request carries");
            }
        }
    }

    // The permissions the named resolver may require, once the function carries every parameter it reads
    private static List<AuthzPermissionChoice> possiblePermissionsOf(String where,
                                                                     AuthzPermissionResolver resolver,
                                                                     String name,
                                                                     List<ParameterDefinition> parameters) {
        if (resolver == null) {
            throw new IllegalStateException("The function " + where + " names the permission resolver '" + name
                                                    + "', but no resolver has that name");
        }
        List<AuthzPermissionChoice> ret = List.copyOf(resolver.possiblePermissions());
        if (ret.isEmpty()) {
            throw new IllegalStateException("The permission resolver '" + name + "' of " + where + " requires no permission");
        }
        for (AuthzPermissionChoice choice : ret) {
            if (!AuthzUtil.isIdentifier(choice.getPermission())) {
                throw new IllegalStateException("The permission '" + choice.getPermission() + "' the resolver '" + name
                                                        + "' of " + where + " may require is not a lowercase identifier");
            }
        }
        for (String parameterName : resolver.parameters()) {
            if (parameter(parameters, parameterName) == null) {
                throw new IllegalStateException("The permission resolver '" + name + "' of " + where + " reads '" + parameterName
                                                        + "', which is not a parameter the request carries");
            }
        }
        return ret;
    }

    /**
     * The derived id of the checked resource of type {@code resource}: the system's fixed id, the parent's id
     * when the resource is the parent, else a resource of the service's own type among the parameters.
     */
    private static String resourceIdOf(String resource,
                                     String type,
                                     String parent,
                                     List<ParameterDefinition> parameters,
                                     ConversionContext conversionContext) {
        String ret;
        if (AuthzConstants.SYSTEM_TYPE.equals(resource)) {
            ret = AuthzConstants.SYSTEM_OBJECT_ID;
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
        if (READ_VERBS.contains(verb)) {
            ret = AuthzConstants.CAN_READ;
        } else if (EDIT_VERBS.contains(verb) || CREATE_VERB.equals(verb)) {
            ret = AuthzConstants.CAN_EDIT;
        } else if (DELETE_VERBS.contains(verb)) {
            ret = AuthzConstants.CAN_DELETE;
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
     * The template naming a resource of the service's own type among the parameters: the id parameter named
     * {@code id} or {@code <type>Id}, else the {@code id} property of the first object parameter that has one;
     * null when none does.
     */
    private static String resourceId(String type, List<ParameterDefinition> parameters, ConversionContext conversionContext) {
        String ret = null;
        String idName = camelCase(type) + "Id";
        for (ParameterDefinition parameter : parameters) {
            if (isId(parameter) && (parameter.getName().equals("id") || parameter.getName().equals(idName))) {
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
     * The template naming the parent resource: the id parameter named {@code <parent>Id}, else that property
     * of the first object parameter that has it, else the caller's own scope for a parent the scope carries, else
     * the system's fixed id for a parent that is the system.
     */
    private static String parentId(String parent, List<ParameterDefinition> parameters, ConversionContext conversionContext) {
        String idName = camelCase(parent) + "Id";
        String ret = null;
        for (ParameterDefinition parameter : parameters) {
            if (isId(parameter) && parameter.getName().equals(idName)) {
                ret = "{" + parameter.getName() + "}";
                break;
            }
        }
        if (ret == null) {
            ret = propertyReference(parameters, idName, conversionContext);
        }
        if (ret == null && SCOPED_PARENTS.contains(parent)) {
            ret = "{" + AuthzConstants.SCOPE_REFERENCE_PREFIX + idName + "}";
        }
        if (ret == null && AuthzConstants.SYSTEM_TYPE.equals(parent)) {
            ret = AuthzConstants.SYSTEM_OBJECT_ID;
        }
        return ret;
    }

    // A parameter an id can be: a string, or one a runtime's definition leaves untyped
    private static boolean isId(ParameterDefinition parameter) {
        return parameter.getType() instanceof StringC3Type || parameter.getType() instanceof AnyC3Type;
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
