import type { OtlpArrayValue } from '@/api/model/telemetry/otlp/OtlpArrayValue'
import type { OtlpKeyValueList } from '@/api/model/telemetry/otlp/OtlpKeyValueList'

/** An OTLP attribute value: exactly one of its fields is present. */
export interface OtlpAnyValue {

    stringValue?: string

    boolValue?: boolean

    /** An int64, as a number or, past the safe integer range, as its decimal string. */
    intValue?: number | string

    doubleValue?: number

    arrayValue?: OtlpArrayValue

    kvlistValue?: OtlpKeyValueList

    /** Bytes, base64 encoded. */
    bytesValue?: string
}
