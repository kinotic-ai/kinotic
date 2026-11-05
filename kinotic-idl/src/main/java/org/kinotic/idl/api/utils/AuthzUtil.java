package org.kinotic.idl.api.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The naming rules shared by everything that reads or writes an authorization declaration: the derivation
 * that turns a function into its check, the generator that turns declarations into a model, and the gateway
 * that resolves a check against a request.
 */
public final class AuthzUtil {

    private static final Pattern TEMPLATE_REFERENCE = Pattern.compile("\\{([^{}]+)}");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern OBJECT_ID = Pattern.compile("[^#:\\s]+");

    private AuthzUtil() {
    }

    /**
     * An object in a store, {@code type:id}: a resource, an identity, a role or a binding.
     */
    public static String object(String type, String id) {
        return type + ":" + id;
    }

    /**
     * The type of an object or a userset in {@code type:id} or {@code type:id#relation} form.
     * @param object the object
     * @return its type
     */
    public static String typeOf(String object) {
        return object.substring(0, object.indexOf(':'));
    }

    /**
     * The id of an object or a userset in {@code type:id} or {@code type:id#relation} form.
     * @param object the object
     * @return its id
     */
    public static String idOf(String object) {
        int relation = object.indexOf('#');
        return object.substring(object.indexOf(':') + 1, relation < 0 ? object.length() : relation);
    }

    /**
     * The id of a built-in role of a type: {@code project.editor} for the editor of projects.
     */
    public static String roleId(String type, String level) {
        return type + "." + level;
    }

    /**
     * The resource type a role is homed at, the prefix of its id: {@code project} for {@code project.editor}, and
     * for a custom role the level it was created at.
     */
    public static String roleHome(String roleId) {
        int dot = roleId.indexOf('.');
        if (dot <= 0) {
            throw new IllegalArgumentException("A role id is <home type>.<name>, not " + roleId);
        }
        return roleId.substring(0, dot);
    }

    /**
     * The type of the roles homed at a resource type: {@code project_role} for {@code project}.
     */
    public static String roleType(String homeType) {
        return homeType + AuthzConstants.ROLE_TYPE_SUFFIX;
    }

    /**
     * A role's object in a store, typed by its home: {@code project_role:project.editor}.
     */
    public static String roleObject(String roleId) {
        return object(roleType(roleHome(roleId)), roleId);
    }

    /**
     * The type of the bindings attached to a resource type: {@code project_role_binding} for {@code project}.
     */
    public static String roleBindingType(String resourceType) {
        return resourceType + AuthzConstants.ROLE_BINDING_TYPE_SUFFIX;
    }

    /**
     * A binding's object in a store, typed by the resource it is attached to.
     *
     * @param resourceObject the resource the binding is attached to, {@code type:id}
     * @param bindingId      the binding's id
     */
    public static String bindingObject(String resourceObject, String bindingId) {
        return object(roleBindingType(typeOf(resourceObject)), bindingId);
    }

    /**
     * The id of the one binding of a role to a user, for a binding made under an id its writer chooses, so every
     * writer making it binds it once: {@code project.editor-of-<userId>}.
     *
     * @param roleId the role the binding grants
     * @param userId the id of the user it is made to
     */
    public static String roleBindingIdOf(String roleId, String userId) {
        return roleId + "-of-" + userId;
    }

    /**
     * The display name of a built-in role, from its type and level: {@code project.editor} is
     * {@code Project Editor}, {@code vm_node.registrar} is {@code Vm Node Registrar}.
     */
    public static String builtInRoleName(String roleId) {
        List<String> words = new ArrayList<>();
        for (String word : roleId.split("[._]")) {
            words.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return String.join(" ", words);
    }

    /**
     * The model name of a permission on a type: {@code project_can_edit} for {@code can_edit} on {@code project}.
     * The type prefix is what lets a role bundle permissions of several types and a binding on an ancestor
     * carry a descendant's permission.
     */
    public static String permissionName(String type, String permission) {
        return type + "_" + permission;
    }

    /**
     * Whether a permission only reads: {@code can_read} and every {@code can_read_<something>}; a viewer holds
     * these and nothing else.
     */
    public static boolean isReading(String permission) {
        return permission.equals(AuthzConstants.CAN_READ) || permission.startsWith(AuthzConstants.CAN_READ + "_");
    }

    /**
     * The references a template makes, without their braces: {@code {registration.id}} yields
     * {@code registration.id}. A literal yields none.
     */
    public static List<String> templateReferences(String template) {
        List<String> ret = new ArrayList<>();
        if (template != null) {
            Matcher matcher = TEMPLATE_REFERENCE.matcher(template);
            while (matcher.find()) {
                ret.add(matcher.group(1));
            }
        }
        return ret;
    }

    /**
     * The parameter a template reference starts with: {@code registration} for {@code registration.id}, or null
     * for a reference to the caller's scope.
     */
    public static String referencedParameter(String reference) {
        String ret;
        if (reference.startsWith(AuthzConstants.SCOPE_REFERENCE_PREFIX)) {
            ret = null;
        } else {
            int dot = reference.indexOf('.');
            ret = dot == -1 ? reference : reference.substring(0, dot);
        }
        return ret;
    }

    /**
     * Whether a type or short permission name is one the model accepts: lowercase letters, digits and
     * underscores, starting with a letter.
     */
    public static boolean isIdentifier(String name) {
        return name != null && IDENTIFIER.matcher(name).matches();
    }

    /**
     * Whether a value can be an object's id in a store: anything but {@code #}, {@code :} and whitespace, which
     * the engine reserves.
     */
    public static boolean isObjectId(String id) {
        return id != null && OBJECT_ID.matcher(id).matches();
    }

    /**
     * The id of a definition's rows within a tenant, the {@link AuthzConstants#TENANT_DEFINITION_TYPE} object: the definition's
     * id and the tenant's, joined by {@code @}.
     */
    public static String tenantDefinitionId(String definitionId, String tenantId) {
        return definitionId + "@" + tenantId;
    }

    /** The definition a {@link AuthzConstants#TENANT_DEFINITION_TYPE} id names; a definition's id holds no {@code @}. */
    public static String definitionOf(String tenantDefinitionId) {
        return tenantDefinitionId.substring(0, tenantDefinitionId.indexOf('@'));
    }

    /** The tenant a {@link AuthzConstants#TENANT_DEFINITION_TYPE} id names. */
    public static String tenantOf(String tenantDefinitionId) {
        return tenantDefinitionId.substring(tenantDefinitionId.indexOf('@') + 1);
    }

}
