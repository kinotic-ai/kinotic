package org.kinotic.management.api.model.workload;

/**
 * Represents the current status of a {@link Workload}.
 */
public enum WorkloadStatus {
    PENDING,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED;

    /**
     * True when this run of the workload has ended — the guest is no longer executing and the
     * node removes its VM.
     */
    public boolean isComplete() {
        return this == STOPPED || this == FAILED;
    }
}
