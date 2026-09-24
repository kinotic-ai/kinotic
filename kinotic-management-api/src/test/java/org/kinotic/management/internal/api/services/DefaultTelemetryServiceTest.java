package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.management.api.model.MetricQuery;
import org.kinotic.management.api.model.TraceQuery;
import org.kinotic.management.api.model.TrafficQuery;
import org.kinotic.management.api.model.TrafficSignal;
import org.kinotic.management.api.services.MimirClient;
import org.kinotic.management.api.services.TempoClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Covers {@link DefaultTelemetryService} authorization and tenant resolution: organization
 * participants read their own organization's tenant and no other, system participants read any
 * organization's and the platform's when they name none. Traffic is read from the platform tenant
 * with an expression selecting only the organization the caller may read.
 */
class DefaultTelemetryServiceTest extends ParticipantCallTest {

    private final RecordingTempoClient tempoClient = new RecordingTempoClient();
    private final RecordingMimirClient mimirClient = new RecordingMimirClient();
    private final DefaultTelemetryService service = new DefaultTelemetryService(tempoClient, mimirClient, tenantAccess);

    @Test
    void organizationParticipantSearchesItsOwnTenant() throws Throwable {
        callAs(ACME_USER, () -> service.searchTraces(traceQuery("acme")));

        assertEquals("acme", tempoClient.tenant);
        assertEquals("{ status = error }", tempoClient.query);
        assertEquals(1_000L, tempoClient.start);
        assertEquals(2_000L, tempoClient.end);
        assertEquals(20, tempoClient.limit);
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
        assertEquals("abc123", tempoClient.traceId);
    }

    @Test
    void systemParticipantReadsThePlatformTenantWhenNamingNone() throws Throwable {
        callAs(PLATFORM_OPERATOR, () -> service.queryMetrics(metricQuery(null)));

        assertEquals(TenantAccess.SYSTEM_TENANT, mimirClient.tenant);
    }

    @Test
    void metricQueryPassesItsExpressionRangeAndStep() throws Throwable {
        callAs(ACME_USER, () -> service.queryMetrics(metricQuery("acme")));

        assertEquals("acme", mimirClient.tenant);
        assertEquals("sum(rate(traces_spanmetrics_calls_total[1m]))", mimirClient.query);
        assertEquals(1_000L, mimirClient.start);
        assertEquals(2_000L, mimirClient.end);
        assertEquals(15L, mimirClient.step);
    }

    @Test
    void blankQueriesAreRejected() {
        assertInstanceOf(IllegalArgumentException.class,
                         failureOf(ACME_USER, () -> service.searchTraces(traceQuery("acme").setQuery(" "))));
        assertInstanceOf(IllegalArgumentException.class,
                         failureOf(ACME_USER, () -> service.queryMetrics(metricQuery("acme").setStep(0))));
    }

    @Test
    void organizationTrafficIsReadFromThePlatformTenantForTheCallersOrganization() throws Throwable {
        callAs(ACME_USER, () -> service.queryTraffic(trafficQuery("acme", null, TrafficSignal.REQUESTS)));

        assertEquals(TenantAccess.SYSTEM_TENANT, mimirClient.tenant);
        assertEquals("sum(rate(kinotic_gateway_invocation_duration_seconds_count{organization_id=\"acme\"}[60s]))",
                     mimirClient.query);
        assertEquals(1_000L, mimirClient.start);
        assertEquals(2_000L, mimirClient.end);
        assertEquals(15L, mimirClient.step);
    }

    @Test
    void applicationTrafficNarrowsToItsApplicationAndCountsFailuresOverEveryCall() throws Throwable {
        callAs(ACME_USER, () -> service.queryTraffic(trafficQuery("acme", "shop", TrafficSignal.ERRORS)));

        assertEquals("(sum(rate(kinotic_gateway_invocation_duration_seconds_count"
                     + "{organization_id=\"acme\", application_id=\"shop\", outcome=~\"error|unavailable\"}[60s])) or vector(0))"
                     + " / sum(rate(kinotic_gateway_invocation_duration_seconds_count{organization_id=\"acme\", application_id=\"shop\"}[60s]))",
                     mimirClient.query);
    }

    @Test
    void systemParticipantReadsEveryInvocationWhenNamingNoOrganization() throws Throwable {
        callAs(PLATFORM_OPERATOR, () -> service.queryTraffic(trafficQuery(null, null, TrafficSignal.LATENCY_P95)));

        assertEquals(TenantAccess.SYSTEM_TENANT, mimirClient.tenant);
        assertEquals("histogram_quantile(0.95, sum by (le) (rate(kinotic_gateway_invocation_duration_seconds_bucket[60s])))",
                     mimirClient.query);
    }

    @Test
    void organizationParticipantMayNotReadAnotherOrganizationsTrafficOrThePlatforms() {
        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> service.queryTraffic(trafficQuery("globex", null, TrafficSignal.REQUESTS))));
        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> service.queryTraffic(trafficQuery(null, null, TrafficSignal.REQUESTS))));
    }

    @Test
    void trafficIdsMustBeZoneLabelsAndAnApplicationNeedsItsOrganization() {
        assertInstanceOf(IllegalArgumentException.class,
                         failureOf(ACME_USER, () -> service.queryTraffic(trafficQuery("acme\"} or vector(1) #", null, TrafficSignal.REQUESTS))));
        assertInstanceOf(IllegalArgumentException.class,
                         failureOf(ACME_USER, () -> service.queryTraffic(trafficQuery("acme", "Shop", TrafficSignal.REQUESTS))));
        assertInstanceOf(IllegalArgumentException.class,
                         failureOf(PLATFORM_OPERATOR, () -> service.queryTraffic(trafficQuery(null, "shop", TrafficSignal.REQUESTS))));
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

    private static TrafficQuery trafficQuery(String organizationId, String applicationId, TrafficSignal signal) {
        return new TrafficQuery().setOrganizationId(organizationId)
                                 .setApplicationId(applicationId)
                                 .setSignal(signal)
                                 .setStart(1_000L)
                                 .setEnd(2_000L)
                                 .setStep(15L);
    }

    private static class RecordingTempoClient implements TempoClient {

        String tenant;
        String query;
        String traceId;
        long start;
        long end;
        int limit;

        @Override
        public Future<Buffer> search(String tenant, String query, long start, long end, int limit) {
            this.tenant = tenant;
            this.query = query;
            this.start = start;
            this.end = end;
            this.limit = limit;
            return Future.succeededFuture(Buffer.buffer("traces"));
        }

        @Override
        public Future<Buffer> findTrace(String tenant, String traceId) {
            this.tenant = tenant;
            this.traceId = traceId;
            return Future.succeededFuture(Buffer.buffer("trace"));
        }
    }

    private static class RecordingMimirClient implements MimirClient {

        String tenant;
        String query;
        long start;
        long end;
        long step;

        @Override
        public Future<Buffer> queryRange(String tenant, String query, long start, long end, long step) {
            this.tenant = tenant;
            this.query = query;
            this.start = start;
            this.end = end;
            this.step = step;
            return Future.succeededFuture(Buffer.buffer("metrics"));
        }
    }
}
