package org.kinotic.grindv2.api;

/**
 * How a step's result is kept for the rest of the run, and what a resumed run does when it
 * finds the step already completed.
 */
public enum StoreType {

    /**
     * The result is not kept. On resume a completed step is skipped.
     */
    NONE,

    /**
     * The result is stored in the job scope for later steps but not persisted. On resume a
     * completed step re-executes - or executes its declared reload task - to regenerate the
     * value from its source of truth.
     */
    RESULT,

    /**
     * The result is stored in the job scope and serialized into the run's {@link StepRecord}.
     * On resume a completed step replays the recorded value instead of executing. The value
     * must survive a JSON round trip: a plain class or record with concrete field types.
     * Generic types (List, Map, Optional, ...) are rejected because their erased type
     * arguments cannot be restored.
     */
    STATE

}
