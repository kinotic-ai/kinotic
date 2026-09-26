package org.kinotic.auth;

import com.authzed.api.v1.CheckPermissionRequest;
import com.authzed.api.v1.CheckPermissionResponse;
import com.authzed.api.v1.Consistency;
import com.authzed.api.v1.ObjectReference;
import com.authzed.api.v1.PermissionsServiceGrpc;
import com.authzed.api.v1.Relationship;
import com.authzed.api.v1.RelationshipUpdate;
import com.authzed.api.v1.SubjectReference;
import com.authzed.api.v1.WriteRelationshipsRequest;
import com.authzed.api.v1.ZedToken;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The benchmark graph shared by the SpiceDB tests: an organization → application → tenant model
 * whose editor grants reach users only through {@value #DEPTH}-deep nested group chains. Even
 * chains hold an editor grant on {@code application:orders}; odd chains hold none.
 */
final class SpiceDbGraph {

    static final int CHAINS = 50;
    static final int DEPTH = 6;
    static final int USERS_PER_LEAF = 40;

    static final String SCHEMA = """
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

    static final Consistency MINIMIZE_LATENCY = Consistency.newBuilder().setMinimizeLatency(true).build();
    static final Consistency FULLY_CONSISTENT = Consistency.newBuilder().setFullyConsistent(true).build();

    private SpiceDbGraph() {}

    static Consistency atLeastAsFresh(ZedToken token) {
        return Consistency.newBuilder().setAtLeastAsFresh(token).build();
    }

    static String group(int chain, int level) { return "g" + chain + "_" + level; }
    static String user(int chain, int k) { return "u" + chain + "_" + k; }
    static boolean expectedEditor(int chain) { return chain % 2 == 0; }
    static SubjectReference topGroupOfChain(int chain) { return subject("group", group(chain, DEPTH - 1), "member"); }

    static ObjectReference obj(String type, String id) {
        return ObjectReference.newBuilder().setObjectType(type).setObjectId(id).build();
    }

    static SubjectReference subject(String type, String id, String relation) {
        SubjectReference.Builder b = SubjectReference.newBuilder().setObject(obj(type, id));
        if (relation != null) b.setOptionalRelation(relation);
        return b.build();
    }

    private static RelationshipUpdate update(RelationshipUpdate.Operation op, String type, String id, String relation, SubjectReference subject) {
        return RelationshipUpdate.newBuilder().setOperation(op)
                .setRelationship(Relationship.newBuilder().setResource(obj(type, id)).setRelation(relation).setSubject(subject))
                .build();
    }

    static RelationshipUpdate touch(String type, String id, String relation, SubjectReference subject) {
        return update(RelationshipUpdate.Operation.OPERATION_TOUCH, type, id, relation, subject);
    }

    static RelationshipUpdate delete(String type, String id, String relation, SubjectReference subject) {
        return update(RelationshipUpdate.Operation.OPERATION_DELETE, type, id, relation, subject);
    }

    static ZedToken write(PermissionsServiceGrpc.PermissionsServiceBlockingStub permissions, List<RelationshipUpdate> updates) {
        ZedToken last = null;
        for (int i = 0; i < updates.size(); i += 500) {
            last = permissions.writeRelationships(WriteRelationshipsRequest.newBuilder()
                    .addAllUpdates(updates.subList(i, Math.min(i + 500, updates.size()))).build()).getWrittenAt();
        }
        return last;
    }

    /**
     * Writes the whole graph and returns the token of the last write.
     */
    static ZedToken seed(PermissionsServiceGrpc.PermissionsServiceBlockingStub permissions) {
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
                updates.add(touch("application", "orders", "editor", topGroupOfChain(c)));
            }
            if (c % 3 == 0) {
                updates.add(touch("role", "orders_acme_approver", "assignee", topGroupOfChain(c)));
            }
            for (int k = 0; k < USERS_PER_LEAF; k++) {
                updates.add(touch("group", group(c, 0), "member", subject("user", user(c, k), null)));
            }
        }
        System.out.printf("SPICEDB seeded %,d relationships (%d chains x depth %d, %,d users)%n",
                updates.size(), CHAINS, DEPTH, CHAINS * USERS_PER_LEAF);
        return write(permissions, updates);
    }

    static boolean canModifyOrders(PermissionsServiceGrpc.PermissionsServiceBlockingStub permissions, String userId, Consistency consistency) {
        CheckPermissionResponse response = permissions.checkPermission(CheckPermissionRequest.newBuilder()
                .setConsistency(consistency)
                .setResource(obj("application", "orders"))
                .setPermission("modify")
                .setSubject(subject("user", userId, null))
                .build());
        return response.getPermissionship() == CheckPermissionResponse.Permissionship.PERMISSIONSHIP_HAS_PERMISSION;
    }

    // minimize_latency reads a quantized revision (a 5s window by default), so freshly written data
    // reaches it only once that window elapses; measurements must run at that steady state.
    static void awaitVisibleUnderMinimizeLatency(PermissionsServiceGrpc.PermissionsServiceBlockingStub permissions) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!canModifyOrders(permissions, user(0, 0), MINIMIZE_LATENCY)) {
            assertTrue(System.nanoTime() < deadline, "seed not visible under minimize_latency within 20s");
            Thread.sleep(250);
        }
    }
}
