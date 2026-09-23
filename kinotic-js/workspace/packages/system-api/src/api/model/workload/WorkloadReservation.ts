/**
 * The room one workload's run holds on a VmNode: the CPU, memory and disk its VM is sized for,
 * from the moment it is placed until its run ends or it is destroyed. A workload holds at most
 * one reservation per node, keyed by its id.
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
}
