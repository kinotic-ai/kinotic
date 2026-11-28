import type { OtlpStatusCode } from '@/api/model/telemetry/otlp/OtlpStatusCode'
import type { OtlpStatusCodeName } from '@/api/model/telemetry/otlp/OtlpStatusCodeName'

/** The status of an OTLP span. */
export interface OtlpStatus {

    /** The status code, as its number or its name; absent when unset. */
    code?: OtlpStatusCode | OtlpStatusCodeName

    /** A description of the error, for an error status. */
    message?: string
}
