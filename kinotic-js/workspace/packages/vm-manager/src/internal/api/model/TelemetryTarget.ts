import type { LogFormat } from '@/internal/api/model/LogFormat'
import type { OtlpEndpoint } from '@/internal/api/model/OtlpEndpoint'

/**
 * A running VM's telemetry sources, as consumed by the Alloy config generation: where its log
 * files live on the host, where its traces and metrics arrive, and the identity labels its
 * streams carry.
 */
export interface TelemetryTarget {

    workloadId: string

    /** The provider's id for the VM. */
    vmId: string

    /** Path the shipper tails, a glob where the VM writes more than one file. */
    logPath: string

    /** Encoding of the lines found at {@link logPath}. */
    format: LogFormat

    /** Where the VM pushes its traces and metrics; null when the workload did not elect telemetry. */
    otlp: OtlpEndpoint | null

    /** Organization whose tenant receives the telemetry; null ships to the system tenant. */
    organizationId: string | null

    /** Application the workload belongs to; null omits the application_id label. */
    applicationId: string | null
}
