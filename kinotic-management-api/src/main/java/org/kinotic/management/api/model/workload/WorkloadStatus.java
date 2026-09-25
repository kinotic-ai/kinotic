package org.kinotic.management.api.model.workload;

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
     * True when this status comes later in a run than {@code other}, so a report of it moves the run
     * forward.
     */
    public boolean isAfter(WorkloadStatus other) {
        return rank > other.rank;
    }
}
