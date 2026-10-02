package org.kinotic.auth;

import com.authzed.api.v1.CheckPermissionRequest;
import com.authzed.api.v1.CheckPermissionResponse;
import com.authzed.api.v1.Consistency;
import com.authzed.api.v1.PermissionsServiceGrpc;
import com.authzed.api.v1.WatchRequest;
import com.authzed.api.v1.WatchResponse;
import com.authzed.api.v1.WatchServiceGrpc;
import com.authzed.api.v1.ZedToken;
import io.grpc.ManagedChannel;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.kinotic.auth.SpiceDbGraph.obj;
import static org.kinotic.auth.SpiceDbGraph.subject;

/**
 * Prototype of the in-JVM coarse gate: decisions are cached per (subject, resource, permission),
 * a miss is resolved by SpiceDB at least as fresh as the last change the cache has seen, and a
 * Watch stream invalidates the cache whenever the relationship graph changes.
 */
final class MaterializedPermissionCache implements AutoCloseable {

    private final PermissionsServiceGrpc.PermissionsServiceBlockingStub permissions;
    private final Map<String, Boolean> decisions = new ConcurrentHashMap<>();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong invalidations = new AtomicLong();
    private volatile ZedToken freshness;
    private volatile long lastInvalidationNanos;
    private volatile boolean closed;

    MaterializedPermissionCache(ManagedChannel channel, PresharedKeyCredentials credentials, ZedToken startCursor) {
        this.permissions = PermissionsServiceGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        this.freshness = startCursor;
        WatchServiceGrpc.WatchServiceBlockingStub watch = WatchServiceGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        Thread watcher = new Thread(() -> consume(watch, startCursor), "spicedb-watch");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void consume(WatchServiceGrpc.WatchServiceBlockingStub watch, ZedToken cursor) {
        try {
            Iterator<WatchResponse> events = watch.watch(WatchRequest.newBuilder().setOptionalStartCursor(cursor).build());
            while (!closed && events.hasNext()) {
                WatchResponse event = events.next();
                if (event.getUpdatesCount() == 0) {
                    continue; // checkpoints carry no relationship changes
                }
                // Coarse but correct: any graph change drops every decision; the next miss re-resolves
                // at least as fresh as the change that caused it.
                decisions.clear();
                freshness = event.getChangesThrough();
                lastInvalidationNanos = System.nanoTime();
                invalidations.incrementAndGet();
            }
        } catch (RuntimeException e) {
            if (!closed) {
                throw e;
            }
        }
    }

    boolean isAllowed(String userId, String resourceType, String resourceId, String permission) {
        String key = userId + '|' + resourceType + '|' + resourceId + '|' + permission;
        Boolean cached = decisions.get(key);
        boolean ret;
        if (cached != null) {
            ret = cached;
        } else {
            misses.incrementAndGet();
            CheckPermissionResponse response = permissions.checkPermission(CheckPermissionRequest.newBuilder()
                    .setConsistency(Consistency.newBuilder().setAtLeastAsFresh(freshness))
                    .setResource(obj(resourceType, resourceId))
                    .setPermission(permission)
                    .setSubject(subject("user", userId, null))
                    .build());
            ret = response.getPermissionship() == CheckPermissionResponse.Permissionship.PERMISSIONSHIP_HAS_PERMISSION;
            decisions.put(key, ret);
        }
        return ret;
    }

    long misses() { return misses.get(); }
    long invalidations() { return invalidations.get(); }
    long lastInvalidationNanos() { return lastInvalidationNanos; }
    int size() { return decisions.size(); }

    @Override
    public void close() {
        closed = true;
    }
}
