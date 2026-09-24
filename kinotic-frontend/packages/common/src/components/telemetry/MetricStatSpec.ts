import type { RouteLocationRaw } from 'vue-router'
import type { ChartAccent } from '../../charts/chartTheme'
import type { MetricSeries } from './MetricSeries'
import type { TimeRange } from './TimeRange'

/** One statistic a metric series feeds: how it reads the series, and how its tile reads the result. */
export interface MetricStatSpec {
  label: string
  /** What the value measures, shown beside its peak. */
  description: string
  /** Reads the series over the range; the statistic shows the first. */
  read: (range: TimeRange) => Promise<MetricSeries[]>
  format: (value: number) => string
  icon: string
  /** The icon chip's accent for the latest value. */
  accent: (latest: number) => ChartAccent
  /** Where the tile leads. */
  to?: RouteLocationRaw
}
