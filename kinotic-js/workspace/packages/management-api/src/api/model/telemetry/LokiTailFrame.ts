import type { LokiDroppedEntry } from '@/api/model/telemetry/LokiDroppedEntry'
import type { LokiStream } from '@/api/model/telemetry/LokiStream'

/** One frame of a Loki live tail: the lines that arrived since the last frame. */
export interface LokiTailFrame {

    /** The new lines, by stream. */
    streams?: LokiStream[]

    /** The entries Loki dropped because the tail fell behind; they remain readable through a history. */
    dropped_entries?: LokiDroppedEntry[]
}
