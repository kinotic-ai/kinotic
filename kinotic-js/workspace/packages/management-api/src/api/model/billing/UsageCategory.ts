/**
 * The group a UsageMeter's usage is shown in.
 */
export enum UsageCategory {
    /** The VMs the organization's microservices run in. */
    COMPUTE = 'COMPUTE',
    /** The data the organization keeps. */
    STORAGE = 'STORAGE'
}
