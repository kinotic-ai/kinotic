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

}
