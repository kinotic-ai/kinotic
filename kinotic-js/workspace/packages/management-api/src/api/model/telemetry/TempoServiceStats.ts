/** How many spans of one trace a service emitted, and how many of them failed. */
export interface TempoServiceStats {

    spanCount?: number

    errorCount?: number
}
