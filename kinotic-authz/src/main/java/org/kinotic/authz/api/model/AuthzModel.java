package org.kinotic.authz.api.model;

import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import tools.jackson.databind.JsonNode;
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
 * @param parents     for each resource type contained in another, the type containing it, which is also the
 *                    relation a resource of the type holds its parent under; a root type has no entry
 * @param servicePermissions the permissions of an application's own services, which the definition has no relation
 *                    for: each one's model name to the model names held with it, itself and every permission it
 *                    implies. A role holds one by naming it, and a check names the one it asks about. Empty for
 *                    the system's model
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public record AuthzModel(ObjectNode definition,
                         String hash,
                         Map<String, Set<String>> permissions,
                         Map<String, Set<String>> roles,
                         Map<String, Set<String>> bindable,
                         Map<String, String> parents,
                         Map<String, Set<String>> servicePermissions) {

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
        return tier(resourceType).contains(AuthzNameUtil.roleHome(roleId));
    }

    /**
     * Requires that a role may be granted on a resource of a type.
     *
     * @param roleId       the role, {@code <home type>.<name>}
     * @param resourceType the type of the resource the grant is made on
     * @throws IllegalArgumentException when the engine would refuse the grant
     */
    public void requireGrantable(String roleId, String resourceType) {
        if (!grantable(roleId, resourceType)) {
            throw new IllegalArgumentException("Role " + roleId + " is homed at " + AuthzNameUtil.roleHome(roleId)
                                                       + " and cannot be granted on a " + resourceType);
        }
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
     * Whether the definition gives a type a relation.
     *
     * @param type     the type, such as {@code project_role}
     * @param relation the relation, such as the model name of a permission
     * @return true when the type has the relation
     */
    public boolean hasRelation(String type, String relation) {
        boolean ret = false;
        for (JsonNode definition : definition.path("type_definitions")) {
            if (type.equals(definition.path("type").asString())) {
                ret = definition.path("relations").has(relation);
            }
        }
        return ret;
    }

    /**
     * What a role bundling the given permissions holds: each permission of an application's own services with
     * every permission it implies, named, and every other permission on everything a grant of the role reaches.
     *
     * @param permissions the model names of the permissions the role bundles
     * @return the role's permissions, naming no definition
     */
    public RolePermissions bundle(Set<String> permissions) {
        Set<String> everywhere = new TreeSet<>();
        Set<String> ofServices = new TreeSet<>();
        for (String permission : permissions) {
            Set<String> held = servicePermissions.get(permission);
            if (held == null) {
                everywhere.add(permission);
            } else {
                ofServices.addAll(held);
            }
        }
        return new RolePermissions(everywhere, Map.of(), ofServices);
    }

    /**
     * The built-in roles a store running the model holds as relationships: those homed at a type the store has
     * resources of. A role homed at a type of an application's own services is held as no relationship, and a check
     * of a permission of such a service names the roles holding it, which {@link #rolesHolding} gives.
     *
     * @return each stored role's id to the model names of the permissions it bundles
     */
    public Map<String, Set<String>> storedRoles() {
        Map<String, Set<String>> ret = new TreeMap<>();
        roles.forEach((roleId, permissions) -> {
            if (bindable.containsKey(AuthzNameUtil.roleHome(roleId))) {
                ret.put(roleId, permissions);
            }
        });
        return ret;
    }

    /**
     * The built-in roles holding a permission of an application's own services: those bundling it, or one that
     * implies it.
     *
     * @param servicePermission the model name of the permission, such as {@code report_can_generate}
     * @return the ids of the roles, empty for a permission the model lacks
     */
    public Set<String> rolesHolding(String servicePermission) {
        Set<String> ret = new TreeSet<>();
        roles.forEach((roleId, permissions) -> {
            for (String bundled : permissions) {
                if (servicePermissions.getOrDefault(bundled, Set.of()).contains(servicePermission)) {
                    ret.add(roleId);
                }
            }
        });
        return ret;
    }

    /**
     * The built-in roles holding each permission of a type of an application's own services, as
     * {@link #rolesHolding} gives them.
     *
     * @param type the type
     * @return the model name of each of the type's permissions to the ids of the roles holding it, empty for a type
     *         the model lacks
     */
    public Map<String, Set<String>> rolesHoldingEach(String type) {
        Map<String, Set<String>> ret = new TreeMap<>();
        for (String permission : permissions.getOrDefault(type, Set.of())) {
            String name = AuthzUtil.permissionName(type, permission);
            ret.put(name, rolesHolding(name));
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
            String home = AuthzNameUtil.roleHome(role.getKey());
            if (tier(root).contains(home)) {
                ret.add(new RoleDefinition(role.getKey(), AuthzNameUtil.builtInRoleName(role.getKey()), null, true, home,
                                           grantableOn(home, root), role.getValue(), Map.of(),
                                           AuthzConstants.TENANT_TYPE.equals(home)));
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
