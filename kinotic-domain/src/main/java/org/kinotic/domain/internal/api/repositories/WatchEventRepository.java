package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.PrefixQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.api.model.WatchEventKind;
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
     * Appends the event to the ledger under the given id. An event entered under the id since the
     * ledger last rolled over is left as it is, so entering the same event again is a no-op.
     *
     * @param eventId the id the event is entered under
     * @param event   what happened
     * @return a future that completes once the entry is accepted, or found entered already
     */
    public Future<Void> record(String eventId, WatchEvent event) {
        Validate.notBlank(eventId, "eventId cannot be blank");
        Validate.notNull(event, "event cannot be null");
        return crudServiceTemplate.create(DATA_STREAM, eventId, event)
                                  .<Void>mapEmpty()
                                  .recover(error -> error instanceof AlreadyExistsException
                                          ? Future.succeededFuture()
                                          : Future.failedFuture(error));
    }

    /**
     * Lists what happened to every watched record, newest first.
     *
     * @param pageable the page to return
     * @return a future emitting a page of entries
     */
    public Future<Page<WatchEvent>> findAll(Pageable pageable) {
        Validate.notNull(pageable, "pageable cannot be null");
        return crudServiceTemplate.search(DATA_STREAM, pageable, WatchEvent.class,
                                          b -> b.sort(so -> so.field(f -> f.field("@timestamp").order(SortOrder.Desc))));
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

    /**
     * Lists what happened to every record of a type under a scope and to the records they made,
     * newest first: the entries {@link #findHistory(WatchedType, String, String, Pageable)} lists for
     * each such record, limited to the given kinds.
     *
     * @param type     the records' kind
     * @param scope    the scope the records are addressed under, as their repository's {@code scopeOf} gives it
     * @param kinds    the kinds of entries to list
     * @param pageable the page to return
     * @return a future emitting a page of entries
     */
    public Future<Page<WatchEvent>> findHistoryOfAll(WatchedType type,
                                                     String scope,
                                                     List<WatchEventKind> kinds,
                                                     Pageable pageable) {
        Validate.notNull(type, "type cannot be null");
        Validate.notBlank(scope, "scope cannot be blank");
        Validate.notEmpty(kinds, "kinds cannot be empty");
        Validate.notNull(pageable, "pageable cannot be null");
        List<Query> about = List.of(
                crudServiceTemplate.composeFilter(crudServiceTemplate.termFilter("type", type.name()),
                                                  crudServiceTemplate.termFilter("scope", scope)),
                PrefixQuery.of(p -> p.field("parent").value(WatchedParent.valuePrefix(type, scope)))._toQuery());
        List<FieldValue> kindValues = kinds.stream().map(kind -> FieldValue.of(kind.name())).toList();
        return crudServiceTemplate.search(DATA_STREAM, pageable, WatchEvent.class,
                                          b -> b.query(q -> q.bool(bq -> bq.filter(f -> f.terms(t -> t.field("kind")
                                                                                                       .terms(v -> v.value(kindValues))))
                                                                           .should(about)
                                                                           .minimumShouldMatch("1")))
                                                .sort(so -> so.field(f -> f.field("@timestamp").order(SortOrder.Desc))));
    }
}
