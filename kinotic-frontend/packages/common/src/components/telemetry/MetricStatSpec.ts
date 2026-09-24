import type { RouteLocationRaw } from 'vue-router'
import type { ChartAccent } from '../../charts/chartTheme'

/** One statistic a metric query feeds: the PromQL it reads, and how its tile reads the result. */
export interface MetricStatSpec {
  label: string
  /** What the value measures, shown beside its peak. */
  description: string
  /** The PromQL whose first series the statistic shows. */
  query: string
  format: (value: number) => string
  icon: string
  /** The icon chip's accent for the latest value. */
  accent: (latest: number) => ChartAccent
  /** Where the tile leads. */
  to?: RouteLocationRaw
}
