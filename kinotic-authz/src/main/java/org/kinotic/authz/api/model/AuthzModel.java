package org.kinotic.authz.api.model;

import org.kinotic.idl.api.utils.AuthzUtil;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A generated authorization model.
 *
 * @param definition  the model as OpenFGA's JSON authorization model, schema 1.1
 * @param hash        a digest of the definition, equal for equal definitions, so a store's model is rewritten
 *                    only when the definition changed
 * @param permissions the short permission names of every resource type in the model, keyed by type: the
 *                    catalog a role may bundle from
 * @param roles       the built-in roles the model implies, each role's id to the model names of the permissions it
 *                    bundles: a viewer, an editor and an admin of every type with permissions, the admin holding
 *                    the type's own and everything inside it, the roles the type's services declare, and for a
 *                    system store the application developer and the system maintainer and support; a role's
 *                    id starts with its home type, {@code project.editor}
 * @param bindable    for each resource type, the home types of the roles a binding on a resource of it may grant:
 *                    the type and the types inside it within its trust boundary, which the engine enforces
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public record AuthzModel(ObjectNode definition, String hash, Map<String, Set<String>> permissions, Map<String, Set<String>> roles, Map<String, Set<String>> bindable) {

    /**
     * The resource types of the tier rooted at a type: the root and the types inside it within its trust boundary,
     * the types whose roles a grant on the root may give.
     *
     * @param root the tier's root type, such as {@code organization}
     * @return the tier's types, empty for a type the model lacks
     */
    public Set<String> tier(String root) {
        return bindable.getOrDefault(root, Set.of());
    }

    /**
     * Whether a role may be granted on a resource of a type: its home is the type or one inside it, within the
     * type's trust boundary.
     *
     * @param roleId       the role, {@code <home type>.<name>}
     * @param resourceType the type of the resource the grant is made on
     * @return true when the engine admits the grant
     */
    public boolean grantable(String roleId, String resourceType) {
        return tier(resourceType).contains(AuthzUtil.roleHome(roleId));
    }

    /**
     * The types of a tier a role homed at a type may be granted on.
     *
     * @param home the role's home type
     * @param root the tier's root type
     * @return the types, empty for a home outside the tier
     */
    public Set<String> grantableOn(String home, String root) {
        Set<String> ret = new TreeSet<>();
        for (String type : tier(root)) {
            if (tier(type).contains(home)) {
                ret.add(type);
            }
        }
        return ret;
    }

    /**
     * The built-in roles of a tier: those homed at one of its types.
     *
     * @param root the tier's root type
     * @return the roles, in the order of their ids
     */
    public List<RoleDefinition> builtInRoles(String root) {
        List<RoleDefinition> ret = new ArrayList<>();
        for (Map.Entry<String, Set<String>> role : new TreeMap<>(roles).entrySet()) {
            String home = AuthzUtil.roleHome(role.getKey());
            if (tier(root).contains(home)) {
                ret.add(new RoleDefinition(role.getKey(), AuthzUtil.builtInRoleName(role.getKey()), null, true, home,
                                           grantableOn(home, root), role.getValue()));
            }
        }
        return ret;
    }

    /**
     * The permissions a role homed at a type may bundle, by the type each belongs to: those of the home and of
     * the types inside it within its trust boundary.
     *
     * @param home the role's home type
     * @return the model names of the permissions, keyed by type
     */
    public Map<String, Set<String>> catalog(String home) {
        Map<String, Set<String>> ret = new TreeMap<>();
        for (String type : tier(home)) {
            Set<String> names = new TreeSet<>();
            for (String permission : permissions.getOrDefault(type, Set.of())) {
                names.add(AuthzUtil.permissionName(type, permission));
            }
            if (!names.isEmpty()) {
                ret.put(type, names);
            }
        }
        return ret;
    }
}
