package org.kinotic.gateway.internal.endpoints.stomp;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.participant.ParticipantScope;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.management.api.model.InvocationMetrics;
import org.kinotic.management.api.model.InvocationOutcome;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Records the invocations clients make through the gateway in the {@link InvocationMetrics} histogram:
 * each one's time to first reply, the organization and application its caller acts for, and how it
 * ended.
 */
@Component
public class InvocationMeter {

    private static final AttributeKey<String> ORGANIZATION_ID = AttributeKey.stringKey(InvocationMetrics.ORGANIZATION_ID);
    private static final AttributeKey<String> APPLICATION_ID = AttributeKey.stringKey(InvocationMetrics.APPLICATION_ID);
    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey(InvocationMetrics.OUTCOME);
    // The boundaries the OpenTelemetry semantic conventions give request durations in seconds
    private static final List<Double> BUCKETS = List.of(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0);

    private final DoubleHistogram durations;

    public InvocationMeter(OpenTelemetry openTelemetry) {
        this.durations = openTelemetry.getMeter("org.kinotic.gateway")
                                      .histogramBuilder(InvocationMetrics.INSTRUMENT_NAME)
                                      .setDescription("Time from a client's invocation to its first reply")
                                      .setUnit("s")
                                      .setExplicitBucketBoundariesAdvice(BUCKETS)
                                      .build();
    }

    /**
     * Records one invocation.
     *
     * @param caller        the participant the invocation was sent as
     * @param outcome       how the invocation ended
     * @param durationNanos the time from the invocation to its first reply, or to its end when no reply came
     */
    public void record(Participant caller, InvocationOutcome outcome, long durationNanos) {
        AttributesBuilder attributes = Attributes.builder().put(OUTCOME, outcome.label());
        if (caller instanceof ScopedParticipant scoped) {
            ParticipantScope scope = scoped.getScope();
            if (scope.organizationId() != null) {
                attributes.put(ORGANIZATION_ID, scope.organizationId());
            }
            if (scope.applicationId() != null) {
                attributes.put(APPLICATION_ID, scope.applicationId());
            }
        }
        durations.record(durationNanos / 1e9, attributes.build());
    }
}
