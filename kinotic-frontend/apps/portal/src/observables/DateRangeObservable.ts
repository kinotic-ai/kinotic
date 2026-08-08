export interface DateRange {
  startDate: Date | null
  endDate: Date | null
}

export class DateRangeObservable {
  private subscribers: Set<(dateRange: DateRange) => void> = new Set()
  private currentDateRange: DateRange = {
    startDate: null,
    endDate: null
  }

  subscribe(callback: (dateRange: DateRange) => void): () => void {
    this.subscribers.add(callback)
    callback(this.currentDateRange)
    return () => {
      this.subscribers.delete(callback)
    }
  }

  updateDateRange(dateRange: DateRange) {
    this.currentDateRange = dateRange
    this.subscribers.forEach(callback => callback(dateRange))
  }

  getCurrentDateRange(): DateRange {
    return { ...this.currentDateRange }
  }
}

declare global {
  interface Window {
    globalDateRangeObservable: DateRangeObservable
  }
}
