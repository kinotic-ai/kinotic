/**
 * A type of billable usage of Kinotic Cloud, which a metered plan component sets the rate of. BillingService.findMeters
 * describes each one.
 */
export enum UsageMeter {
    /** vCPUs of the organization's running microservices, times the hours each ran. */
    VCPU_HOURS = 'VCPU_HOURS',
    /** Gigabytes of memory of the organization's running microservices, times the hours each ran. */
    MEMORY_GB_HOURS = 'MEMORY_GB_HOURS',
    /** Gigabytes the organization's entity data takes up, over time: a gigabyte stored for a whole month is one GB-month. */
    DATA_STORAGE_GB_MONTHS = 'DATA_STORAGE_GB_MONTHS'
}
