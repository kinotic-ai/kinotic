/**
 * Series colors: theme ramp steps validated for adjacent-pair separation and surface contrast in
 * both modes (dark uses the lighter 400 steps), cycled when a chart holds more series than hues.
 */
const SERIES_COLORS: ReadonlyArray<{ light: string; dark: string }> = [
    { light: '#0EA5E9', dark: '#38BDF8' },
    { light: '#7C3AED', dark: '#A78BFA' },
    { light: '#16A34A', dark: '#4ADE80' },
    { light: '#D97706', dark: '#FBBF24' },
    { light: '#DC2626', dark: '#F87171' },
    { light: '#0D9488', dark: '#2DD4BF' }
]

export function seriesColor(index: number, dark: boolean): string {
    const hue = SERIES_COLORS[index % SERIES_COLORS.length]!
    return dark ? hue.dark : hue.light
}

/** A stable color per service name, so the same service reads the same across a trace. */
export function serviceColor(service: string, dark: boolean): string {
    let hash = 0
    for (const char of service) {
        hash = (hash * 31 + char.charCodeAt(0)) >>> 0
    }
    return seriesColor(hash % SERIES_COLORS.length, dark)
}

export function formatDuration(ms: number): string {
    let ret: string
    if (ms < 1) {
        ret = `${(ms * 1000).toFixed(0)} µs`
    } else if (ms < 1000) {
        ret = `${ms < 10 ? ms.toFixed(2) : ms.toFixed(0)} ms`
    } else if (ms < 60_000) {
        ret = `${(ms / 1000).toFixed(2)} s`
    } else {
        ret = `${(ms / 60_000).toFixed(1)} min`
    }
    return ret
}

export function formatTime(epochMs: number): string {
    return new Date(epochMs).toLocaleTimeString('en-US', { hour12: false })
}

export function formatDateTime(epochMs: number): string {
    return new Date(epochMs).toLocaleString('en-US', { hour12: false })
}

export function formatRate(perSecond: number): string {
    return perSecond >= 10 ? `${perSecond.toFixed(0)}/s` : `${perSecond.toFixed(2)}/s`
}

export function formatPercent(fraction: number): string {
    return `${(fraction * 100).toFixed(fraction * 100 < 10 ? 2 : 1)}%`
}
