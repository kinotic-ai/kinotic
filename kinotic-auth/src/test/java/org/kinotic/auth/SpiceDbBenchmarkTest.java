package org.kinotic.auth;

import com.authzed.api.v1.CheckPermissionRequest;
import com.authzed.api.v1.CheckPermissionResponse;
import com.authzed.api.v1.Consistency;
import com.authzed.api.v1.LookupResourcesRequest;
import com.authzed.api.v1.LookupResourcesResponse;
import com.authzed.api.v1.ObjectReference;
import com.authzed.api.v1.PermissionsServiceGrpc;
import com.authzed.api.v1.Relationship;
import com.authzed.api.v1.RelationshipUpdate;
import com.authzed.api.v1.SchemaServiceGrpc;
import com.authzed.api.v1.SubjectReference;
import com.authzed.api.v1.WriteRelationshipsRequest;
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

/**
 * Coarse-gate benchmark against a running SpiceDB. Seeds an organization → application → tenant
 * graph whose editor grants reach users only through {@value #DEPTH}-deep nested group chains, then
 * measures CheckPermission latency at that depth, throughput under concurrency, revoke propagation
 * observed through {@code at_least_as_fresh}, and LookupResources cost.
 * <p>
 * Skips when no SpiceDB is reachable. Endpoint and preshared key come from {@code SPICEDB_ENDPOINT}
 * (default {@code localhost:50051}) and {@code SPICEDB_TOKEN} (default {@code bench}).
 */
class SpiceDbBenchmarkTest {

    private static final int CHAINS = 50;          // independent nesting chains; even chains hold an editor grant
    private static final int DEPTH = 6;            // groups per chain, leaf (users) → top (grant)
    private static final int USERS_PER_LEAF = 40;  // 2,000 users
    private static final int SAMPLES = 2000;
    private static final int THREADS = 16;

    private static final String SCHEMA = """
            definition user {}

            definition group {
                relation member: user | group#member
            }

            definition organization {
                relation admin: user | group#member
                relation member: user | group#member
                permission manage_members = admin
            }

            definition application {
                relation org: organization
                relation admin: user | group#member
                relation editor: user | group#member
                permission modify = editor + admin + org->admin
                permission manage_users = admin + org->admin
            }

            definition tenant {
                relation app: application
                relation admin: user | group#member
                permission manage_users = admin + app->manage_users
            }

            definition role {
                relation scope: organization | application | tenant
                relation assignee: user | group#member
            }
            """;

    private static ManagedChannel channel;
    private static PermissionsServiceGrpc.PermissionsServiceBlockingStub permissions;
    private static ZedToken seededAt;

    @BeforeAll
    static void connectAndSeed() throws InterruptedException {
        String endpoint = System.getenv().getOrDefault("SPICEDB_ENDPOINT", "localhost:50051");
        PresharedKeyCredentials credentials = new PresharedKeyCredentials(System.getenv().getOrDefault("SPICEDB_TOKEN", "bench"));
        channel = ManagedChannelBuilder.forTarget(endpoint).usePlaintext().build();
        SchemaServiceGrpc.SchemaServiceBlockingStub schema = SchemaServiceGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        permissions = PermissionsServiceGrpc.newBlockingStub(channel).withCallCredentials(credentials);

        boolean reachable;
        try {
            schema.writeSchema(WriteSchemaRequest.newBuilder().setSchema(SCHEMA).build());
            reachable = true;
        } catch (StatusRuntimeException e) {
            reachable = false;
        }
        assumeTrue(reachable, "No SpiceDB reachable at " + endpoint + " — skipping benchmark");
        seededAt = seed();
        assertTrue(canModifyOrders(user(0, 0), Consistency.newBuilder().setAtLeastAsFresh(seededAt).build()),
                "graph model: u0_0 must reach the editor grant through the " + DEPTH + "-deep chain");
        awaitVisibleUnderMinimizeLatency();
    }

    // minimize_latency reads a quantized revision (a 5s window by default), so freshly written data
    // reaches it only once that window elapses; measurements must run at that steady state.
    private static void awaitVisibleUnderMinimizeLatency() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!canModifyOrders(user(0, 0), MINIMIZE_LATENCY)) {
            assertTrue(System.nanoTime() < deadline, "seed not visible under minimize_latency within 20s");
            Thread.sleep(250);
        }
    }

    @AfterAll
    static void disconnect() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ---------- graph ----------

    private static String group(int chain, int level) { return "g" + chain + "_" + level; }
    private static String user(int chain, int k) { return "u" + chain + "_" + k; }
    private static boolean expectedEditor(int chain) { return chain % 2 == 0; }

    private static ObjectReference obj(String type, String id) {
        return ObjectReference.newBuilder().setObjectType(type).setObjectId(id).build();
    }
    private static SubjectReference subject(String type, String id, String relation) {
        SubjectReference.Builder b = SubjectReference.newBuilder().setObject(obj(type, id));
        if (relation != null) b.setOptionalRelation(relation);
        return b.build();
    }
    private static RelationshipUpdate update(RelationshipUpdate.Operation op, String type, String id, String relation, SubjectReference subject) {
        return RelationshipUpdate.newBuilder().setOperation(op)
                .setRelationship(Relationship.newBuilder().setResource(obj(type, id)).setRelation(relation).setSubject(subject))
                .build();
    }
    private static RelationshipUpdate touch(String type, String id, String relation, SubjectReference subject) {
        return update(RelationshipUpdate.Operation.OPERATION_TOUCH, type, id, relation, subject);
    }
    private static RelationshipUpdate delete(String type, String id, String relation, SubjectReference subject) {
        return update(RelationshipUpdate.Operation.OPERATION_DELETE, type, id, relation, subject);
    }
    private static ZedToken write(List<RelationshipUpdate> updates) {
        ZedToken last = null;
        for (int i = 0; i < updates.size(); i += 500) {
            last = permissions.writeRelationships(WriteRelationshipsRequest.newBuilder()
                    .addAllUpdates(updates.subList(i, Math.min(i + 500, updates.size()))).build()).getWrittenAt();
        }
        return last;
    }

    private static ZedToken seed() {
        List<RelationshipUpdate> updates = new ArrayList<>();
        updates.add(touch("organization", "kinotic", "admin", subject("user", "orgadmin", null)));
        updates.add(touch("application", "orders", "org", subject("organization", "kinotic", null)));
        updates.add(touch("application", "inventory", "org", subject("organization", "kinotic", null)));
        updates.add(touch("tenant", "acme", "app", subject("application", "orders", null)));
        updates.add(touch("tenant", "globex", "app", subject("application", "orders", null)));
        for (int c = 0; c < CHAINS; c++) {
            for (int level = 0; level < DEPTH - 1; level++) {
                updates.add(touch("group", group(c, level + 1), "member", subject("group", group(c, level), "member")));
            }
            if (expectedEditor(c)) {
                updates.add(touch("application", "orders", "editor", subject("group", group(c, DEPTH - 1), "member")));
            }
            if (c % 3 == 0) {
                updates.add(touch("role", "orders_acme_approver", "assignee", subject("group", group(c, DEPTH - 1), "member")));
            }
            for (int k = 0; k < USERS_PER_LEAF; k++) {
                updates.add(touch("group", group(c, 0), "member", subject("user", user(c, k), null)));
            }
        }
        System.out.printf("SPICEDB seeded %,d relationships (%d chains x depth %d, %,d users)%n",
                updates.size(), CHAINS, DEPTH, CHAINS * USERS_PER_LEAF);
        return write(updates);
    }

    // ---------- checks ----------

    private static final Consistency MINIMIZE_LATENCY = Consistency.newBuilder().setMinimizeLatency(true).build();
    private static final Consistency FULLY_CONSISTENT = Consistency.newBuilder().setFullyConsistent(true).build();

    private static boolean canModifyOrders(String userId, Consistency consistency) {
        CheckPermissionResponse response = permissions.checkPermission(CheckPermissionRequest.newBuilder()
                .setConsistency(consistency)
                .setResource(obj("application", "orders"))
                .setPermission("modify")
                .setSubject(subject("user", userId, null))
                .build());
        return response.getPermissionship() == CheckPermissionResponse.Permissionship.PERMISSIONSHIP_HAS_PERMISSION;
    }

    private static long[] sampleLatencies(Consistency consistency, int samples) {
        long[] nanos = new long[samples];
        for (int i = 0; i < samples; i++) {
            int chain = i % CHAINS, k = (i / CHAINS) % USERS_PER_LEAF;
            long start = System.nanoTime();
            boolean allowed = canModifyOrders(user(chain, k), consistency);
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
        System.out.println("SPICEDB check depth=" + DEPTH + " minimize_latency : " + percentiles(sampleLatencies(MINIMIZE_LATENCY, SAMPLES)) + "  (n=" + SAMPLES + ")");
        System.out.println("SPICEDB check depth=" + DEPTH + " fully_consistent : " + percentiles(sampleLatencies(FULLY_CONSISTENT, SAMPLES)) + "  (n=" + SAMPLES + ")");
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
                        if (canModifyOrders(user(chain, k), MINIMIZE_LATENCY)) allowed++;
                    }
                    return allowed;
                }));
            }
            int allowed = 0;
            for (Future<Integer> f : futures) allowed += f.get();
            long nanos = System.nanoTime() - start;
            long total = (long) THREADS * perThread;
            System.out.printf("SPICEDB throughput %d threads : %,d checks in %,d ms -> %,.0f checks/sec (%,d allowed)%n",
                    THREADS, total, nanos / 1_000_000, total / (nanos / 1e9), allowed);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void revokeIsVisibleOnTheNextRequest() throws InterruptedException {
        String victim = user(0, 0);
        assertTrue(canModifyOrders(victim, FULLY_CONSISTENT), "precondition: chain 0 holds an editor grant");
        SubjectReference topGroup = subject("group", group(0, DEPTH - 1), "member");

        long start = System.nanoTime();
        ZedToken revokedAt = write(List.of(delete("application", "orders", "editor", topGroup)));
        boolean afterRevoke = canModifyOrders(victim, Consistency.newBuilder().setAtLeastAsFresh(revokedAt).build());
        long nanos = System.nanoTime() - start;
        assertFalse(afterRevoke, "revoke must be visible to a check at_least_as_fresh(written_at)");
        System.out.printf("SPICEDB revoke -> consistent check : %.0f us (write + at_least_as_fresh check)%n", nanos / 1000.0);

        ZedToken restoredAt = write(List.of(touch("application", "orders", "editor", topGroup)));
        assertTrue(canModifyOrders(victim, Consistency.newBuilder().setAtLeastAsFresh(restoredAt).build()));
        awaitVisibleUnderMinimizeLatency();
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
        System.out.println("SPICEDB lookupResources(application#modify) : " + percentiles(nanos) + "  (n=" + runs + ", results=" + found + ")");
    }
}
