package org.kinotic.domain.api.reconcile;

import org.kinotic.core.api.crud.Identifiable;

/**
 * A record with more than one writer, each owning its own fields, whose changes the platform watches:
 * it infers conditions beside the authority's word, records every write, and tells the record's
 * parent when it changes.
 */
public interface Watched extends Identifiable<String> {

    /**
     * @return what the platform keeps on this record beside the authority's fields
     */
    WatchedState getState();
}
