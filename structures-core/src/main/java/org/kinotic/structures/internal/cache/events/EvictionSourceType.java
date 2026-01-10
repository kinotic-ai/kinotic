package org.kinotic.structures.internal.cache.events;

public enum EvictionSourceType {
    STRUCTURE("Structure"),
    NAMED_QUERY("Named Query");

    private final String displayName;

    EvictionSourceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
