/**
 * The chart accents: theme ramp steps validated for adjacent-pair separation and surface
 * contrast in both modes (dark uses the lighter 400 steps).
 */
export const CHART_ACCENTS = {
    sky: { light: '#0EA5E9', dark: '#38BDF8' },
    violet: { light: '#7C3AED', dark: '#A78BFA' },
    green: { light: '#16A34A', dark: '#4ADE80' },
    amber: { light: '#D97706', dark: '#FBBF24' },
    red: { light: '#DC2626', dark: '#F87171' },
    teal: { light: '#0D9488', dark: '#2DD4BF' }
} as const

export type ChartAccent = keyof typeof CHART_ACCENTS

const ACCENT_ORDER: ChartAccent[] = ['sky', 'violet', 'green', 'amber', 'red', 'teal']

export function accentColor(accent: ChartAccent, dark: boolean): string {
    return dark ? CHART_ACCENTS[accent].dark : CHART_ACCENTS[accent].light
}

/** The accent of the n-th series of a chart, cycling once a chart holds more series than accents. */
export function seriesColor(index: number, dark: boolean): string {
    return accentColor(ACCENT_ORDER[index % ACCENT_ORDER.length]!, dark)
}

/** The preset's muted text token, so chart text matches the captions around it. */
export function chartTextColor(dark: boolean): string {
    return dark ? '#A1A1AA' : '#71717A'
}

/** The surface border token, for axis lines and grid lines. */
export function chartGridColor(dark: boolean): string {
    return dark ? '#27272A' : '#E4E4E7'
}

/** A legend below the plot, dot-marked and in muted text, as every chart draws its own. */
export function chartLegend(dark: boolean): Record<string, unknown> {
    return {
        bottom: 0,
        left: 0,
        icon: 'circle',
        itemWidth: 10,
        itemHeight: 10,
        itemGap: 16,
        textStyle: { color: chartTextColor(dark) }
    }
}

/** One series' entry of an axis tooltip, as ECharts passes it to the formatter. */
interface AxisTooltipParam {
    seriesName?: string
    color?: string
    value?: unknown
    axisValue?: unknown
    axisValueLabel?: string
}

export interface ChartTooltipOptions {
    /** Renders a series' value; integers as they are when omitted. */
    format?: (value: number) => string
    /** Renders the hovered axis value as the tooltip's heading; the axis label when omitted. */
    heading?: (axisValue: unknown) => string
    /** Adds a total row, for stacked series whose sum means something. */
    totalLabel?: string
    /** A shaded band over the hovered category, for bars; a line otherwise. */
    pointer?: 'line' | 'shadow'
}

function escapeHtml(text: string): string {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

/** One line of a tooltip card: an optional colour swatch, a name, and its value. */
export interface ChartTooltipRow {
    name: string
    value: string
    color?: string
}

// The card every tooltip draws: a heading over a divider, then one row per entry and an optional total
function tooltipCard(dark: boolean, heading: string, rows: ChartTooltipRow[], total?: ChartTooltipRow): string {
    const text = dark ? '#FAFAFA' : '#18181B'
    const muted = chartTextColor(dark)
    const divider = chartGridColor(dark)
    const line = (entry: ChartTooltipRow, strong: boolean) =>
        `<div style="display:flex;align-items:center;gap:8px;padding:3px 0">`
        + (entry.color ? `<span style="width:8px;height:8px;border-radius:2px;flex:none;background:${entry.color}"></span>` : '')
        + `<span style="flex:1;color:${strong ? text : muted}">${escapeHtml(entry.name)}</span>`
        + `<span style="font-weight:600;font-variant-numeric:tabular-nums;color:${text}">${escapeHtml(entry.value)}</span></div>`
    const totalLine = total
        ? `<div style="margin-top:4px;padding-top:4px;border-top:1px solid ${divider}">${line(total, true)}</div>`
        : ''
    return `<div style="min-width:180px;font-size:12px;line-height:1.4">`
        + `<div style="padding:8px 12px;border-bottom:1px solid ${divider};font-weight:600;color:${text}">${escapeHtml(heading)}</div>`
        + `<div style="padding:6px 12px 8px">${rows.map(entry => line(entry, false)).join('')}${totalLine}</div></div>`
}

// The tooltip box ECharts draws around the card
function tooltipFrame(dark: boolean): Record<string, unknown> {
    return {
        confine: true,
        padding: 0,
        backgroundColor: dark ? '#18181B' : '#FFFFFF',
        borderColor: dark ? '#3F3F46' : '#E4E4E7',
        borderWidth: 1,
        textStyle: { fontFamily: 'inherit' },
        extraCssText: 'border-radius:10px;box-shadow:0 10px 28px -8px rgba(0,0,0,0.22);'
    }
}

/**
 * The axis tooltip every chart shares: a card with the hovered day or time as its heading and
 * one row per series (swatch, name, value), with an optional total.
 */
export function chartTooltip(dark: boolean, options: ChartTooltipOptions = {}): Record<string, unknown> {
    const border = dark ? '#3F3F46' : '#E4E4E7'
    const format = options.format ?? ((value: number) => String(value))
    // A series with no value at the hovered point reads as zero rather than ECharts' dash
    const numberOf = (param: AxisTooltipParam): number => {
        const raw = Array.isArray(param.value) ? param.value[1] : param.value
        return typeof raw === 'number' && Number.isFinite(raw) ? raw : 0
    }
    return {
        ...tooltipFrame(dark),
        trigger: 'axis',
        axisPointer: options.pointer === 'shadow'
            ? { type: 'shadow', shadowStyle: { color: dark ? 'rgba(255,255,255,0.05)' : 'rgba(24,24,27,0.05)' } }
            : { type: 'line', lineStyle: { color: border, type: 'dashed' } },
        formatter: (raw: AxisTooltipParam | AxisTooltipParam[]) => {
            const params = Array.isArray(raw) ? raw : [raw]
            const first = params[0]
            const heading = first ? (options.heading ? options.heading(first.axisValue) : first.axisValueLabel ?? '') : ''
            const rows = params.map(param => ({ name: param.seriesName ?? '', value: format(numberOf(param)), color: param.color }))
            const total = options.totalLabel && params.length > 1
                ? { name: options.totalLabel, value: format(params.reduce((sum, param) => sum + numberOf(param), 0)) }
                : undefined
            return tooltipCard(dark, heading, rows, total)
        }
    }
}

/** The same card for a chart whose hover says one fixed thing, such as a single gauge. */
export function chartItemTooltip(dark: boolean, heading: string, rows: ChartTooltipRow[]): Record<string, unknown> {
    return {
        ...tooltipFrame(dark),
        // A gauge is a few pixels tall inside a clipped card, so the card attaches to the page instead
        confine: false,
        appendTo: 'body',
        trigger: 'item',
        formatter: () => tooltipCard(dark, heading, rows)
    }
}
