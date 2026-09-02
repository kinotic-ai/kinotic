import type { LogFormat } from '@/internal/api/model/LogFormat'

/**
 * A running VM's log source, as consumed by the Alloy config generation: where its log
 * files live on the host and the identity labels its log streams carry.
 */
export interface LogTarget {

    workloadId: string

    /** The provider's id for the VM. */
    vmId: string

    /** Path the shipper tails, a glob where the VM writes more than one file. */
    logPath: string

    /** Encoding of the lines found at {@link logPath}. */
    format: LogFormat

    /** Organization whose Loki tenant receives the logs; null ships to the system tenant. */
    organizationId: string | null

    /** Application the workload belongs to; null omits the application_id label. */
    applicationId: string | null
}
