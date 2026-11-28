/** What an OTLP span represents, OTLP's SpanKind, by the number OTLP gives it. */
export enum OtlpSpanType {
    UNSPECIFIED = 0,
    /** An operation inside an application, with no remote side. */
    INTERNAL = 1,
    /** The handling of a remote request. */
    SERVER = 2,
    /** A request to a remote service. */
    CLIENT = 3,
    /** The sending of a message that a consumer handles later. */
    PRODUCER = 4,
    /** The handling of a message a producer sent. */
    CONSUMER = 5
}
