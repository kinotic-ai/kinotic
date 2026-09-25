package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.api.model.Watched;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.api.repositories.WatchedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

/**
 * Elasticsearch CRUD over one index of {@link Watched} records stored by id alone, with what the
 * reconcile master reads of them and the writes every watched record shares: marking a write as
 * seen, and setting or clearing a condition. Each write is one shard operation that enters the
 * ledger, and the ledger is read back per record.
 *
 * @param <T> the kind of record
 */
public abstract class AbstractWatchedRepository<T extends Watched> extends AbstractRepository<T> implements WatchedRepository<T> {

    protected final WatchedIndex watched;
    protected final WatchedStateRepository watchedStateRepository;
    protected final WatchEventRepository watchEventRepository;

    protected AbstractWatchedRepository(WatchedIndex watched,
                                        Class<T> type,
                                        CrudServiceTemplate crudServiceTemplate,
                                        WatchedStateRepository watchedStateRepository,
                                        WatchEventRepository watchEventRepository) {
        super(watched.name(), type, crudServiceTemplate);
        this.watched = watched;
        this.watchedStateRepository = watchedStateRepository;
        this.watchEventRepository = watchEventRepository;
    }

    @Override
    public WatchedType type() {
        return watched.type();
    }

    // Stored by id alone, so the scope plays no part in finding the record
    @Override
    public Future<T> find(String id, String scope) {
        return findById(id);
    }

    @Override
    public Future<Page<T>> findDirty(Pageable pageable) {
        return watchedStateRepository.findDirty(indexName, type, pageable);
    }

    @Override
    public Future<Void> clearDirty(String id, String scope, long dirtyAt) {
        return watchedStateRepository.clearDirty(document(id), dirtyAt);
    }

    /**
     * @see WatchedStateRepository#setCondition(WatchedDocument, StatusCondition, String)
     */
    public Future<Boolean> setCondition(String id, StatusCondition condition, String source) {
        return watchedStateRepository.setCondition(document(id), condition, source);
    }

    /**
     * @see WatchedStateRepository#clearCondition(WatchedDocument, StatusConditionType, String)
     */
    public Future<Boolean> clearCondition(String id, StatusConditionType type, String source) {
        return watchedStateRepository.clearCondition(document(id), type, source);
    }

    /**
     * Lists what happened to the record and to the records it made, newest first.
     *
     * @param record   the record
     * @param pageable the page to return
     * @return a future emitting a page of ledger entries, empty when nothing has happened to the record
     */
    public Future<Page<WatchEvent>> findHistory(T record, Pageable pageable) {
        Validate.notNull(record, "record cannot be null");
        return watchEventRepository.findHistory(watched.type(), scopeOf(record), record.getId(), pageable);
    }

    /**
     * @param id a record's id
     * @return the record as Elasticsearch addresses it
     */
    protected WatchedDocument document(String id) {
        return WatchedDocument.of(watched, id);
    }
}
