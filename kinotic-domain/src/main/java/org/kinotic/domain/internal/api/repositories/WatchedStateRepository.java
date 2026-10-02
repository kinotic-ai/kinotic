package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.Kinotic;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.Watched;
import org.kinotic.domain.api.model.WatchedState;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.api.model.WatchEventKind;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The writer of the {@link WatchedState} a {@link Watched} record carries under {@code state}, on
 * whichever index the record lives in. Every write is one shard operation on the record, so two
 * writers never lose each other's entry, every write marks the record dirty for the reconcile
 * master in the same operation, and every write that changed the record is entered in the ledger:
 * the entry is held on the record by the write itself, so a write that landed is entered even when
 * the server that made it fails before entering it. A record's own repository composes this one and
 * passes its index.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchedStateRepository {

    /**
     * Shared by every script that writes a watched record: the guard for a record written before its
     * index had the column, the lookup of a condition by type, and {@code touched}, which every
     * write run through {@link #write} ends with as {@code touched(ctx._source, s, params)}. It
     * marks the record for the reconcile master and holds the write's ledger entry on the record,
     * stamped with a time later than every earlier write to the record. The reconciled flag exists
     * only on a reconcilable's state, so it is recomputed only where it is present.
     */
    public static final String STATE_FUNCTIONS = """
            Map state(def source) {
                if (source.state == null) {
                    source.state = new HashMap();
                }
                if (source.state.conditions == null) {
                    source.state.conditions = new ArrayList();
                }
                return source.state;
            }
            Map condition(Map s, String type) {
                for (def c : s.conditions) {
                    if (c.type == type) {
                        return c;
                    }
                }
                return null;
            }
            boolean reconciled(Map s) {
                return s.desired == s.observed
                    && (long) s.generation == (long) s.observedGeneration
                    && s.conditions.isEmpty()
                    && s.deletionRequested == null;
            }
            void touched(def source, Map s, Map input) {
                // dirtyAt only rises, so the ledger orders a record's writes as they landed whatever the writers' clocks say
                long now = (long) input.now;
                long next = s.dirtyAt == null ? 0L : (long) s.dirtyAt + 1;
                long at = now > next ? now : next;
                s.dirty = true;
                s.dirtyAt = at;
                if (s.containsKey('reconciled')) {
                    s.reconciled = reconciled(s);
                }
                if (s.unrecorded == null) {
                    s.unrecorded = new HashMap();
                }
                Map entry = new HashMap(input.event);
                entry['@timestamp'] = at;
                entry.scope = source['%s'];
                entry.parent = s.parent;
                entry.generation = s.generation;
                s.unrecorded[input.eventId] = entry;
            }
            """.formatted(AbstractOrganizationScopedRepository.ORGANIZATION_ID_FIELD);

    /**
     * The statements that add the condition {@code params.type}, {@code params.message} and
     * {@code params.since} name beside the record's state and touch it, on a script opened by
     * {@link #STATE_FUNCTIONS}. A condition already present is kept as it is: what matters is when the
     * inference was first made. A record kind with a rule of its own on when a condition may be set
     * puts that rule first and passes the script to
     * {@link #setCondition(WatchedDocument, StatusCondition, String, String, Map)}.
     */
    public static final String ADD_CONDITION = """
            def s = state(ctx._source);
            if (condition(s, params.type) != null) {
                ctx.op = 'noop';
            } else {
                s.conditions.add(['type': params.type, 'message': params.message, 'since': params.since]);
                touched(ctx._source, s, params);
            }
            """;

    private static final String SET_CONDITION = STATE_FUNCTIONS + ADD_CONDITION;

    private static final String CLEAR_CONDITION = STATE_FUNCTIONS + """
            def s = state(ctx._source);
            def c = condition(s, params.type);
            if (c == null) {
                ctx.op = 'noop';
            } else {
                s.conditions.remove(s.conditions.indexOf(c));
                touched(ctx._source, s, params);
            }
            """;

    // The master's bookkeeping, not a change to the record, so it is not entered in the ledger. A write
    // that landed since the scan stamped a later dirtyAt and keeps its mark.
    private static final String CLEAR_DIRTY = STATE_FUNCTIONS + """
            def s = state(ctx._source);
            if (s.dirty != true || s.dirtyAt == null || (long) s.dirtyAt != (long) params.dirtyAt) {
                ctx.op = 'noop';
            } else {
                s.dirty = false;
            }
            """;

    // Bookkeeping like CLEAR_DIRTY, so the record is not touched
    private static final String REMOVE_ENTERED = """
            def unrecorded = ctx._source.state?.unrecorded;
            boolean removed = false;
            if (unrecorded != null) {
                for (def id : params.ids) {
                    if (unrecorded.remove(id) != null) {
                        removed = true;
                    }
                }
            }
            if (!removed) {
                ctx.op = 'noop';
            }
            """;

    private static final TypeReference<Map<String, WatchEvent>> UNRECORDED = new TypeReference<>() {};

    // Rounds a delete may make before it gives up on a record that keeps being written
    private static final int DELETE_ATTEMPTS = 5;

    private final CrudServiceTemplate crudServiceTemplate;
    private final WatchEventRepository watchEventRepository;
    private final Kinotic kinotic;

    /**
     * @param indexName the index to search
     * @param type      the record type
     * @param pageable  the page to return
     * @return the records written since the reconcile master last saw them
     */
    public <R> Future<Page<R>> findDirty(String indexName, Class<R> type, Pageable pageable) {
        Validate.notBlank(indexName, "indexName cannot be blank");
        return crudServiceTemplate.search(indexName, pageable, type,
                                          b -> b.query(crudServiceTemplate.termFilter("state.dirty", true)));
    }

    /**
     * Enters the writes the record holds unrecorded in the ledger, then marks the write the reconcile
     * master saw as seen. A record written again since keeps its mark, as does one whose entries could
     * not be entered, so the master's next look enters them.
     *
     * @param document the record
     * @param state    the record's state as the master read it
     */
    public Future<Void> clearDirty(WatchedDocument document, WatchedState state) {
        Validate.notNull(document, "document cannot be null");
        Validate.notNull(state, "state cannot be null");
        return enter(document, state.getUnrecorded())
                .compose(v -> crudServiceTemplate.scriptedUpdateReturningSourceSync(document.index().name(), document.documentId(),
                                                                                    CLEAR_DIRTY, Map.of("dirtyAt", state.getDirtyAt()),
                                                                                    request(document, null)))
                .mapEmpty();
    }

    /**
     * Sets the condition on the record, beside the fields its authority writes, and records it. A
     * record already carrying a condition of the same type keeps it as it is. Visible to search on
     * completion.
     *
     * @param document  the record
     * @param condition the condition to set
     * @param source    what caused it, for the ledger
     * @return true when the condition was set, false when the record already carried one of its type
     */
    public Future<Boolean> setCondition(WatchedDocument document, StatusCondition condition, String source) {
        return setCondition(document, condition, source, SET_CONDITION, Map.of());
    }

    /**
     * As {@link #setCondition(WatchedDocument, StatusCondition, String)}, through a script built on
     * {@link #ADD_CONDITION} that reads the given parameters beside the condition's own.
     *
     * @param document     the record
     * @param condition    the condition to set
     * @param source       what caused it, for the ledger
     * @param script       the Painless source, opened by {@link #STATE_FUNCTIONS} and ending in {@link #ADD_CONDITION}
     * @param scriptParams the values the script's own rule reads as {@code params.<name>}
     * @return true when the condition was set, false when the script declined
     */
    public Future<Boolean> setCondition(WatchedDocument document,
                                        StatusCondition condition,
                                        String source,
                                        String script,
                                        Map<String, Object> scriptParams) {
        Validate.notNull(document, "document cannot be null");
        Validate.notNull(condition, "condition cannot be null");
        Validate.notNull(condition.type(), "condition type cannot be null");
        Validate.notNull(condition.message(), "condition message cannot be null");
        Validate.notNull(condition.since(), "condition since cannot be null");
        Validate.notBlank(source, "source cannot be blank");
        Validate.notBlank(script, "script cannot be blank");
        Validate.notNull(scriptParams, "scriptParams cannot be null");
        Map<String, Object> params = new HashMap<>(scriptParams);
        params.put("type", condition.type().name());
        params.put("message", condition.message());
        params.put("since", condition.since().toInstant().toString());
        return write(document, script, params,
                     new WatchedChange(WatchEventKind.CONDITION_SET, source, condition.message(), condition))
                .map(written -> written != null);
    }

    /**
     * Clears the record's condition of the given type and records it. A record carrying none is left
     * as it is. Visible to search on completion.
     *
     * @param document the record
     * @param type     the type to clear
     * @param source   what caused it, for the ledger
     * @return true when a condition was cleared, false when the record carried none of the type
     */
    public Future<Boolean> clearCondition(WatchedDocument document, StatusConditionType type, String source) {
        Validate.notNull(document, "document cannot be null");
        Validate.notNull(type, "type cannot be null");
        Validate.notBlank(source, "source cannot be blank");
        return write(document, CLEAR_CONDITION, Map.of("type", type.name()),
                     new WatchedChange(WatchEventKind.CONDITION_CLEARED, source, type + " cleared", Map.of("type", type)))
                .map(written -> written != null);
    }

    /**
     * Runs a state script against the record and enters what it changed in the ledger. The script is
     * opened by {@link #STATE_FUNCTIONS}, reads {@code params.now} as the write's time beside its own
     * parameters, and ends every branch that changes the record with
     * {@code touched(ctx._source, s, params)}. Visible to search on completion.
     *
     * @param document the record
     * @param script   the Painless source
     * @param params   the values the script reads as {@code params.<name>}
     * @param change   what the write is, for the ledger
     * @return the record as written, or null when the script declined
     */
    public Future<Map<String, Object>> write(WatchedDocument document, String script, Map<String, Object> params, WatchedChange change) {
        return write(document, script, params, change, null);
    }

    /**
     * As {@link #write(WatchedDocument, String, Map, WatchedChange)}, with an upsert document the
     * script runs against when the record does not exist yet, in which case the future completes with
     * the created record.
     *
     * @param document the record
     * @param script   the Painless source
     * @param params   the values the script reads as {@code params.<name>}
     * @param change   what the write is, for the ledger
     * @param upsert   the record to create when none exists, or null when a missing record is an error
     * @return the record as written, or null when the script declined
     */
    public Future<Map<String, Object>> write(WatchedDocument document,
                                             String script,
                                             Map<String, Object> params,
                                             WatchedChange change,
                                             Map<String, Object> upsert) {
        Validate.notNull(document, "document cannot be null");
        Validate.notBlank(script, "script cannot be blank");
        Validate.notNull(params, "params cannot be null");
        Validate.notNull(change, "change cannot be null");
        Map<String, Object> stamped = new HashMap<>(params);
        stamped.put("now", System.currentTimeMillis());
        stamped.put("eventId", UUID.randomUUID().toString());
        // the script stamps the entry's time, scope, parent and generation as the write leaves the record
        stamped.put("event", new WatchEvent(null, document.index().type(), document.id(), null, null,
                                            change.kind(), change.source(), kinotic.serverInfo().getNodeId(), null,
                                            change.message(), crudServiceTemplate.getObjectMapper().valueToTree(change.value())));
        return crudServiceTemplate.scriptedUpdateReturningSourceSync(document.index().name(), document.documentId(), script, stamped,
                                                                     request(document, upsert))
                                  .compose(written -> written == null
                                          ? Future.succeededFuture()
                                          : enterWritten(document, written).map(written));
    }

    /**
     * Deletes the record once every write to it is entered in the ledger. A record that does not exist
     * is left as it is.
     *
     * @param document the record
     * @return a future that completes once the record is gone, or fails when it kept being written
     * through every attempt to delete it
     */
    public Future<Void> delete(WatchedDocument document) {
        Validate.notNull(document, "document cannot be null");
        return delete(document, Refresh.False, 1);
    }

    /**
     * As {@link #delete(WatchedDocument)}, waiting for the deletion to be visible to search.
     *
     * @param document the record
     * @return a future that completes once the record is gone, or fails when it kept being written
     * through every attempt to delete it
     */
    public Future<Void> deleteSync(WatchedDocument document) {
        Validate.notNull(document, "document cannot be null");
        return delete(document, Refresh.WaitFor, 1);
    }

    // The delete is conditional on the record as read, so a write landing between the read and the
    // delete fails it with a conflict, and the next round enters that write's entry before deleting
    private Future<Void> delete(WatchedDocument document, Refresh refresh, int attempt) {
        return crudServiceTemplate.findById(document.index().name(), document.documentId(), Map.class,
                                            g -> {
                                                if (document.routing() != null) {
                                                    g.routing(document.routing());
                                                }
                                            },
                                            Function.identity())
                .compose(read -> {
                    Future<Void> ret;
                    if (!read.found()) {
                        ret = Future.succeededFuture();
                    } else {
                        @SuppressWarnings("unchecked")
                        Map<String, WatchEvent> unrecorded = unrecordedIn((Map<String, Object>) read.source());
                        if (!unrecorded.isEmpty()) {
                            // entering the entries removes them from the record, which is another write
                            ret = enter(document, unrecorded).compose(v -> deleteAgain(document, refresh, attempt));
                        } else {
                            ret = crudServiceTemplate.deleteById(document.index().name(), document.documentId(),
                                                                 d -> {
                                                                     if (document.routing() != null) {
                                                                         d.routing(document.routing());
                                                                     }
                                                                     d.ifSeqNo(read.seqNo())
                                                                      .ifPrimaryTerm(read.primaryTerm())
                                                                      .refresh(refresh);
                                                                 })
                                                     .<Void>mapEmpty()
                                                     .recover(error -> CrudServiceTemplate.isVersionConflict(error)
                                                             ? deleteAgain(document, refresh, attempt)
                                                             : Future.failedFuture(error));
                        }
                    }
                    return ret;
                });
    }

    private Future<Void> deleteAgain(WatchedDocument document, Refresh refresh, int attempt) {
        Future<Void> ret;
        if (attempt < DELETE_ATTEMPTS) {
            ret = delete(document, refresh, attempt + 1);
        } else {
            ret = Future.failedFuture(new IllegalStateException(document.index().type() + " " + document.id()
                    + " kept being written through " + DELETE_ATTEMPTS + " attempts to delete it"));
        }
        return ret;
    }

    // The write landed with its entry held on the record, so an entry not entered here is entered by
    // the master's next look at the record, which the write marked dirty
    private Future<Void> enterWritten(WatchedDocument document, Map<String, Object> written) {
        return enter(document, unrecordedIn(written))
                .recover(error -> {
                    log.warn("Could not enter the writes to {} {} in the ledger, leaving them to the reconcile master",
                             document.index().type(), document.id(), error);
                    return Future.succeededFuture();
                });
    }

    // A record no write has touched carries no entries
    private Map<String, WatchEvent> unrecordedIn(Map<String, Object> source) {
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) source.get("state");
        Map<String, WatchEvent> ret = null;
        if (state != null) {
            ret = crudServiceTemplate.getObjectMapper().convertValue(state.get("unrecorded"), UNRECORDED);
        }
        return ret != null ? ret : Map.of();
    }

    // Each entry is appended under its own id, so one another server entered already is refused and counts as entered
    private Future<Void> enter(WatchedDocument document, Map<String, WatchEvent> unrecorded) {
        Future<Void> ret;
        if (unrecorded.isEmpty()) {
            ret = Future.succeededFuture();
        } else {
            List<String> ids = List.copyOf(unrecorded.keySet());
            ret = Future.all(ids.stream().map(id -> watchEventRepository.record(id, unrecorded.get(id))).toList())
                        .compose(v -> crudServiceTemplate.scriptedUpdate(document.index().name(), document.documentId(),
                                                                         REMOVE_ENTERED, Map.of("ids", ids), request(document, null)))
                        .mapEmpty();
        }
        return ret;
    }

    private static Consumer<UpdateRequest.Builder<Map, Map<String, Object>>> request(WatchedDocument document, Map<String, Object> upsert) {
        return u -> {
            if (document.routing() != null) {
                u.routing(document.routing());
            }
            if (upsert != null) {
                u.upsert(upsert).scriptedUpsert(true);
            }
        };
    }
}
