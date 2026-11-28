import type { OtlpResource } from '@/api/model/telemetry/otlp/OtlpResource'
import type { OtlpScopeSpans } from '@/api/model/telemetry/otlp/OtlpScopeSpans'

/** The spans one resource, such as a service instance, emitted. */
export interface OtlpResourceSpans {

    /** The resource that emitted the spans. */
    resource?: OtlpResource

    /** The spans, grouped by the instrumentation scope that recorded them. */
    scopeSpans?: OtlpScopeSpans[]

    /** The schema the resource's attributes follow. */
    schemaUrl?: string
}
