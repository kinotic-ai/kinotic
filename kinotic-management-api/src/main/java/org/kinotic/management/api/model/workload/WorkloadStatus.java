package org.kinotic.management.api.model.workload;

import lombok.Getter;

/**
 * Represents the current status of a {@link Workload}. A run moves through these in order and only
 * forward: pending, starting, running, stopping, then ended, where the two ended statuses share one
 * rank since a run ends once.
 */
public enum WorkloadStatus {
    PENDING(0),
    STARTING(1),
    RUNNING(2),
    STOPPING(3),
    STOPPED(4),
    FAILED(4);

    /**
     * Where this status falls in a run: a status of a lower rank comes earlier, and the two ended
     * statuses share the highest.
     */
    @Getter
    private final int rank;

    WorkloadStatus(int rank) {
        this.rank = rank;
    }

    /**
     * True when this run of the workload has ended — the guest is no longer executing and the
     * node removes its VM.
     */
    public boolean isComplete() {
        return this == STOPPED || this == FAILED;
    }

    /**
     * True while the run holds a VM on its node: started and not yet ended. A stop the node has not
     * answered counts, since the VM may still be there.
     */
    public boolean isOpen() {
        return this == STARTING || this == RUNNING || this == STOPPING;
    }

    /**
     * True when this status comes later in a run than {@code other}, so a report of it moves the run
     * forward.
     */
    public boolean isAfter(WorkloadStatus other) {
        return rank > other.rank;
    }
}
