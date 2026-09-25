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
import org.kinotic.domain.api.model.WatchedType;
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
     * @param type     the record's kind
     * @param scope    the scope the record is addressed under, as its repository's {@code scopeOf} gives it
     * @param id       the record's id
     * @param pageable the page to return
     * @return a future emitting a page of entries, empty when nothing has happened to the record
     */
    public Future<Page<WatchEvent>> findHistory(WatchedType type, String scope, String id, Pageable pageable) {
        Validate.notNull(type, "type cannot be null");
        Validate.notBlank(id, "id cannot be blank");
        Validate.notNull(pageable, "pageable cannot be null");
        List<Query> own = new ArrayList<>();
        own.add(crudServiceTemplate.termFilter("type", type.name()));
        own.add(crudServiceTemplate.termFilter("id", id));
        List<Query> about = new ArrayList<>();
        if (scope != null) {
            // an id is unique within the scope its record is addressed under, so the entries are read under it,
            // and a pointer names its parent by scope, so the record's own pointer form finds what it made
            own.add(crudServiceTemplate.termFilter("scope", scope));
            about.add(crudServiceTemplate.termFilter("parent", new WatchedParent(type, scope, id).value()));
        }
        about.add(crudServiceTemplate.composeFilter(own.toArray(Query[]::new)));
        return crudServiceTemplate.search(DATA_STREAM, pageable, WatchEvent.class,
                                          b -> b.query(q -> q.bool(bq -> bq.should(about).minimumShouldMatch("1")))
                                                .sort(so -> so.field(f -> f.field("@timestamp").order(SortOrder.Desc))));
    }
}
