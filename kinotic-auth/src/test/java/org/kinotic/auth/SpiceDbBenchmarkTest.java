package org.kinotic.auth;

import com.authzed.api.v1.Consistency;
import com.authzed.api.v1.LookupResourcesRequest;
import com.authzed.api.v1.LookupResourcesResponse;
import com.authzed.api.v1.PermissionsServiceGrpc;
import com.authzed.api.v1.SchemaServiceGrpc;
import com.authzed.api.v1.SubjectReference;
import com.authzed.api.v1.WriteSchemaRequest;
import com.authzed.api.v1.ZedToken;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.kinotic.auth.SpiceDbGraph.CHAINS;
import static org.kinotic.auth.SpiceDbGraph.DEPTH;
import static org.kinotic.auth.SpiceDbGraph.FULLY_CONSISTENT;
import static org.kinotic.auth.SpiceDbGraph.MINIMIZE_LATENCY;
import static org.kinotic.auth.SpiceDbGraph.USERS_PER_LEAF;
import static org.kinotic.auth.SpiceDbGraph.atLeastAsFresh;
import static org.kinotic.auth.SpiceDbGraph.awaitVisibleUnderMinimizeLatency;
import static org.kinotic.auth.SpiceDbGraph.canModifyOrders;
import static org.kinotic.auth.SpiceDbGraph.delete;
import static org.kinotic.auth.SpiceDbGraph.expectedEditor;
import static org.kinotic.auth.SpiceDbGraph.subject;
import static org.kinotic.auth.SpiceDbGraph.topGroupOfChain;
import static org.kinotic.auth.SpiceDbGraph.touch;
import static org.kinotic.auth.SpiceDbGraph.user;
import static org.kinotic.auth.SpiceDbGraph.write;

/**
 * Coarse-gate benchmark against a running SpiceDB using the {@link SpiceDbGraph}: CheckPermission
 * latency at depth, throughput under concurrency, revoke propagation observed through
 * {@code at_least_as_fresh}, and LookupResources cost.
 * <p>
 * Skips when no SpiceDB is reachable. {@code SPICEDB_ENDPOINT} (default {@code localhost:50051}),
 * {@code SPICEDB_TOKEN} (default {@code bench}) and {@code SPICEDB_LABEL} (default {@code memory},
 * printed with every result to name the datastore under test) come from the environment.
 */
class SpiceDbBenchmarkTest {

    private static final int SAMPLES = 2000;
    private static final int THREADS = 16;

    private static ManagedChannel channel;
    private static PermissionsServiceGrpc.PermissionsServiceBlockingStub permissions;
    private static String label;

    @BeforeAll
    static void connectAndSeed() throws InterruptedException {
        String endpoint = System.getenv().getOrDefault("SPICEDB_ENDPOINT", "localhost:50051");
        label = "SPICEDB[" + System.getenv().getOrDefault("SPICEDB_LABEL", "memory") + "]";
        PresharedKeyCredentials credentials = new PresharedKeyCredentials(System.getenv().getOrDefault("SPICEDB_TOKEN", "bench"));
        channel = ManagedChannelBuilder.forTarget(endpoint).usePlaintext().build();
        SchemaServiceGrpc.SchemaServiceBlockingStub schema = SchemaServiceGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        permissions = PermissionsServiceGrpc.newBlockingStub(channel).withCallCredentials(credentials);

        boolean reachable;
        try {
            schema.writeSchema(WriteSchemaRequest.newBuilder().setSchema(SpiceDbGraph.SCHEMA).build());
            reachable = true;
        } catch (StatusRuntimeException e) {
            reachable = false;
        }
        assumeTrue(reachable, "No SpiceDB reachable at " + endpoint + " — skipping benchmark");
        ZedToken seededAt = SpiceDbGraph.seed(permissions);
        assertTrue(canModifyOrders(permissions, user(0, 0), atLeastAsFresh(seededAt)),
                "graph model: u0_0 must reach the editor grant through the " + DEPTH + "-deep chain");
        awaitVisibleUnderMinimizeLatency(permissions);
    }

    @AfterAll
    static void disconnect() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static long[] sampleLatencies(Consistency consistency, int samples) {
        long[] nanos = new long[samples];
        for (int i = 0; i < samples; i++) {
            int chain = i % CHAINS, k = (i / CHAINS) % USERS_PER_LEAF;
            long start = System.nanoTime();
            boolean allowed = canModifyOrders(permissions, user(chain, k), consistency);
            nanos[i] = System.nanoTime() - start;
            assertEquals(expectedEditor(chain), allowed, "wrong decision for " + user(chain, k));
        }
        Arrays.sort(nanos);
        return nanos;
    }

    private static String percentiles(long[] sorted) {
        return String.format("p50=%.0f us  p95=%.0f us  p99=%.0f us  max=%.0f us",
                sorted[sorted.length / 2] / 1000.0, sorted[(int) (sorted.length * 0.95)] / 1000.0,
                sorted[(int) (sorted.length * 0.99)] / 1000.0, sorted[sorted.length - 1] / 1000.0);
    }

    @Test
    void checkLatencyAtDepth() {
        sampleLatencies(MINIMIZE_LATENCY, 500); // warm caches
        System.out.println(label + " check depth=" + DEPTH + " minimize_latency : " + percentiles(sampleLatencies(MINIMIZE_LATENCY, SAMPLES)) + "  (n=" + SAMPLES + ")");
        System.out.println(label + " check depth=" + DEPTH + " fully_consistent : " + percentiles(sampleLatencies(FULLY_CONSISTENT, SAMPLES)) + "  (n=" + SAMPLES + ")");
    }

    @Test
    void checkLatencyRepeatedSubjects() {
        // Production traffic re-checks the same subjects many times within a quantization window;
        // this measures that cache-hit path rather than the first-touch cost sampled above.
        String[] subjects = {user(0, 0), user(1, 0), user(2, 0), user(3, 0)};
        for (int i = 0; i < 200; i++) {
            canModifyOrders(permissions, subjects[i % subjects.length], MINIMIZE_LATENCY);
        }
        long[] nanos = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            int chain = i % subjects.length;
            long start = System.nanoTime();
            boolean allowed = canModifyOrders(permissions, subjects[chain], MINIMIZE_LATENCY);
            nanos[i] = System.nanoTime() - start;
            assertEquals(expectedEditor(chain), allowed, "wrong decision for " + subjects[chain]);
        }
        Arrays.sort(nanos);
        System.out.println(label + " check depth=" + DEPTH + " repeated subjects (cache hit) : " + percentiles(nanos) + "  (n=" + SAMPLES + ", 4 subjects)");
    }

    @Test
    void throughputUnderConcurrency() throws Exception {
        sampleLatencies(MINIMIZE_LATENCY, 500);
        int perThread = 1500;
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            long start = System.nanoTime();
            List<Future<Integer>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                final int seed = t;
                futures.add(pool.submit(() -> {
                    int allowed = 0;
                    for (int i = 0; i < perThread; i++) {
                        int chain = (seed + i) % CHAINS, k = i % USERS_PER_LEAF;
                        if (canModifyOrders(permissions, user(chain, k), MINIMIZE_LATENCY)) allowed++;
                    }
                    return allowed;
                }));
            }
            int allowed = 0;
            for (Future<Integer> f : futures) allowed += f.get();
            long nanos = System.nanoTime() - start;
            long total = (long) THREADS * perThread;
            System.out.printf("%s throughput %d threads : %,d checks in %,d ms -> %,.0f checks/sec (%,d allowed)%n",
                    label, THREADS, total, nanos / 1_000_000, total / (nanos / 1e9), allowed);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void revokeIsVisibleOnTheNextRequest() throws InterruptedException {
        String victim = user(0, 0);
        assertTrue(canModifyOrders(permissions, victim, FULLY_CONSISTENT), "precondition: chain 0 holds an editor grant");
        SubjectReference topGroup = topGroupOfChain(0);

        long start = System.nanoTime();
        ZedToken revokedAt = write(permissions, List.of(delete("application", "orders", "editor", topGroup)));
        boolean afterRevoke = canModifyOrders(permissions, victim, atLeastAsFresh(revokedAt));
        long nanos = System.nanoTime() - start;
        assertFalse(afterRevoke, "revoke must be visible to a check at_least_as_fresh(written_at)");
        System.out.printf("%s revoke -> consistent check : %.0f us (write + at_least_as_fresh check)%n", label, nanos / 1000.0);

        ZedToken restoredAt = write(permissions, List.of(touch("application", "orders", "editor", topGroup)));
        assertTrue(canModifyOrders(permissions, victim, atLeastAsFresh(restoredAt)));
        awaitVisibleUnderMinimizeLatency(permissions);
    }

    @Test
    void lookupResourcesLatency() {
        int runs = 200;
        long[] nanos = new long[runs];
        int found = 0;
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            Iterator<LookupResourcesResponse> results = permissions.lookupResources(LookupResourcesRequest.newBuilder()
                    .setConsistency(MINIMIZE_LATENCY)
                    .setResourceObjectType("application")
                    .setPermission("modify")
                    .setSubject(subject("user", user(0, 0), null))
                    .build());
            found = 0;
            while (results.hasNext()) { results.next(); found++; }
            nanos[i] = System.nanoTime() - start;
        }
        Arrays.sort(nanos);
        assertEquals(1, found, "u0_0 may modify exactly one application (orders)");
        System.out.println(label + " lookupResources(application#modify) : " + percentiles(nanos) + "  (n=" + runs + ", results=" + found + ")");
    }
}
