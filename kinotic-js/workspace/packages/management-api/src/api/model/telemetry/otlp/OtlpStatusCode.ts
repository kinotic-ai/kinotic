/** The outcome of an OTLP span, by the number OTLP gives it. */
export enum OtlpStatusCode {
    /** No outcome was recorded. */
    UNSET = 0,
    /** The operation was marked as succeeded. */
    OK = 1,
    /** The operation failed. */
    ERROR = 2
}
