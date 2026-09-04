
/**
 * The current status of a Workload.
 */
export enum WorkloadStatus {
    /** Accepted, not yet placed on a node. */
    PENDING = 'PENDING',
    /** Placed on a node, which is bringing the VM up. */
    STARTING = 'STARTING',
    /** The guest is executing. */
    RUNNING = 'RUNNING',
    /** A stop was requested and the node is shutting the guest down. */
    STOPPING = 'STOPPING',
    /**
     * The run was ended by a stop request, or a detached run's guest exited on its own with
     * code 0. The VM is dormant, unless autoRemove discarded it.
     */
    STOPPED = 'STOPPED',
    /**
     * A non-detached run's guest ran to its end and exited with code 0. The VM is dormant,
     * unless autoRemove discarded it.
     */
    COMPLETED = 'COMPLETED',
    /**
     * The VM could not be started, its guest exited on its own with a non-zero code, or its
     * node went offline while it ran.
     */
    FAILED = 'FAILED'
}
