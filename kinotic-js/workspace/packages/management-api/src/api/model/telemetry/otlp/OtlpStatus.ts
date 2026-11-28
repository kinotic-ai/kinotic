/** The status of an OTLP span. */
export interface OtlpStatus {

    /** The status code, as its number or its name: 0 or `STATUS_CODE_UNSET`, 1 or `STATUS_CODE_OK`, 2 or `STATUS_CODE_ERROR`. */
    code?: number | string

    /** A description of the error, for an error status. */
    message?: string
}
