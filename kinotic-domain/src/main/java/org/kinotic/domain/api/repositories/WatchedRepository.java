package org.kinotic.domain.api.repositories;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Watched;
import org.kinotic.domain.api.model.WatchedType;

/**
 * What the reconcile master needs from the repository of one kind of {@link Watched} record: to find
 * the records whose last write it has not seen, to read one afresh, and to enter its writes in the ledger and mark them as seen.
 *
 * @param <R> the kind of record
 */
public interface WatchedRepository<R extends Watched> {

    /**
     * @return the kind of record this repository stores
     */
    WatchedType type();

    /**
     * @param record a record of this kind
     * @return the scope the record is addressed under: its organization for a record an organization
     * owns, or null when its kind has none; what {@link #find(String, String)} takes beside the id,
     * and what a parent pointer to the record carries
     */
    String scopeOf(R record);

    /**
     * @param id    the record's id
     * @param scope the scope the record is stored under, as {@link #scopeOf(Watched)} gives it
     * @return the record, or null when it does not exist
     */
    Future<R> find(String id, String scope);

    /**
     * @param pageable the page to return
     * @return the records written since the master last saw them
     */
    Future<Page<R>> findDirty(Pageable pageable);

    /**
     * Enters the writes the record holds unrecorded in the ledger, then marks the write the master saw
     * as seen. A record written again since keeps its mark, as does one whose entries could not be
     * entered.
     *
     * @param record the record as the master read it
     */
    Future<Void> clearDirty(R record);
}
