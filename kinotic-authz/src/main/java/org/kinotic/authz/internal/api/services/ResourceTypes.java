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
        return carried.get(name).stream().map(types::get).toList();
    }

    /** The types a type carries beside itself, in the order of their names. */
    List<ResourceType> inside(String name) {
        return carried.get(name).stream().filter(other -> !other.equals(name)).map(types::get).toList();
    }

    /** Makes a type carry another that is outside its own tree. */
    void carry(String carrier, String other) {
        carried.get(carrier).add(other);
    }
}
