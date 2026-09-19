package org.kinotic.system.internal.api.services;

/**
 * The CPU and root-filesystem size of the VMs of a project deployment. The same in every
 * environment: what differs between a development node and a production one is how many of
 * them fit, which the node's capacity ledger decides.
 */
public final class ProjectWorkloadSizes {

    /**
     * CPU of the sync VM, in cores. It compiles the project's entity sources and builds the UIs,
     * which is where a deployment spends its CPU.
     */
    public static final double SYNC_CPUS = 1;

    /**
     * Size of the sync VM's root filesystem in megabytes, enforced by the node's filesystem
     * quota. The package manager's cache lives there, outside the checkout mount.
     */
    public static final int SYNC_DISK_SIZE_MB = 2048;

    /**
     * CPU of each runtime VM, in cores; a share of one core. The site publish and removal VMs
     * are sized the same way.
     */
    public static final double RUNTIME_CPUS = 0.5;

    /**
     * Size of each runtime VM's root filesystem in megabytes, enforced by the node's filesystem
     * quota. The checkout is a read-only mount, so the root holds only what the microservice
     * writes.
     */
    public static final int RUNTIME_DISK_SIZE_MB = 512;

    private ProjectWorkloadSizes() {
    }
}
