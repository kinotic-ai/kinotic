package org.kinotic.orchestrator.api.model.workload;

/**
 * Whether a {@link Workload}'s VM has network access.
 */
public enum NetworkMode {

    ENABLED,

    /**
     * No network access. The boxlite provider cannot boot a VM in this mode and rejects a
     * workload that asks for it; restrict egress with {@link NetworkPolicy#getAllowedHosts()}
     * instead.
     */
    DISABLED
}
