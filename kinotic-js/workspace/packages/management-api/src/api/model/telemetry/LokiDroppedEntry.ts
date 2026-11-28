/** A log line a Loki tail dropped, identified by its labels and time. */
export interface LokiDroppedEntry {

    /** The labels of the line's stream. */
    labels: Record<string, string>

    /** The line's time, epoch nanoseconds as a decimal string. */
    timestamp: string
}
