package org.kinotic.system.internal.api.services;

/**
 * How large the VMs of a project deployment are. The same in every environment: what differs
 * between a development node and a production one is how many of them fit, which the node's
 * capacity ledger decides.
 */
public final class ProjectWorkloadSizes {

    /**
     * CPU of the sync VM, in cores; a share of one core. It compiles the project's entity sources
     * and builds the UIs, so a deployment takes longer on a smaller share.
     */
    public static final double SYNC_CPUS = 0.5;

    /**
     * Memory of the sync VM in megabytes. It compiles the project's entity sources during
     * {@code kinotic sync}, which needs more headroom than serving does.
     */
    public static final int SYNC_MEMORY_MB = 2048;

    /**
     * Size of the sync VM's root filesystem in megabytes, enforced by the node's filesystem
     * quota. The package manager's cache lives there, outside the checkout mount.
     */
    public static final int SYNC_DISK_SIZE_MB = 2048;

    /**
     * Size limit applied to the project checkout mount (clone plus node_modules), enforced by
     * the node's filesystem quota.
     */
    public static final int SYNC_MOUNT_LIMIT_MB = 4096;

    /**
     * CPU of each runtime VM, in cores; a share of one core. The site publish and removal VMs
     * are sized the same way.
     */
    public static final double RUNTIME_CPUS = 0.5;

    /**
     * Memory of each runtime VM in megabytes.
     */
    public static final int RUNTIME_MEMORY_MB = 1024;

    /**
     * Size of each runtime VM's root filesystem in megabytes, enforced by the node's filesystem
     * quota. The checkout is a read-only mount, so the root holds only what the microservice
     * writes.
     */
    public static final int RUNTIME_DISK_SIZE_MB = 512;

    private ProjectWorkloadSizes() {
    }
}
