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

    /**
     * Marks a template reference to the calling participant's scope rather than to a parameter, as in
     * {@code {@organizationId}}.
     */
    public static final String SCOPE_REFERENCE_PREFIX = "@";

    /** The type of the platform itself, the root every organization sits under. */
    public static final String PLATFORM_TYPE = "platform";

    /** The id of the one platform object, so a check on the platform needs no id from the request. */
    public static final String PLATFORM_OBJECT_ID = "kinotic";

    /** The kernel types every store has: the identities, the bundles of permissions and the grants of them. */
    public static final String USER_TYPE = "user";
    public static final String GROUP_TYPE = "group";
    public static final String ROLE_TYPE = "role";
    public static final String ROLE_BINDING_TYPE = "role_binding";

    /** The kernel types of the identity scopes, each contained in the one before it. */
    public static final String ORGANIZATION_TYPE = "organization";
    public static final String APPLICATION_TYPE = "application";
    public static final String TENANT_TYPE = "tenant";

    /** The membership relation of an organization, a tenant or a group. */
    public static final String MEMBER_RELATION = "member";
    /** The membership relation of an application's end users. */
    public static final String END_USER_RELATION = "end_user";
    /** The relation of a binding to the role it grants. */
    public static final String ROLE_RELATION = "role";
    /** The relation of a resource to a binding attached to it. */
    public static final String ROLE_BINDING_RELATION = "role_binding";
    /** The relation a role holds for everyone, which a binding then narrows to its members. */
    public static final String GRANT_RELATION = "grant";
    /** Everyone, the user of every tuple that puts a permission in a role. */
    public static final String EVERYONE = USER_TYPE + ":*";

    /** The levels of the built-in roles every resource type gets: viewing it, editing it, and everything. */
    public static final String VIEWER = "viewer";
    public static final String EDITOR = "editor";
    public static final String ADMIN = "admin";
    /** The level of the built-in role over everything inside an application but the application itself. */
    public static final String DEVELOPER = "developer";
    /** The built-in role holding every permission of an organization and of everything inside it. */
    public static final String ORGANIZATION_ADMIN_ROLE = ORGANIZATION_TYPE + "." + ADMIN;
    /** The built-in role holding every permission of everything inside an application, and none of its own. */
    public static final String APPLICATION_DEVELOPER_ROLE = APPLICATION_TYPE + "." + DEVELOPER;

    public static final String CAN_VIEW = "can_view";
    public static final String CAN_EDIT = "can_edit";
    public static final String CAN_DELETE = "can_delete";

    /** The longest relation name OpenFGA accepts; a typed permission name must fit it. */
    public static final int MAX_RELATION_NAME_LENGTH = 50;

    private static final Pattern TEMPLATE_REFERENCE = Pattern.compile("\\{([^{}]+)}");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]*");

    private AuthzUtil() {
    }

    /**
     * An object in a store, {@code type:id}: a resource, an identity, a role or a binding.
     */
    public static String object(String type, String id) {
        return type + ":" + id;
    }

    /**
     * The id of a built-in role of a type: {@code project.editor} for the editor of projects.
     */
    public static String roleId(String type, String level) {
        return type + "." + level;
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
     * Whether a resource or object id template takes its value from the request, rather than naming a type or
     * id outright.
     */
    public static boolean isTemplate(String value) {
        return value != null && TEMPLATE_REFERENCE.matcher(value).find();
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
        if (reference.startsWith(SCOPE_REFERENCE_PREFIX)) {
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

}
