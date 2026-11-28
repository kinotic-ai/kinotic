import type { OtlpAnyValue } from '@/api/model/telemetry/otlp/OtlpAnyValue'

/** A list of OTLP attribute values. */
export interface OtlpArrayValue {

    /** The values, in order. */
    values?: OtlpAnyValue[]
}
