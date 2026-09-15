/** A closed time range in epoch milliseconds, which every telemetry query is scoped to. */
export interface TimeRange {
    start: number
    end: number
}
