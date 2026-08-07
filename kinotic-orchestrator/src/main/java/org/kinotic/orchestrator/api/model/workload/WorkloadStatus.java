package org.kinotic.orchestrator.api.model.workload;

/**
 * Represents the current status of a {@link Workload}.
 */
public enum WorkloadStatus {
    PENDING,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED
}
