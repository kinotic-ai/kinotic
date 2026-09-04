package org.kinotic.management.api.model.workload;

/**
 * Represents the current status of a {@link Workload}.
 */
public enum WorkloadStatus {
    /**
     * Accepted, not yet placed on a node.
     */
    PENDING,
    /**
     * Placed on a node, which is bringing the VM up.
     */
    STARTING,
    /**
     * The guest is executing.
     */
    RUNNING,
    /**
     * A stop was requested and the node is shutting the guest down.
     */
    STOPPING,
    /**
     * The run was ended by a stop request, or a detached run's guest exited on its own with
     * code 0. The VM is dormant, unless {@link Workload#isAutoRemove()} discarded it.
     */
    STOPPED,
    /**
     * A non-detached run's guest ran to its end and exited with code 0. The VM is dormant,
     * unless {@link Workload#isAutoRemove()} discarded it.
     */
    COMPLETED,
    /**
     * The VM could not be started, its guest exited on its own with a non-zero code, or its
     * node went offline while it ran.
     */
    FAILED;

    /**
     * True when this run of the workload has ended — the guest is no longer executing.
     * A {@link #STOPPED} or {@link #COMPLETED} workload may still be restarted in place, which
     * begins a new run.
     */
    public boolean hasEnded() {
        return this == STOPPED || this == COMPLETED || this == FAILED;
    }
}
