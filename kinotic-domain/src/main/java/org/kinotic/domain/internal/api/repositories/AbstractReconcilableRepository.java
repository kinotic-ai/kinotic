package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Reconcilable;
import org.kinotic.domain.api.repositories.ReconcilableRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

/**
 * {@link AbstractWatchedRepository} for {@link Reconcilable} records: adds what the master reads of
 * them and the writes to a record's intent and observation, each one shard operation that enters
 * the ledger.
 *
 * @param <T> the kind of record
 * @param <S> the shape of a record's desired and observed state
 */
public abstract class AbstractReconcilableRepository<T extends Reconcilable<S>, S> extends AbstractWatchedRepository<T>
        implements ReconcilableRepository<T> {

    protected final ReconcileStateRepository reconcileStateRepository;

    protected AbstractReconcilableRepository(WatchedIndex watched,
                                             Class<T> type,
                                             CrudServiceTemplate crudServiceTemplate,
                                             WatchedStateRepository watchedStateRepository,
                                             WatchEventRepository watchEventRepository,
                                             ReconcileStateRepository reconcileStateRepository) {
        super(watched, type, crudServiceTemplate, watchedStateRepository, watchEventRepository);
        this.reconcileStateRepository = reconcileStateRepository;
    }

    @Override
    public Future<Page<T>> findUnreconciled(Pageable pageable) {
        return reconcileStateRepository.findUnreconciled(indexName, type, pageable);
    }

    /**
     * Writes what the record should be, creating it from {@code upsert} when it does not exist yet, and
     * enters the change in the ledger; visible to search on completion.
     *
     * @param id      the record
     * @param desired what it should be
     * @param upsert  the record to create when none exists, or null when a missing record is an error
     * @param source  what caused it, for the ledger
     * @return the record as written, or as it stands when the intent was already in place
     */
    public Future<T> updateDesired(String id, S desired, T upsert, String source) {
        Validate.notBlank(id, "id cannot be blank");
        Validate.isTrue(upsert == null || id.equals(upsert.getId()), "upsert must be the record %s", id);
        return reconcileStateRepository.updateDesired(document(id), desired, upsert, source)
                                       .compose(written -> written != null
                                               ? Future.succeededFuture(crudServiceTemplate.getObjectMapper().convertValue(written, type))
                                               : findById(id));
    }

    /**
     * Writes what the record is and which generation of intent that answers, and enters the change
     * in the ledger; visible to search on completion.
     *
     * @param id       the record
     * @param observed what it is
     * @param seen     the generation of intent the report answers
     * @param source   what caused it, for the ledger
     */
    public Future<Void> reportObserved(String id, S observed, long seen, String source) {
        Validate.notBlank(id, "id cannot be blank");
        return reconcileStateRepository.reportObserved(document(id), observed, seen, source).mapEmpty();
    }

    /**
     * @see ReconcileStateRepository#requestDeletion(WatchedDocument, String)
     */
    public Future<Void> requestDeletion(String id, String source) {
        return reconcileStateRepository.requestDeletion(document(id), source).mapEmpty();
    }
}
