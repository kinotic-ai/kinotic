package org.kinotic.domain.api.model.grind;

/**
 * How a step's result was stored in the job scope, recorded on the {@link TaskRecord}
 * and governing how the step is rehydrated when a run is resumed.
 */
public enum StoreType {

    /**
     * The step stored nothing. On resume a completed step is skipped.
     */
    NONE,

    /**
     * The step stored transient wiring for later steps in the same run. The value is not
     * persisted; on resume the value is reloaded from its external source of truth.
     */
    RESULT,

    /**
     * The step stored durable state. The value is serialized into the {@link TaskRecord}
     * and on resume is replayed from the record instead of re-executing the step.
     */
    STATE

}
