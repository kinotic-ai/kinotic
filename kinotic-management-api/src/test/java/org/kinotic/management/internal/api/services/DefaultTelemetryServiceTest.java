package org.kinotic.management.internal.api.services;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultSystemParticipant;
import org.kinotic.management.api.model.MetricQuery;
import org.kinotic.management.api.model.TraceQuery;
import org.kinotic.management.api.services.MimirClient;
import org.kinotic.management.api.services.TempoClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Covers {@link DefaultTelemetryService} authorization and tenant resolution: organization
 * participants read their own organization's tenant whether they name it or not and no other,
 * system participants read any organization's and the platform's when they name none.
 */
class DefaultTelemetryServiceTest {

    private static final Participant ACME_USER =
            new DefaultOrganizationParticipant("user-1", "acme", Map.of(), List.of("USER"));
    private static final Participant PLATFORM_OPERATOR =
            new DefaultSystemParticipant("operator-1", Map.of(), List.of("ADMIN"));

    private static SecurityContext securityContext;
    private static Vertx vertx;

    private final RecordingTempoClient tempoClient = new RecordingTempoClient();
    private final RecordingMimirClient mimirClient = new RecordingMimirClient();
    private final DefaultTelemetryService service = new DefaultTelemetryService(tempoClient, mimirClient, securityContext);

    @BeforeAll
    static void setup() {
        // SecurityContext registers its ContextLocal at class load, which must happen
        // before any Vertx instance is created
        securityContext = new SecurityContext();
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void tearDown() {
        vertx.close();
    }

    @Test
    void organizationParticipantSearchesItsOwnTenantWhenNamingIt() throws Throwable {
        callAs(ACME_USER, () -> service.searchTraces(traceQuery("acme")));

        assertEquals("acme", tempoClient.tenant);
        assertEquals("{ status = error }", tempoClient.query);
        assertEquals(1_000L, tempoClient.start);
        assertEquals(2_000L, tempoClient.end);
        assertEquals(20, tempoClient.limit);
    }

    @Test
    void organizationParticipantSearchesItsOwnTenantWhenNamingNone() throws Throwable {
        callAs(ACME_USER, () -> service.searchTraces(traceQuery(null)));

        assertEquals("acme", tempoClient.tenant);
    }

    @Test
    void organizationParticipantMayNotReadAnotherOrganization() {
        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> service.searchTraces(traceQuery("globex"))));
        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> service.queryMetrics(metricQuery("globex"))));
        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> service.findTrace("globex", "abc123")));
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

        assertEquals(DefaultLogService.SYSTEM_TENANT, mimirClient.tenant);
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

    /**
     * Runs the call on a Vert.x context with the given participant bound, mirroring how the
     * gateway invokes published services.
     */
    private <T> T callAs(Participant participant, Supplier<Future<T>> call) throws Throwable {
        Promise<T> result = Promise.promise();
        Context context = vertx.getOrCreateContext();
        context.runOnContext(unused -> {
            securityContext.setParticipant(context, participant);
            try {
                call.get().onComplete(result);
            } catch (Throwable error) {
                result.fail(error);
            }
        });
        // await rethrows a failed future's raw cause, so failureOf sees the unwrapped exception
        return result.future().await(10, TimeUnit.SECONDS);
    }

    private <T> Throwable failureOf(Participant participant, Supplier<Future<T>> call) {
        try {
            callAs(participant, call);
            return null;
        } catch (Throwable error) {
            return error;
        }
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
