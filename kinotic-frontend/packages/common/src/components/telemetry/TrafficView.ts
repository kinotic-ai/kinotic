import type { RouteLocationRaw } from 'vue-router'
import type { TelemetryFilter } from './TelemetryFilter'

/**
 * What a traffic summary reads: the organization whose tenant holds the telemetry, null for the
 * platform's own, the filter narrowing it to an application or a workload, and the page with
 * the detail.
 */
export interface TrafficView {
  organizationId: string | null
  filter: TelemetryFilter
  to: RouteLocationRaw
}
