import type { OtlpKeyValue } from '@/api/model/telemetry/otlp/OtlpKeyValue'

/** An event recorded on an OTLP span at one moment, such as an exception. */
export interface OtlpSpanEvent {

    /** The event's name; `exception` for a recorded exception. */
    name?: string

    /** When the event happened, epoch nanoseconds as a decimal string. */
    timeUnixNano?: string

    attributes?: OtlpKeyValue[]

    droppedAttributesCount?: number
}
