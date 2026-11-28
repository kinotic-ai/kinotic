import type { OtlpKeyValue } from '@/api/model/telemetry/otlp/OtlpKeyValue'

/** A map of OTLP attribute values, as its key-value pairs. */
export interface OtlpKeyValueList {

    /** The pairs. */
    values?: OtlpKeyValue[]
}
