package org.kinotic.system.internal.api.model.grind;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kinotic.system.api.model.grind.StoreType;

/**
 * What a {@link ReplayLedger} knows about one completed step of the run being resumed.
 */
@Getter
@RequiredArgsConstructor
public class ReplayEntry {

    /**
     * How the completed step stored its result.
     */
    private final StoreType storeType;

    /**
     * True if the completed step produced dynamic steps, meaning it must be re-executed
     * so those steps are regenerated.
     */
    private final boolean dynamicSteps;

    /**
     * The recorded stored value, deserialized, or null if the step stored nothing
     * or the value could not be restored.
     */
    private final Object value;

}
