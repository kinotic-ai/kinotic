package org.kinotic.auth;

import com.authzed.api.v1.PermissionsServiceGrpc;
import com.authzed.api.v1.SchemaServiceGrpc;
import com.authzed.api.v1.WriteSchemaRequest;
import com.authzed.api.v1.ZedToken;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.kinotic.auth.SpiceDbGraph.CHAINS;
import static org.kinotic.auth.SpiceDbGraph.USERS_PER_LEAF;
import static org.kinotic.auth.SpiceDbGraph.atLeastAsFresh;
import static org.kinotic.auth.SpiceDbGraph.canModifyOrders;
import static org.kinotic.auth.SpiceDbGraph.delete;
import static org.kinotic.auth.SpiceDbGraph.expectedEditor;
import static org.kinotic.auth.SpiceDbGraph.topGroupOfChain;
import static org.kinotic.auth.SpiceDbGraph.touch;
import static org.kinotic.auth.SpiceDbGraph.user;
import static org.kinotic.auth.SpiceDbGraph.write;

/**
 * Measures the in-JVM coarse gate prototype ({@link MaterializedPermissionCache}) against a running
 * SpiceDB: hot-path lookup cost, the cost of filling the cache, and how long a revoke takes to
 * propagate through the Watch stream into a denied decision. Uses the same environment variables
 * as {@link SpiceDbBenchmarkTest} and skips when no SpiceDB is reachable.
 */
class MaterializedCacheBenchmarkTest {

    private static ManagedChannel channel;
    private static PermissionsServiceGrpc.PermissionsServiceBlockingStub permissions;
    private static MaterializedPermissionCache cache;
    private static String label;

    @BeforeAll
    static void connectSeedAndWatch() {
        String endpoint = System.getenv().getOrDefault("SPICEDB_ENDPOINT", "localhost:50051");
        label = "MATERIALIZED[" + System.getenv().getOrDefault("SPICEDB_LABEL", "memory") + "]";
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
        assertTrue(canModifyOrders(permissions, user(0, 0), atLeastAsFresh(seededAt)), "graph model: u0_0 must reach the editor grant");
        cache = new MaterializedPermissionCache(channel, credentials, seededAt);
    }

    @AfterAll
    static void disconnect() throws InterruptedException {
        if (cache != null) cache.close();
        if (channel != null) channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private static boolean allowed(String userId) {
        return cache.isAllowed(userId, "application", "orders", "modify");
    }

    @Test
    void hotPathLatency() {
        long fillStart = System.nanoTime();
        int filled = 0;
        for (int c = 0; c < CHAINS; c++) {
            for (int k = 0; k < USERS_PER_LEAF; k++) {
                assertEquals(expectedEditor(c), allowed(user(c, k)), "wrong decision for " + user(c, k));
                filled++;
            }
        }
        long fillNanos = System.nanoTime() - fillStart;
        System.out.printf("%s fill : %,d decisions resolved in %,d ms -> %.0f us per miss (session-start cost)%n",
                label, filled, fillNanos / 1_000_000, fillNanos / 1000.0 / filled);

        int iterations = 200_000;
        long start = System.nanoTime();
        int hits = 0;
        for (int i = 0; i < iterations; i++) {
            if (allowed(user(i % CHAINS, (i / CHAINS) % USERS_PER_LEAF))) hits++;
        }
        long nanos = System.nanoTime() - start;
        System.out.printf("%s hot path : %,d lookups in %,d ms -> %.3f us/check, %,.0f checks/sec (%,d allowed, cache size %,d)%n",
                label, iterations, nanos / 1_000_000, nanos / 1000.0 / iterations, iterations / (nanos / 1e9), hits, cache.size());
    }

    @Test
    void revokePropagatesThroughWatch() throws InterruptedException {
        String victim = user(0, 0);
        assertTrue(allowed(victim), "precondition: chain 0 holds an editor grant");
        long invalidationsBefore = cache.invalidations();

        long writeStart = System.nanoTime();
        write(permissions, List.of(delete("application", "orders", "editor", topGroupOfChain(0))));
        long writeReturned = System.nanoTime();

        long deadline = writeStart + TimeUnit.SECONDS.toNanos(15);
        while (cache.invalidations() == invalidationsBefore && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertTrue(cache.invalidations() > invalidationsBefore, "Watch must deliver the revoke");
        long watchApplied = cache.lastInvalidationNanos();

        long deniedAt = 0;
        while (System.nanoTime() < deadline) {
            if (!allowed(victim)) { deniedAt = System.nanoTime(); break; }
            Thread.sleep(1);
        }
        assertTrue(deniedAt > 0, "revoke must become a denied decision");
        System.out.printf("%s revoke : write returned +%.1f ms, Watch invalidation +%.1f ms, first denied check +%.1f ms (all measured from the write call)%n",
                label, (writeReturned - writeStart) / 1e6, (watchApplied - writeStart) / 1e6, (deniedAt - writeStart) / 1e6);

        write(permissions, List.of(touch("application", "orders", "editor", topGroupOfChain(0))));
        long restoreDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!allowed(victim) && System.nanoTime() < restoreDeadline) {
            Thread.sleep(1);
        }
        assertTrue(allowed(victim), "restore must become an allowed decision");
    }
}
