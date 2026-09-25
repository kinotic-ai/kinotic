package org.kinotic.domain.api.model;

/**
 * The kinds of {@link Watched} record, the first half of a {@link WatchedParent}.
 */
public enum WatchedType {
    WORKLOAD,
    PROJECT_DEPLOYMENT,
    MICROSERVICE_DEPLOYMENT,
    UI_DEPLOYMENT,
    VM_NODE,
    JOB_RUN
}
