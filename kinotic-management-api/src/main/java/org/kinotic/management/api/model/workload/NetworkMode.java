package org.kinotic.management.api.model.workload;

/**
 * Whether a {@link Workload}'s VM has network access.
 */
public enum NetworkMode {

    ENABLED,

    /**
     * No network access. Nothing outside the VM is reachable, by name or by address, and
     * any {@link NetworkPolicy#getAllowedHosts()} the workload declares does not apply.
     */
    DISABLED
}
