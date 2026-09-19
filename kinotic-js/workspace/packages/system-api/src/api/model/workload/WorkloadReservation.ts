/**
 * The room one workload holds on a VmNode: its CPU, memory and disk while its VM runs, and its
 * disk alone once the run has ended, since a VM that is kept for a restart keeps its disk on the
 * node. A workload holds at most one reservation per node, keyed by its id.
 */
export interface WorkloadReservation {
    /** The id of the workload holding the reservation. */
    workloadId: string
    /** CPU the workload's VM is allotted, in cores; a fraction is a share of one core. */
    cpus: number
    /** Memory the workload's VM is allotted, in megabytes. */
    memoryMb: number
    /** Disk the workload's VM is allotted, in megabytes. */
    diskMb: number
    /**
     * True while the workload's run holds its CPU and memory; false once the run has ended and
     * only the disk is still held.
     */
    running: boolean
}
