import type { OtlpAnyValue } from '@/api/model/telemetry/otlp/OtlpAnyValue'

/** One OTLP attribute: a key and its value. */
export interface OtlpKeyValue {

    /** The attribute's name. */
    key: string

    /** The attribute's value. */
    value?: OtlpAnyValue
}
