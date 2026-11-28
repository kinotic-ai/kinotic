/** Log lines that share one label set. */
export interface LokiStream {

    /** The labels every line of the stream carries. */
    stream: Record<string, string>

    /** The lines, each its time in epoch nanoseconds as a decimal string and the line itself. */
    values: [string, string][]
}
