package org.kinotic.domain.internal.api.repositories;

import org.kinotic.core.api.reconcile.WatchedType;

/**
 * The index a kind of watched record lives in, which is what {@link WatchedStateRepository} needs to
 * write the record and to name it in the ledger.
 *
 * @param type the kind of record
 * @param name the index name
 */
public record WatchedIndex(WatchedType type, String name) {
}
