/**
 * A running VM's log source, as consumed by the Alloy config generation: where its log
 * files live on the host and the identity labels its log streams carry.
 */
export interface LogTarget {

    workloadId: string

    /** boxlite box id (ULID). */
    vmId: string

    /** Host directory holding the VM's log files. */
    logDir: string

    /** Organization whose Loki tenant receives the logs; null ships to the system tenant. */
    organizationId: string | null

    /** Application the workload belongs to; null omits the application_id label. */
    applicationId: string | null
}
