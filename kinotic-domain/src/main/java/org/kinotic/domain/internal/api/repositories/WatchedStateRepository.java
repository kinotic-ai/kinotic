package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.Kinotic;
import org.kinotic.core.api.reconcile.StatusCondition;
import org.kinotic.core.api.reconcile.StatusConditionType;
import org.kinotic.core.api.reconcile.Watched;
import org.kinotic.core.api.reconcile.WatchedParent;
import org.kinotic.core.api.reconcile.WatchedState;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.api.model.WatchEventKind;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

/**
 * The writer of the {@link WatchedState} a {@link Watched} record carries under {@code state}, on
 * whichever index the record lives in. Every write is one shard operation on the record, so two
 * writers never lose each other's entry, every write marks the record dirty for the reconcile
 * master in the same operation, and every write that changed the record goes to the ledger. A
 * record's own repository composes this one and passes its index.
 */
@Component
@RequiredArgsConstructor
public class WatchedStateRepository {

    /**
     * Shared by every script that writes a watched record: the guard for a record written before its
     * index had the column, the lookup of a condition by type, and {@code touched}, which every
     * write ends with. The reconciled flag exists only on a reconcilable's state, so it is recomputed
     * only where it is present.
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
            void touched(Map s, long now) {
                s.dirty = true;
                s.dirtyAt = now;
                if (s.containsKey('reconciled')) {
                    s.reconciled = reconciled(s);
                }
            }
            """;

    // A condition already present is kept as it is: what matters is when the inference was first made
    private static final String SET_CONDITION = STATE_FUNCTIONS + """
            def s = state(ctx._source);
            if (condition(s, params.type) != null) {
                ctx.op = 'noop';
            } else {
                s.conditions.add(['type': params.type, 'message': params.message, 'since': params.since]);
                touched(s, params.now);
            }
            """;

    private static final String CLEAR_CONDITION = STATE_FUNCTIONS + """
            def s = state(ctx._source);
            def c = condition(s, params.type);
            if (c == null) {
                ctx.op = 'noop';
            } else {
                s.conditions.remove(s.conditions.indexOf(c));
                touched(s, params.now);
            }
            """;

    private final CrudServiceTemplate crudServiceTemplate;
    private final WatchEventRepository watchEventRepository;
    private final Kinotic kinotic;

    /**
     * Sets the condition on the record, beside the fields its authority writes, and records it. A
     * record already carrying a condition of the same type keeps it as it is. Visible to search on
     * completion.
     *
     * @param index     where the record lives
     * @param id        the record's id
     * @param condition the condition to set
     * @param source    what caused it, for the ledger
     * @return true when the condition was set, false when the record already carried one of its type
     */
    public Future<Boolean> setCondition(WatchedIndex index, String id, StatusCondition condition, String source) {
        Validate.notNull(index, "index cannot be null");
        Validate.notBlank(id, "id cannot be blank");
        Validate.notNull(condition, "condition cannot be null");
        Validate.notNull(condition.type(), "condition type cannot be null");
        Validate.notNull(condition.message(), "condition message cannot be null");
        Validate.notNull(condition.since(), "condition since cannot be null");
        Validate.notBlank(source, "source cannot be blank");
        return crudServiceTemplate.scriptedUpdateReturningSourceSync(index.name(), id, SET_CONDITION,
                                                                     Map.of("type", condition.type().name(),
                                                                            "message", condition.message(),
                                                                            "since", condition.since().toInstant().toString(),
                                                                            "now", System.currentTimeMillis()))
                                  .compose(document -> recorded(index, id, document,
                                                                new WatchedChange(WatchEventKind.CONDITION_SET, source,
                                                                                  condition.message(), condition)));
    }

    /**
     * Clears the record's condition of the given type and records it. A record carrying none is left
     * as it is. Visible to search on completion.
     *
     * @param index  where the record lives
     * @param id     the record's id
     * @param type   the type to clear
     * @param source what caused it, for the ledger
     * @return true when a condition was cleared, false when the record carried none of the type
     */
    public Future<Boolean> clearCondition(WatchedIndex index, String id, StatusConditionType type, String source) {
        Validate.notNull(index, "index cannot be null");
        Validate.notBlank(id, "id cannot be blank");
        Validate.notNull(type, "type cannot be null");
        Validate.notBlank(source, "source cannot be blank");
        return crudServiceTemplate.scriptedUpdateReturningSourceSync(index.name(), id, CLEAR_CONDITION,
                                                                     Map.of("type", type.name(),
                                                                            "now", System.currentTimeMillis()))
                                  .compose(document -> recorded(index, id, document,
                                                                new WatchedChange(WatchEventKind.CONDITION_CLEARED, source,
                                                                                  type + " cleared", Map.of("type", type))));
    }

    /**
     * Records a write to the record in the ledger, naming what the record belongs to and its
     * generation as the write left them.
     *
     * @param index    where the record lives
     * @param id       the record's id
     * @param document the record as the write left it
     * @param change   what the write was
     * @return a future that completes once the entry is accepted
     */
    public Future<Void> record(WatchedIndex index, String id, Map<String, Object> document, WatchedChange change) {
        Validate.notNull(index, "index cannot be null");
        Validate.notBlank(id, "id cannot be blank");
        Validate.notNull(document, "document cannot be null");
        Validate.notNull(change, "change cannot be null");
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) document.get("state");
        WatchedParent parent = null;
        Long generation = null;
        if (state != null) {
            if (state.get("parent") != null) {
                parent = WatchedParent.parse((String) state.get("parent"));
            }
            if (state.get("generation") != null) {
                generation = ((Number) state.get("generation")).longValue();
            }
        }
        return watchEventRepository.record(new WatchEvent(new Date(), index.type(), id, parent, change.kind(),
                                                          change.source(), kinotic.serverInfo().getNodeId(),
                                                          generation, change.message(), change.value()));
    }

    // A script that declined returns no document, and a write that did not happen is not recorded
    private Future<Boolean> recorded(WatchedIndex index, String id, Map<String, Object> document, WatchedChange change) {
        Future<Boolean> ret;
        if (document == null) {
            ret = Future.succeededFuture(false);
        } else {
            ret = record(index, id, document, change).map(true);
        }
        return ret;
    }
}
