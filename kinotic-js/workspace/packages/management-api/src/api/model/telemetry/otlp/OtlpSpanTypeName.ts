/** An {@link OtlpSpanType} by the name OTLP gives it, as Tempo writes span kinds. */
export type OtlpSpanTypeName =
    | 'SPAN_KIND_UNSPECIFIED'
    | 'SPAN_KIND_INTERNAL'
    | 'SPAN_KIND_SERVER'
    | 'SPAN_KIND_CLIENT'
    | 'SPAN_KIND_PRODUCER'
    | 'SPAN_KIND_CONSUMER'
