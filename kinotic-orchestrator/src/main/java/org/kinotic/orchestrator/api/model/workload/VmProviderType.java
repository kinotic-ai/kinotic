package org.kinotic.orchestrator.api.model.workload;

/**
 * Represents the type of VM provider used to run a {@link Workload}.
 */
public enum VmProviderType {
    BOXLITE,
    FIRECRACKER,
    CLOUD_HYPERVISOR
}
