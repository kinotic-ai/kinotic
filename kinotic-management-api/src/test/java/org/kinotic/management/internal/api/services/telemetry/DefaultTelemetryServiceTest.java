package org.kinotic.management.internal.api.services.telemetry;

import org.kinotic.management.api.model.telemetry.TelemetryTenant;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.management.api.model.telemetry.MetricQuery;
import org.kinotic.management.api.model.telemetry.TraceQuery;
import org.kinotic.management.api.services.telemetry.MimirClient;
import org.kinotic.management.api.services.telemetry.TempoClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Covers {@link DefaultTelemetryService} authorization and tenant resolution: organization
 * participants read their own organization's tenant and no other, system participants read any
 * organization's and the platform's when they name none.
 */
class DefaultTelemetryServiceTest extends ParticipantCallTest {

    private final RecordingTempoClient tempoClient = new RecordingTempoClient();
    private final RecordingMimirClient mimirClient = new RecordingMimirClient();
    private final DefaultTelemetryService service = new DefaultTelemetryService(tempoClient, mimirClient, tenantAccess);

    @Test
    void organizationParticipantSearchesItsOwnTenant() throws Throwable {
        callAs(ACME_USER, () -> service.searchTraces(traceQuery("acme")));

        assertEquals("acme", tempoClient.tenant);
    }

    @Test
    void organizationParticipantMayNotReadAnotherOrganizationOrThePlatform() {
        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> service.searchTraces(traceQuery("globex"))));
        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> service.queryMetrics(metricQuery("globex"))));
        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> service.findTrace("globex", "abc123")));
        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> service.searchTraces(traceQuery(null))));
    }

    @Test
    void systemParticipantReadsAnyOrganization() throws Throwable {
        callAs(PLATFORM_OPERATOR, () -> service.findTrace("globex", "abc123"));

        assertEquals("globex", tempoClient.tenant);
    }

    @Test
    void systemParticipantReadsThePlatformTenantWhenNamingNone() throws Throwable {
        callAs(PLATFORM_OPERATOR, () -> service.queryMetrics(metricQuery(null)));

        assertEquals(TelemetryTenant.SYSTEM, mimirClient.tenant);
    }

    @Test
    void organizationParticipantQueriesMetricsInItsOwnTenant() throws Throwable {
        callAs(ACME_USER, () -> service.queryMetrics(metricQuery("acme")));

        assertEquals("acme", mimirClient.tenant);
    }

    @Test
    void blankQueriesAreRejected() {
        assertInstanceOf(IllegalArgumentException.class,
                         failureOf(ACME_USER, () -> service.searchTraces(traceQuery("acme").setQuery(" "))));
        assertInstanceOf(IllegalArgumentException.class,
                         failureOf(ACME_USER, () -> service.queryMetrics(metricQuery("acme").setStep(0))));
    }

    private static TraceQuery traceQuery(String organizationId) {
        return new TraceQuery().setOrganizationId(organizationId)
                               .setQuery("{ status = error }")
                               .setStart(1_000L)
                               .setEnd(2_000L)
                               .setLimit(20);
    }

    private static MetricQuery metricQuery(String organizationId) {
        return new MetricQuery().setOrganizationId(organizationId)
                                .setQuery("sum(rate(traces_spanmetrics_calls_total[1m]))")
                                .setStart(1_000L)
                                .setEnd(2_000L)
                                .setStep(15L);
    }

    private static class RecordingTempoClient implements TempoClient {

        String tenant;

        @Override
        public Future<Buffer> search(String tenant, String query, long start, long end, int limit) {
            this.tenant = tenant;
            return Future.succeededFuture(Buffer.buffer("traces"));
        }

        @Override
        public Future<Buffer> findTrace(String tenant, String traceId) {
            this.tenant = tenant;
            return Future.succeededFuture(Buffer.buffer("trace"));
        }
    }

    private static class RecordingMimirClient implements MimirClient {

        String tenant;

        @Override
        public Future<Buffer> queryRange(String tenant, String query, long start, long end, long step) {
            this.tenant = tenant;
            return Future.succeededFuture(Buffer.buffer("metrics"));
        }
    }
}
