package org.kinotic.os.internal.api.services;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.log.LogConstants;
import org.kinotic.domain.api.model.log.LogQuery;
import org.kinotic.domain.api.model.workload.Workload;
import org.kinotic.domain.api.security.DefaultOrganizationParticipant;
import org.kinotic.domain.api.security.DefaultSystemParticipant;
import org.kinotic.domain.api.services.LokiClient;
import org.kinotic.domain.api.services.WorkloadService;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
            lokiClient, securityContext, new FakeWorkloadService(
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
        Buffer result = callAs(ACME_USER, () -> service.history(query("wl-acme")));

        assertNotNull(result);
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

        assertEquals(LogConstants.SYSTEM_LOG_TENANT, lokiClient.tenant);
    }

    @Test
    void unknownWorkloadFails() {
        assertInstanceOf(IllegalArgumentException.class,
                         failureOf(PLATFORM_OPERATOR, () -> service.history(query("wl-missing"))));
    }

    @Test
    void tailResolvesTheWorkloadTenantAndQuery() throws Throwable {
        List<Buffer> frames = callAs(ACME_USER, () -> service.tail("wl-acme").collectList().toFuture());

        assertEquals(1, frames.size());
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
    private <T> T callAs(Participant participant, Supplier<CompletableFuture<T>> call) throws Throwable {
        CompletableFuture<T> result = new CompletableFuture<>();
        Context context = vertx.getOrCreateContext();
        context.runOnContext(unused -> {
            securityContext.setParticipant(context, participant);
            try {
                call.get().whenComplete((value, error) -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                    } else {
                        result.complete(value);
                    }
                });
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        try {
            return result.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw unwrap(e.getCause());
        }
    }

    private <T> Throwable failureOf(Participant participant, Supplier<CompletableFuture<T>> call) {
        try {
            callAs(participant, call);
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private static Throwable unwrap(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
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
            return Flux.just(Buffer.buffer("frame"));
        }
    }

    /**
     * Serves the given workloads from findById; every other operation is unsupported.
     */
    private static class FakeWorkloadService implements WorkloadService {

        private final Map<String, Workload> workloads = new HashMap<>();

        FakeWorkloadService(Workload... entities) {
            for (Workload workload : entities) {
                workloads.put(workload.getId(), workload);
            }
        }

        @Override
        public CompletableFuture<Workload> findById(String id) {
            // Completes on another thread like the real ES-backed service, so any
            // SecurityContext read after this hop loses the Vert.x context and fails
            return CompletableFuture.supplyAsync(() -> workloads.get(id));
        }

        @Override
        public CompletableFuture<Page<Workload>> findAllForNode(String nodeId, Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Long> countForNode(String nodeId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Workload> create(Workload entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Workload> createSync(Workload entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Workload> save(Workload entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Workload> saveSync(Workload entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Long> count() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> deleteById(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> deleteByIdSync(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Page<Workload>> findAll(Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Page<Workload>> search(String searchText, Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> syncIndex() {
            throw new UnsupportedOperationException();
        }
    }
}
