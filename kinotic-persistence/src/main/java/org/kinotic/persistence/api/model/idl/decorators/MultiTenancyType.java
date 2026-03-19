package org.kinotic.persistence.api.model.idl.decorators;

/**
 * Created by Navíd Mitchell 🤪on 6/16/23.
 */
public enum MultiTenancyType {
    // NOTE: The order of these values since they are serialized and deserialized by ordinal
    NONE,
    SHARED
    // We will support these in the future
//    ISOLATED,
//    HYBRID
}
