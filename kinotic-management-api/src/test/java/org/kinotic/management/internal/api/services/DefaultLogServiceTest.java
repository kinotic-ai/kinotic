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
import org.kinotic.domain.api.model.log.LogQuery;
import org.kinotic.domain.api.model.workload.Workload;
import org.kinotic.domain.api.model.security.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.DefaultSystemParticipant;
import org.kinotic.domain.api.services.LokiClient;
import org.kinotic.domain.internal.api.repositories.WorkloadRepository;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Covers {@link DefaultLogService} authorization and tenant resolution: organization
 * participants may only read their own organization's workloads, system participants may
 * read any, and platform workloads (no organization) resolve to the system log tenant.
 */
class DefaultLogServiceTest {

    private static final Participant ACME_USER =
            new DefaultOrganizationParticipant("user-1", "acme", Map.of(), List.of("USER"));
    private static final Participant PLATFORM_OPERATOR =
            new DefaultSystemParticipant("operator-1", Map.of(), List.of("ADMIN"));

    private static SecurityContext securityContext;
    private static Vertx vertx;

    private final RecordingLokiClient lokiClient = new RecordingLokiClient();
    private final DefaultLogService service = new DefaultLogService(
            lokiClient, securityContext, new FakeWorkloadRepository(
                    workload("wl-acme", "acme"),
                    workload("wl-globex", "globex"),
                    workload("wl-platform", null)));

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
    void organizationParticipantReadsItsOwnWorkload() throws Throwable {
        callAs(ACME_USER, () -> service.history(query("wl-acme")));

        assertEquals("acme", lokiClient.tenant);
        assertEquals("{workload_id=\"wl-acme\"}", lokiClient.query);
        assertEquals(1_000L, lokiClient.start);
        assertEquals(2_000L, lokiClient.end);
        assertEquals(50, lokiClient.limit);
    }

    @Test
    void organizationParticipantMayNotReadAnotherOrganizationsWorkload() {
        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> service.history(query("wl-globex"))));
    }

    @Test
    void organizationParticipantMayNotReadPlatformWorkloads() {
        assertInstanceOf(AuthorizationException.class,
                         failureOf(ACME_USER, () -> service.history(query("wl-platform"))));
    }

    @Test
    void systemParticipantReadsAnyOrganizationsWorkload() throws Throwable {
        callAs(PLATFORM_OPERATOR, () -> service.history(query("wl-acme")));

        assertEquals("acme", lokiClient.tenant);
    }

    @Test
    void platformWorkloadsResolveToTheSystemTenant() throws Throwable {
        callAs(PLATFORM_OPERATOR, () -> service.history(query("wl-platform")));

        assertEquals(DefaultLogService.SYSTEM_LOG_TENANT, lokiClient.tenant);
    }

    @Test
    void unknownWorkloadFails() {
        assertInstanceOf(IllegalArgumentException.class,
                         failureOf(PLATFORM_OPERATOR, () -> service.history(query("wl-missing"))));
    }

    @Test
    void tailResolvesTheWorkloadTenantAndQuery() throws Throwable {
        callAs(ACME_USER, () -> Future.fromCompletionStage(service.tail("wl-acme").collectList().toFuture(),
                                                           vertx.getOrCreateContext()));

        assertEquals("acme", lokiClient.tenant);
        assertEquals("{workload_id=\"wl-acme\"}", lokiClient.query);
    }

    private static LogQuery query(String workloadId) {
        return new LogQuery().setWorkloadId(workloadId).setStart(1_000L).setEnd(2_000L).setLimit(50);
    }

    private static Workload workload(String id, String organizationId) {
        return new Workload("test", "alpine:latest").setId(id).setOrganizationId(organizationId);
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
    }

    /**
     * Serves the given workloads from findById without touching Elasticsearch.
     */
    private static class FakeWorkloadRepository extends WorkloadRepository {

        private final Map<String, Workload> workloads = new HashMap<>();

        FakeWorkloadRepository(Workload... entities) {
            super(null);
            for (Workload workload : entities) {
                workloads.put(workload.getId(), workload);
            }
        }

        @Override
        public Future<Workload> findById(String id) {
            // Completes on another thread like the real ES-backed repository, so any
            // SecurityContext read after this hop loses the Vert.x context and fails
            Promise<Workload> promise = Promise.promise();
            new Thread(() -> promise.complete(workloads.get(id))).start();
            return promise.future();
        }
    }
}
