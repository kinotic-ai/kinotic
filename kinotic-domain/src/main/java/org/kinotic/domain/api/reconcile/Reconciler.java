package org.kinotic.domain.api.reconcile;

import io.vertx.core.Future;

/**
 * The worker for one kind of {@link Reconcilable} record: given the record as it stands, acts to bring
 * the world to what the record should be, reports what it observed through the record's repository,
 * and says when it wants to be called again. The reconcile master calls it when the record changes,
 * when something the record made changes, when the record is found not in its desired state, and
 * when it asked to be called again; never twice at once for the same record.
 *
 * @param <R> the kind of record
 */
public interface Reconciler<R extends Reconcilable<?>> {

    /**
     * @return the kind of record this worker reconciles
     */
    WatchedType type();

    /**
     * Acts on the record as it stands. A failed future is retried with backoff.
     *
     * @param current the record, read just before the call
     * @return when to be called again for the record
     */
    Future<Requeue> reconcile(R current);
}
