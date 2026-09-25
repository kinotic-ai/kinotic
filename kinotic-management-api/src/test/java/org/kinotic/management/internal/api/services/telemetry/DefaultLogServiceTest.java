package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.management.api.model.telemetry.LogQuery;
import org.kinotic.management.api.model.telemetry.TelemetryTenant;
import org.kinotic.management.api.services.LokiClient;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Covers {@link DefaultLogService} authorization and tenant resolution: organization
 * participants may only read their own organization's logs, system participants may read any
 * organization's, and the platform's own (no organization) resolve to the system tenant. No
 * workload record takes part, so a destroyed workload's logs read the same way.
 */
class DefaultLogServiceTest extends ParticipantCallTest {

    private final RecordingLokiClient lokiClient = new RecordingLokiClient();
    private final DefaultLogService service = new DefaultLogService(lokiClient, tenantAccess);

    @Test
    void organizationParticipantReadsItsOwnWorkload() throws Throwable {
        callAs(ACME_USER, () -> service.history(query("acme", "wl-acme")));

        assertEquals("acme", lokiClient.tenant);
        assertEquals("{workload_id=\"wl-acme\"}", lokiClient.query);
        assertEquals(1_000L, lokiClient.start);
        assertEquals(2_000L, lokiClient.end);
        assertEquals(50, lokiClient.limit);
    }

    @Test
    void organizationParticipantMayNotReadAnotherOrganizationsWorkload() {
        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> service.history(query("globex", "wl-globex"))));
    }

    @Test
    void organizationParticipantMayNotReadPlatformWorkloads() {
        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> service.history(query(null, "wl-platform"))));
    }

    @Test
    void systemParticipantReadsAnyOrganizationsWorkload() throws Throwable {
        callAs(PLATFORM_OPERATOR, () -> service.history(query("acme", "wl-acme")));

        assertEquals("acme", lokiClient.tenant);
    }

    @Test
    void platformWorkloadsResolveToTheSystemTenant() throws Throwable {
        callAs(PLATFORM_OPERATOR, () -> service.history(query(null, "wl-platform")));

        assertEquals(TelemetryTenant.SYSTEM, lokiClient.tenant);
    }

    @Test
    void workloadIdIsQuotedIntoTheSelector() throws Throwable {
        callAs(ACME_USER, () -> service.history(query("acme", "wl\"} or {tenant=\"globex")));

        assertEquals("{workload_id=\"wl\\\"} or {tenant=\\\"globex\"}", lokiClient.query);
    }

    @Test
    void tailResolvesTheTenantAndQuery() throws Throwable {
        callAs(ACME_USER, () -> Future.fromCompletionStage(service.tail("acme", "wl-acme").collectList().toFuture(),
                                                           vertx.getOrCreateContext()));

        assertEquals("acme", lokiClient.tenant);
        assertEquals("{workload_id=\"wl-acme\"}", lokiClient.query);
    }

    @Test
    void tailOfAnotherOrganizationIsRefused() {
        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> Future.fromCompletionStage(service.tail("globex", "wl-globex").collectList().toFuture(),
                                                                               vertx.getOrCreateContext())));
    }

    private static LogQuery query(String organizationId, String workloadId) {
        return new LogQuery().setOrganizationId(organizationId).setWorkloadId(workloadId).setStart(1_000L).setEnd(2_000L).setLimit(50);
    }

    private static class RecordingLokiClient implements LokiClient {

        String tenant;
        String query;
        long start;
        long end;
        int limit;

        @Override
        public Future<Buffer> queryRange(String tenant, String query, long start, long end, int limit) {
            this.tenant = tenant;
            this.query = query;
            this.start = start;
            this.end = end;
            this.limit = limit;
            return Future.succeededFuture(Buffer.buffer("history"));
        }

        @Override
        public Flux<Buffer> tail(String tenant, String query) {
            this.tenant = tenant;
            this.query = query;
            return Flux.empty();
        }

        @Override
        public Future<Void> delete(String tenant, String query, long start, long end) {
            throw new UnsupportedOperationException();
        }
    }
}
