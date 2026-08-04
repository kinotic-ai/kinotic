package org.kinotic.domain.api.model.grind;

/**
 * The lifecycle state of a {@link TaskRecord}.
 */
public enum TaskRecordStatus {

    /**
     * The step began executing. A record that keeps this status after its run
     * reached a terminal status indicates the step never finished.
     */
    STARTED,

    /**
     * The step finished successfully.
     */
    COMPLETED,

    /**
     * The step threw an exception.
     */
    FAILED

}
