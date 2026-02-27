package org.kinotic.persistence.internal.cache.events;

import lombok.Getter;

@Getter
public enum EvictionSourceType {
    ENTITY_DEFINITION("Entity Definition"),
    NAMED_QUERY("Named Query");

    private final String displayName;

    EvictionSourceType(String displayName) {
        this.displayName = displayName;
    }

}
