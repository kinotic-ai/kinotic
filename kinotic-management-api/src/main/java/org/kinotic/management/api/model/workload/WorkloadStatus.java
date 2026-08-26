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
     * True when this run of the workload has ended — the guest is no longer executing.
     * A {@link #STOPPED} workload may still be restarted in place, which begins a new run.
     */
    public boolean isComplete() {
        return this == STOPPED || this == FAILED;
    }
}
