package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.api.model.WatchedParent;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The ledger of what happened to every watched record, an append-only data stream.
 */
@Component
@RequiredArgsConstructor
public class WatchEventRepository {

    public static final String DATA_STREAM = "kinotic_watch_event";

    private final CrudServiceTemplate crudServiceTemplate;

    /**
     * Appends the event to the ledger.
     *
     * @param event what happened
     * @return a future that completes once the entry is accepted
     */
    public Future<Void> record(WatchEvent event) {
        Validate.notNull(event, "event cannot be null");
        return crudServiceTemplate.appendToDataStream(DATA_STREAM, event).mapEmpty();
    }

    /**
     * Lists what happened to a record and to the records it made, newest first.
     *
     * @param document the record as Elasticsearch addresses it
     * @param asParent the record as the records it made name it, null for a record nothing names
     * @param pageable the page to return
     * @return a future emitting a page of entries, empty when nothing has happened to the record
     */
    public Future<Page<WatchEvent>> findHistory(WatchedDocument document, WatchedParent asParent, Pageable pageable) {
        Validate.notNull(document, "document cannot be null");
        Validate.notNull(pageable, "pageable cannot be null");
        List<Query> own = new ArrayList<>();
        own.add(crudServiceTemplate.termFilter("type", document.index().type().name()));
        own.add(crudServiceTemplate.termFilter("id", document.id()));
        // an id is unique only within the scope its record is stored under, so a scoped record's entries are read under it
        if (document.routing() != null) {
            own.add(crudServiceTemplate.termFilter("scope", document.routing()));
        }
        List<Query> about = new ArrayList<>();
        about.add(crudServiceTemplate.composeFilter(own.toArray(Query[]::new)));
        if (asParent != null) {
            about.add(crudServiceTemplate.termFilter("parent", asParent.value()));
        }
        return crudServiceTemplate.search(DATA_STREAM, pageable, WatchEvent.class,
                                          b -> b.query(q -> q.bool(bq -> bq.should(about).minimumShouldMatch("1")))
                                                .sort(so -> so.field(f -> f.field("@timestamp").order(SortOrder.Desc))));
    }
}
