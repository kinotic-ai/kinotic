/**
 * Which workloads' telemetry a query reads within the tenant: one application's, one workload's,
 * or, with neither, every workload's.
 */
export interface TelemetryFilter {
    applicationId: string | null
    workloadId?: string | null
}
