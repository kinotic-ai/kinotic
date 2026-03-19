package org.kinotic.persistence.api.model.idl.decorators;

/**
 * Created By Navíd Mitchell 🤪on 3/31/25
 */
public enum EntityType {
    // NOTE: The order of these values since they are serialized and deserialized by ordinal
    /**
     * This is the default type of entity. It is a basic single table entity.
     */
    TABLE,
    /**
     * This entity is a stream entity. It is used for streaming data
     */
    STREAM,
}
