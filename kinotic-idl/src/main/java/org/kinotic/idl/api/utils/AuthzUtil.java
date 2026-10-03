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
