package org.kinotic.authz.internal.api.services;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The resource types of a model while it is generated, with the types each one carries: itself and every type
 * inside it, whose permissions a grant made on it reaches.
 */
final class ResourceTypes {

    private final Map<String, ResourceType> types;
    private final Map<String, Set<String>> carried = new TreeMap<>();
    private final Map<String, ResourceType> undeclared = new TreeMap<>();

    /**
     * @param types the types by name, each naming a parent among them or none
     */
    ResourceTypes(Map<String, ResourceType> types) {
        this.types = types;
        for (ResourceType type : types.values()) {
            for (String ancestor = type.name; ancestor != null; ancestor = types.get(ancestor).parent) {
                carried.computeIfAbsent(ancestor, k -> new TreeSet<>()).add(type.name);
            }
        }
    }

    ResourceType get(String name) {
        return types.get(name);
    }

    /** Every type, in the order the types were declared. */
    Collection<ResourceType> declared() {
        return types.values();
    }

    /** Every type, in the order of the names. */
    Collection<ResourceType> sorted() {
        return new TreeMap<>(types).values();
    }

    /** The types a type carries, itself among them, in the order of their names. */
    List<ResourceType> carriedBy(String name) {
        return carried.get(name).stream().map(this::carriedType).toList();
    }

    /** The types a type carries beside itself, in the order of their names. */
    List<ResourceType> inside(String name) {
        return carried.get(name).stream().filter(other -> !other.equals(name)).map(this::carriedType).toList();
    }

    private ResourceType carriedType(String name) {
        ResourceType ret = types.get(name);
        return ret != null ? ret : undeclared.get(name);
    }

    /** Makes a type carry another that is outside its own tree. */
    void carry(String carrier, String other) {
        carried.get(carrier).add(other);
    }

    /**
     * Makes a type carry the permissions of one the model does not declare: the carrier, its roles and its bindings
     * hold them, and the model has no type, role or binding of the other.
     */
    void carryUndeclared(String carrier, ResourceType other) {
        undeclared.put(other.name, other);
        carried.get(carrier).add(other.name);
    }
}
