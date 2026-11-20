package org.kinotic.authz.internal.api.services;

import org.kinotic.idl.api.utils.AuthzUtil;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A resource type while a model is being generated: its place in the containment tree, the structural
 * relations the kernel gives it, its permissions with what each implies, and the roles its services declare.
 */
final class ResourceType {

    final String name;
    final String parent;
    /** Structural relations the identity model writes, such as {@code member}, each to the subject types it holds. */
    final Map<String, Set<String>> memberships = new LinkedHashMap<>();
    /** Short permission names to the short names each implies on this same type. */
    final Map<String, Set<String>> permissions = new LinkedHashMap<>();
    /** The roles the type's services declare, each id to the short names of the permissions it bundles. */
    final Map<String, Set<String>> declaredRoles = new LinkedHashMap<>();
    /** Structural relations reached through the parent, each to the relation of the parent it is read from. */
    final Map<String, String> inherited = new LinkedHashMap<>();
    /**
     * True for a type whose resources are granted one by one: each of its permissions stands alone, and a role
     * bundles one either for every resource of the type or for the resources it names.
     */
    boolean granular;

    ResourceType(String name, String parent) {
        this.name = name;
        this.parent = parent;
    }

    void addPermission(String permission, Iterable<String> implies) {
        Set<String> implied = permissions.computeIfAbsent(permission, k -> new LinkedHashSet<>());
        implies.forEach(implied::add);
    }

    /** The model names of the given permissions of this type. */
    Set<String> modelNames(Collection<String> permissions) {
        Set<String> ret = new TreeSet<>();
        for (String permission : permissions) {
            ret.add(AuthzUtil.permissionName(name, permission));
        }
        return ret;
    }

    /** The model names of every permission of this type. */
    Set<String> permissionNames() {
        return modelNames(permissions.keySet());
    }

    /** The model names of this type's reading permissions: {@code can_read} and every {@code can_read_<something>}. */
    Set<String> readingNames() {
        return modelNames(permissions.keySet().stream().filter(AuthzUtil::isReading).toList());
    }

    /**
     * Whether holding a permission holds one of the given ones: it is one of them, or implies one, directly or
     * through the permissions it implies.
     */
    boolean holdsAny(String permission, Set<String> others) {
        Set<String> held = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(List.of(permission));
        while (!pending.isEmpty()) {
            String next = pending.pop();
            if (held.add(next)) {
                pending.addAll(permissions.getOrDefault(next, Set.of()));
            }
        }
        return !Collections.disjoint(held, others);
    }

}
