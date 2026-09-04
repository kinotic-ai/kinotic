

export default class DatetimeUtil {
    public static formatDateFromEpoch(timeInMills: number): string {
        let ret: string = ''
        if (timeInMills !== 0) {
            const [date, time] = new Date(Number(timeInMills)).toLocaleString('en-US', { hour12: false }).split(', ')
            ret = date + ' ' + time
        }
        return ret
    }

    public static formatDate(dateTime: string): string {
        let ret: string = ''
        if (dateTime?.length > 0) {
            const [date, time] = new Date(dateTime).toLocaleString('en-US', { hour12: false }).split(', ')
            ret = date + ' ' + time
        }
        return ret
    }
public static formatRelativeDate(dateStr: string | number | Date): string {
    if (!dateStr) return ''

    const date = new Date(dateStr)
    if (isNaN(date.getTime())) return ''

    const now = new Date()
    const diffTime = now.getTime() - date.getTime()
    const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24))

    if (diffDays === 0) return 'Today'
    if (diffDays === 1) return '1 day ago'
    return `${diffDays} days ago`
}
/** Renders epoch millis as the 24-hour locale time of day. */
public static formatTime(epochMillis: number): string {
    return new Date(epochMillis).toLocaleTimeString('en-US', { hour12: false })
}

/** Renders epoch millis as the locale date, or an em dash when absent. */
public static formatEpochDate(epochMillis: number | null): string {
    return epochMillis ? new Date(epochMillis).toLocaleDateString() : '—'
}

/**
 * Formats the elapsed time between two epoch timestamps, measuring against nowMs while
 * finished is absent. Returns an em dash when started is absent.
 */
public static formatDuration(started: number | null, finished: number | null, nowMs: number = Date.now()): string {
    let ret: string
    if (!started) {
        ret = '—'
    } else {
        const totalSeconds = Math.max(0, Math.floor(((finished ?? nowMs) - started) / 1000))
        const hours = Math.floor(totalSeconds / 3600)
        const minutes = Math.floor((totalSeconds % 3600) / 60)
        const seconds = totalSeconds % 60
        if (hours > 0) {
            ret = `${hours}h ${minutes}m`
        } else if (minutes > 0) {
            ret = `${minutes}m ${seconds}s`
        } else {
            ret = `${seconds}s`
        }
    }
    return ret
}

/** Renders epoch millis as the locale date and time, or an em dash when absent. */
public static formatEpochDateTime(epochMillis: number | null): string {
    return epochMillis ? new Date(epochMillis).toLocaleString() : '—'
}

public static formatMonthDayYear(dateStr: string | number | Date): string {
    if (!dateStr) return ''

    const date = new Date(dateStr)
    if (isNaN(date.getTime())) return ''

    return date.toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
    })
}

}