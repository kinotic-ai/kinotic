package org.kinotic.management.api.model;

/**
 * The histogram the gateway records the invocations clients make in, by the names it is written and
 * read under: one point per invocation that awaits a reply, holding its time to first reply in
 * seconds, attributed to the organization and application its caller acts for and to how it ended.
 */
public class InvocationMetrics {

    /** The OpenTelemetry instrument name. */
    public static final String INSTRUMENT_NAME = "kinotic.gateway.invocation.duration";

    /** The instrument's series name in Mimir, with the unit suffix its OTLP translation adds. */
    public static final String SERIES_NAME = "kinotic_gateway_invocation_duration_seconds";

    /** The attribute naming the caller's organization; absent for a platform participant. */
    public static final String ORGANIZATION_ID = "organization_id";

    /** The attribute naming the caller's application; absent unless the caller acts for one. */
    public static final String APPLICATION_ID = "application_id";

    /** The attribute holding the {@link InvocationOutcome#label()} of how the invocation ended. */
    public static final String OUTCOME = "outcome";
}
