package org.kinotic.authz.api.model;

import org.kinotic.idl.api.utils.AuthzConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The naming rules of the authorization stores: how a resource, a role, the binding a role has on a resource, a
 * grant and a definition's rows within a tenant are named, and what such a name says.
 */
public final class AuthzNameUtil {

    /**
     * The system's object, the root every organization sits under.
     */
    public static final String SYSTEM_OBJECT = object(AuthzConstants.SYSTEM_TYPE, AuthzConstants.SYSTEM_OBJECT_ID);

    private static final Pattern OBJECT_ID = Pattern.compile("[^#:\\s]+");

    private AuthzNameUtil() {
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
     * Who a grant to a subject is made to in a store: a user as {@code user:<id>}, a group as its members,
     * {@code group:<id>#member}, a tenant as its users, {@code tenant:<id>#member}, an application as its end users,
     * {@code application:<id>#end_user}.
     *
     * @param subject the subject, named as the store holds it
     * @return the user or the userset
     */
    public static String userset(Subject subject) {
        String ret = object(subject.type().getObjectType(), subject.id());
        if (subject.type().getRelation() != null) {
            ret = ret + "#" + subject.type().getRelation();
        }
        return ret;
    }

    /**
     * The subject a user or a userset of a store stands for, the inverse of {@link #userset}.
     *
     * @param userset the user or the userset, in {@code type:id} or {@code type:id#relation} form
     * @return the subject, or null when a grant is made to no such userset
     */
    public static Subject subjectOf(String userset) {
        Subject ret = null;
        int relationStart = userset.indexOf('#');
        String relation = relationStart < 0 ? null : userset.substring(relationStart + 1);
        for (SubjectType type : SubjectType.values()) {
            if (type.getObjectType().equals(typeOf(userset)) && Objects.equals(type.getRelation(), relation)) {
                ret = new Subject(type, idOf(userset));
            }
        }
        return ret;
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
     * The object of the one binding a role has on a resource, typed by the resource and named by both:
     * {@code project_role_binding:acme.site/project.editor}. Everyone granted the role on the resource is a member
     * of it. A role's id holds no {@code /}, so the resource and the role each name one binding.
     *
     * @param resourceObject the resource the binding is attached to, {@code type:id}
     * @param roleId         the role the binding grants
     */
    public static String bindingObject(String resourceObject, String roleId) {
        return object(roleBindingType(typeOf(resourceObject)), idOf(resourceObject) + "/" + roleId);
    }

    /**
     * The id of the role a binding grants, which its object is named by.
     *
     * @param bindingObject a binding as {@link #bindingObject(String, String)} names it
     */
    public static String bindingRole(String bindingObject) {
        return bindingObject.substring(bindingObject.lastIndexOf('/') + 1);
    }

    /**
     * The id of the grant of a role to a member, on whichever resource it is made: the role's id and the member,
     * {@code project.editor:user:<userId>} or {@code project.editor:group:<groupId>#member}.
     *
     * @param roleId the role granted
     * @param member who holds it, in {@code type:id} form, or a userset such as {@code group:id#member}
     */
    public static String grantId(String roleId, String member) {
        return roleId + ":" + member;
    }

    /**
     * The role a grant's id names.
     *
     * @throws IllegalArgumentException when the value is not a grant's id
     */
    public static String grantRole(String grantId) {
        return grantId.substring(0, grantSeparator(grantId));
    }

    /**
     * The member a grant's id names, in {@code type:id} form, or a userset such as {@code group:id#member}.
     *
     * @throws IllegalArgumentException when the value is not a grant's id
     */
    public static String grantMember(String grantId) {
        return grantId.substring(grantSeparator(grantId) + 1);
    }

    // A role's id holds no colon, so the first one ends it; the member after it is type:id
    private static int grantSeparator(String grantId) {
        int ret = grantId.indexOf(':');
        if (ret <= 0 || grantId.indexOf(':', ret + 1) < 0) {
            throw new IllegalArgumentException("A grant's id is <role id>:<member>, not " + grantId);
        }
        return ret;
    }

    /**
     * The display name of a built-in role, from its type and level: {@code project.editor} is
     * {@code Project Editor}, {@code vm_node.registrar} is {@code Vm Node Registrar}.
     */
    public static String builtInRoleName(String roleId) {
        List<String> words = new ArrayList<>();
        for (String word : roleId.split("[._]")) {
            // an identifier may hold consecutive underscores, which split as an empty word
            if (!word.isEmpty()) {
                words.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        return String.join(" ", words);
    }

    /**
     * Whether a permission only reads: {@code can_read} and every {@code can_read_<something>}; a viewer holds
     * these and nothing else.
     */
    public static boolean isReading(String permission) {
        return permission.equals(AuthzConstants.CAN_READ) || permission.startsWith(AuthzConstants.CAN_READ + "_");
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
