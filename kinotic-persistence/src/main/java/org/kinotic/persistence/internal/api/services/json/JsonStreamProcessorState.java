package org.kinotic.persistence.internal.api.services.json;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * The state a {@link JsonStreamProcessor} carries through one call to its {@link JsonStreamProcessorStrategy}
 * and {@link JsonTransformer}s. A strategy extends it with whatever it collects from the json.
 * Created By Navíd Mitchell 🤪on 3/29/25
 */
@Getter
@Setter(AccessLevel.PACKAGE)
public class JsonStreamProcessorState {

    /**
     * How many objects enclose the current token: 1 while reading the fields of a root object.
     */
    private int objectDepth = 0;

    /**
     * @return true while the current field belongs to a root object
     */
    public boolean isRootField() {
        return objectDepth == 1;
    }
}
