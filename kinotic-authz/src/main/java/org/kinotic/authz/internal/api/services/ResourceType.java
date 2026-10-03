package org.kinotic.authz.internal.api.services;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A resource type while a model is being generated: its place in the containment tree, the structural
 * relations the kernel gives it, and its permissions with what each implies.
 */
final class ResourceType {

    final String name;
    final String parent;
    /** Structural relations the identity model writes, such as {@code member}, each to the subject types it holds. */
    final Map<String, Set<String>> memberships = new LinkedHashMap<>();
    /** Short permission names to the short names each implies on this same type. */
    final Map<String, Set<String>> permissions = new LinkedHashMap<>();

    ResourceType(String name, String parent) {
        this.name = name;
        this.parent = parent;
    }

    void addPermission(String permission, Iterable<String> implies) {
        Set<String> implied = permissions.computeIfAbsent(permission, k -> new LinkedHashSet<>());
        implies.forEach(implied::add);
    }

}
