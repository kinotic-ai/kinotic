import { computed, ref } from 'vue'

import type { Stat } from '../dashboard/Stat'
import type { MetricSeries } from './MetricSeries'
import type { MetricStatSpec } from './MetricStatSpec'
import type { TimeRange } from './TimeRange'
import { finitePoints, latestValue, queryMetrics, rangeEndingNow } from './telemetryApi'

/** How far back a metric statistic looks. */
const STAT_WINDOW_MS = 60 * 60_000

/** One statistic's series, or the reason it has none. */
interface Reading {
  series: MetricSeries | undefined
  failed: boolean
}

/**
 * Dashboard statistics fed by metric queries over the last hour, one per spec: each shows its
 * query's latest value, its peak, and its course over the hour as a sparkline. The organization
 * is whose tenant the queries read, null for the platform's own; the specs are built for the
 * hour being read, whose length their rate windows follow. load() reads them again.
 */
export function useMetricStats(organizationId: () => string | null, specs: (range: TimeRange) => MetricStatSpec[]) {
  const shown = ref<MetricStatSpec[]>(specs(rangeEndingNow(STAT_WINDOW_MS)))
  const readings = ref<Reading[]>([])
  const loaded = ref(false)

  // A query that fails leaves its statistic reading unavailable rather than taking the others with it
  async function read(query: string, range: TimeRange): Promise<Reading> {
    let ret: Reading
    try {
      ret = { series: (await queryMetrics(organizationId(), query, range))[0], failed: false }
    } catch {
      ret = { series: undefined, failed: true }
    }
    return ret
  }

  async function load(): Promise<void> {
    const range = rangeEndingNow(STAT_WINDOW_MS)
    const current = specs(range)
    readings.value = await Promise.all(current.map(spec => read(spec.query, range)))
    shown.value = current
    loaded.value = true
  }

  function stat(spec: MetricStatSpec, reading: Reading | undefined): Stat {
    const points = finitePoints(reading?.series)
    const latest = latestValue(reading?.series)
    let value: string
    let description: string
    if (!loaded.value) {
      value = '—'
      description = spec.description
    } else if (reading?.failed) {
      value = '—'
      description = 'Metrics unavailable'
    } else if (latest === null) {
      value = '—'
      description = 'No data in the last hour'
    } else {
      value = spec.format(latest)
      description = `${spec.description} · peak ${spec.format(Math.max(...points.map(([, point]) => point)))}`
    }
    return {
      label: spec.label,
      value,
      description,
      to: spec.to,
      icon: spec.icon,
      accent: latest === null ? 'sky' : spec.accent(latest),
      trend: points,
      format: spec.format
    }
  }

  const stats = computed<Stat[]>(() => shown.value.map((spec, index) => stat(spec, readings.value[index])))

  return { stats, load }
}
