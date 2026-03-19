

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