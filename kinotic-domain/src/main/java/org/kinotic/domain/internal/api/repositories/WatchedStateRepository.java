package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.reconcile.StatusCondition;
import org.kinotic.core.api.reconcile.StatusConditionType;
import org.kinotic.core.api.reconcile.Watched;
import org.kinotic.core.api.reconcile.WatchedState;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The writer of the {@link WatchedState} a {@link Watched} record carries under {@code state}, on
 * whichever index the record lives in. Every write is one shard operation on the record, so two
 * writers never lose each other's entry, and every write marks the record dirty for the reconcile
 * master in the same operation. A record's own repository composes this one and passes its index.
 */
@Component
@RequiredArgsConstructor
public class WatchedStateRepository {

    // Shared by every state script: the guard for a record written before its index had the column,
    // the lookup of a condition by type, and the two things every write ends with. The reconciled
    // flag exists only on a reconcilable's state, so it is recomputed only where it is present.
    static final String STATE_FUNCTIONS = """
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

    /**
     * Sets the condition on the record, beside the fields its authority writes. A record already
     * carrying a condition of the same type keeps it as it is. Visible to search on completion.
     *
     * @param indexName the index the record lives in
     * @param id        the record's id
     * @param condition the condition to set
     * @return true when the condition was set, false when the record already carried one of its type
     */
    public Future<Boolean> setCondition(String indexName, String id, StatusCondition condition) {
        Validate.notBlank(indexName, "indexName cannot be blank");
        Validate.notBlank(id, "id cannot be blank");
        Validate.notNull(condition, "condition cannot be null");
        Validate.notNull(condition.type(), "condition type cannot be null");
        Validate.notNull(condition.message(), "condition message cannot be null");
        Validate.notNull(condition.since(), "condition since cannot be null");
        return crudServiceTemplate.scriptedUpdateSync(indexName, id, SET_CONDITION,
                                                      Map.of("type", condition.type().name(),
                                                             "message", condition.message(),
                                                             "since", condition.since().toInstant().toString(),
                                                             "now", System.currentTimeMillis()));
    }

    /**
     * Clears the record's condition of the given type. A record carrying none is left as it is.
     * Visible to search on completion.
     *
     * @param indexName the index the record lives in
     * @param id        the record's id
     * @param type      the type to clear
     * @return true when a condition was cleared, false when the record carried none of the type
     */
    public Future<Boolean> clearCondition(String indexName, String id, StatusConditionType type) {
        Validate.notBlank(indexName, "indexName cannot be blank");
        Validate.notBlank(id, "id cannot be blank");
        Validate.notNull(type, "type cannot be null");
        return crudServiceTemplate.scriptedUpdateSync(indexName, id, CLEAR_CONDITION,
                                                      Map.of("type", type.name(),
                                                             "now", System.currentTimeMillis()));
    }
}
