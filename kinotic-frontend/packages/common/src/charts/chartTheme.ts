/**
 * The chart accents: theme ramp steps that pass the palette checks against the chart surface in
 * both modes — each within the mode's lightness band, above the chroma floor and at 3:1 contrast
 * or more, and each series distinguishable from its neighbours with normal vision and with
 * protanopia or deuteranopia.
 */
export const CHART_ACCENTS = {
    sky: { light: '#0284C7', dark: '#0284C7' },
    violet: { light: '#7C3AED', dark: '#7C3AED' },
    green: { light: '#16A34A', dark: '#15803D' },
    amber: { light: '#D97706', dark: '#D97706' },
    red: { light: '#B91C1C', dark: '#DC2626' },
    teal: { light: '#0D9488', dark: '#0D9488' }
} as const

export type ChartAccent = keyof typeof CHART_ACCENTS

// The order is part of the colour-blind safety: the separation is measured between neighbours in
// it. Red and amber come after the first three, so a chart of up to three series never draws one
// in a warning colour.
const ACCENT_ORDER: ChartAccent[] = ['sky', 'violet', 'green', 'red', 'teal', 'amber']

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

/**
 * A legend below the plot, dot-marked and in muted text, as every chart draws its own. It keeps to
 * one row, paging through the entries the row cannot hold.
 */
export function chartLegend(dark: boolean): Record<string, unknown> {
    return {
        // A wrapped second row would draw over the time axis, which the grid leaves one row below
        type: 'scroll',
        bottom: 0,
        left: 0,
        icon: 'circle',
        itemWidth: 10,
        itemHeight: 10,
        itemGap: 16,
        textStyle: { color: chartTextColor(dark) },
        pageIconSize: 10,
        pageIconColor: chartTextColor(dark),
        pageIconInactiveColor: chartGridColor(dark),
        pageTextStyle: { color: chartTextColor(dark) }
    }
}
