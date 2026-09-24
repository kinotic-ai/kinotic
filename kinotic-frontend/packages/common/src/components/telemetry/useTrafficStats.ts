import type { TrafficView } from './TrafficView'
import { trafficQueries } from './telemetryApi'
import { formatDuration, formatPercent, formatRate } from './telemetryDisplay'
import { useMetricStats } from './useMetricStats'

/**
 * The headline traffic of a view over the last hour — calls per second, the share of them that
 * failed, and the 95th percentile call duration — as dashboard statistics, each leading to the
 * view's detail page. load() reads them again.
 */
export function useTrafficStats(view: () => TrafficView) {
  return useMetricStats(() => view().organizationId, range => {
    const { filter, to } = view()
    const queries = trafficQueries(filter, range)
    return [
      {
        label: 'Requests',
        description: 'calls per second',
        query: queries.requests,
        format: formatRate,
        icon: 'pi-bolt',
        accent: () => 'sky',
        to
      },
      {
        label: 'Errors',
        description: 'of calls failed',
        query: queries.errors,
        format: formatPercent,
        icon: 'pi-exclamation-triangle',
        accent: latest => latest > 0 ? 'red' : 'green',
        to
      },
      {
        label: 'Latency',
        description: '95th percentile',
        query: queries.latencyP95,
        format: seconds => formatDuration(seconds * 1000),
        icon: 'pi-stopwatch',
        accent: () => 'violet',
        to
      }
    ]
  })
}
