import { TrafficSignal } from '@kinotic-ai/management-api'

import type { TimeRange } from './TimeRange'
import type { TrafficView } from './TrafficView'
import { queryTraffic } from './telemetryApi'
import { formatDuration, formatPercent, formatRate } from './telemetryDisplay'
import { useMetricStats } from './useMetricStats'

/**
 * The headline traffic of a view over the last hour, from the invocations its callers made through
 * the gateway — calls per second, the share of them that failed, and the 95th percentile time to
 * first reply — as dashboard statistics, each leading to the view's detail page. load() reads them
 * again.
 */
export function useTrafficStats(view: () => TrafficView) {
  return useMetricStats(() => {
    const { organizationId, applicationId, to } = view()
    const read = (signal: TrafficSignal) => (range: TimeRange) => queryTraffic(organizationId, applicationId, signal, range)
    return [
      {
        label: 'Requests',
        description: 'calls per second',
        read: read(TrafficSignal.REQUESTS),
        format: formatRate,
        icon: 'pi-bolt',
        accent: () => 'sky',
        to
      },
      {
        label: 'Errors',
        description: 'of calls failed',
        read: read(TrafficSignal.ERRORS),
        format: formatPercent,
        icon: 'pi-exclamation-triangle',
        accent: latest => latest > 0 ? 'red' : 'green',
        to
      },
      {
        label: 'Latency',
        description: '95th percentile',
        read: read(TrafficSignal.LATENCY_P95),
        format: seconds => formatDuration(seconds * 1000),
        icon: 'pi-stopwatch',
        accent: () => 'violet',
        to
      }
    ]
  })
}
