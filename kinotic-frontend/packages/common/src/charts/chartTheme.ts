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
