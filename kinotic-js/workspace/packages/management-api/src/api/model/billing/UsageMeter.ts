/**
 * A measure of an organization's use of Kinotic Cloud that a metered plan component charges for.
 */
export enum UsageMeter {
    /** vCPUs of the organization's workload VMs, times the hours each ran. */
    VCPU_HOURS = 'VCPU_HOURS',
    /** Gigabytes of memory of the organization's workload VMs, times the hours each ran. */
    MEMORY_GB_HOURS = 'MEMORY_GB_HOURS'
}
