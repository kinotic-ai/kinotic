import { seriesColor } from '../../charts/chartTheme'

/** A stable accent per service name, so the same service reads the same across a trace. */
export function serviceColor(service: string, dark: boolean): string {
    let hash = 0
    for (const char of service) {
        hash = (hash * 31 + char.charCodeAt(0)) >>> 0
    }
    return seriesColor(hash, dark)
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

export function formatRate(perSecond: number): string {
    return perSecond >= 10 ? `${perSecond.toFixed(0)}/s` : `${perSecond.toFixed(2)}/s`
}

export function formatPercent(fraction: number): string {
    return `${(fraction * 100).toFixed(fraction * 100 < 10 ? 2 : 1)}%`
}
