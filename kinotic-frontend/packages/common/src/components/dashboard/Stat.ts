import type { RouteLocationRaw } from 'vue-router'
import type { ChartAccent } from '../../charts/chartTheme'

/** One dashboard statistic, as a StatTile shows it. */
export interface Stat {
  label: string
  value: string
  /** What the value measures. */
  description: string
  /** A Tag severity; given one, the value renders as a Tag instead of a number. */
  tag?: string
  /** Where the tile leads; given one, the tile is a link. */
  to?: RouteLocationRaw
  icon?: string
  accent?: ChartAccent
  /** The value over time as [epoch milliseconds, value] pairs, drawn as a sparkline under it. */
  trend?: Array<[number, number]>
  /** Renders a trend value in its unit, for the sparkline's tooltip. */
  format?: (value: number) => string
}
