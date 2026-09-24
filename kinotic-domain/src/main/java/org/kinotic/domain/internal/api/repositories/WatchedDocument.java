package org.kinotic.domain.internal.api.repositories;

/**
 * One watched record as Elasticsearch addresses it: the index it lives in, its own id for the
 * ledger, and the document id and routing its repository stores it under, which differ from the
 * id for a record scoped to an organization.
 *
 * @param index      where the record lives
 * @param id         the record's own id
 * @param documentId the Elasticsearch document id
 * @param routing    the routing value, or null when the index has none
 */
public record WatchedDocument(WatchedIndex index, String id, String documentId, String routing) {

    /**
     * @param index where the record lives
     * @param id    the record's id, which is also its document id
     * @return the record on an index without routing
     */
    public static WatchedDocument of(WatchedIndex index, String id) {
        return new WatchedDocument(index, id, id, null);
    }
}
