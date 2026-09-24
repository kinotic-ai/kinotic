package org.kinotic.core.api.reconcile;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;

/**
 * What the reconcile master needs from the repository of one kind of {@link Reconcilable} record
 * beside what every watched repository gives it: the records not in their desired state, and every
 * record, for the one look the master takes at each when it starts.
 *
 * @param <R> the kind of record
 */
public interface ReconcilableRepository<R extends Reconcilable<?>> extends WatchedRepository<R> {

    /**
     * @param pageable the page to return
     * @return the records that are not in their desired state
     */
    Future<Page<R>> findUnreconciled(Pageable pageable);

    /**
     * @param pageable the page to return
     * @return every record of this kind
     */
    Future<Page<R>> findAll(Pageable pageable);
}
