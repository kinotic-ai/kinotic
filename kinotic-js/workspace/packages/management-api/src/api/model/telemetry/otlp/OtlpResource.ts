import type { OtlpKeyValue } from '@/api/model/telemetry/otlp/OtlpKeyValue'

/** What emitted telemetry, described by its attributes, such as service.name. */
export interface OtlpResource {

    attributes?: OtlpKeyValue[]

    /** How many attributes were discarded for exceeding a limit. */
    droppedAttributesCount?: number
}
