package org.kinotic.system.api.model.workload;

import org.kinotic.management.api.model.workload.Workload;

/**
 * Represents the type of VM provider a {@link VmNode} runs its {@link Workload}s on.
 */
public enum VmProviderType {
    BOXLITE,
    CLOUD_HYPERVISOR
}
