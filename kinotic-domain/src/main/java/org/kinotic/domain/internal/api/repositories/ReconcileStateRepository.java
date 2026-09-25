package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Reconcilable;
import org.kinotic.domain.api.model.ReconcileState;
import org.kinotic.domain.api.model.WatchEventKind;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * The writer of the intent and observation a {@link Reconcilable} record carries in its
 * {@link ReconcileState}, on whichever index the record lives in. Every write is one shard operation
 * that also recomputes whether the record is in its desired state and marks it for the reconcile
 * master, and every write that changed the record goes to the ledger. A record's own repository
 * composes this one and passes its document.
 */
@Component
@RequiredArgsConstructor
public class ReconcileStateRepository {

    /**
     * The state functions plus the guard that gives a reconcilable's state its counters and flag
     * when the record was written before its index had them.
     */
    public static final String RECONCILE_FUNCTIONS = WatchedStateRepository.STATE_FUNCTIONS + """
            Map reconcileState(def source) {
                Map s = state(source);
                if (s.generation == null) {
                    s.generation = 0L;
                }
                if (s.observedGeneration == null) {
                    s.observedGeneration = 0L;
                }
                if (!s.containsKey('reconciled')) {
                    s.reconciled = false;
                }
                return s;
            }
            """;

    // An identical intent is a noop, so a consumer fanned out to every server node writes once
    private static final String UPDATE_DESIRED = RECONCILE_FUNCTIONS + """
            def s = reconcileState(ctx._source);
            if (s.desired == params.desired) {
                ctx.op = 'noop';
            } else {
                s.desired = params.desired;
                s.generation = (long) s.generation + 1;
                touched(s, params.now);
            }
            """;

    private static final String REPORT_OBSERVED = RECONCILE_FUNCTIONS + """
            def s = reconcileState(ctx._source);
            if (s.observed == params.observed && (long) s.observedGeneration == (long) params.seen) {
                ctx.op = 'noop';
            } else {
                s.observed = params.observed;
                s.observedGeneration = params.seen;
                touched(s, params.now);
            }
            """;

    // A record with no intent has nothing to answer again
    private static final String RENEW_DESIRED = RECONCILE_FUNCTIONS + """
            def s = reconcileState(ctx._source);
            if (s.desired == null) {
                ctx.op = 'noop';
            } else {
                s.generation = (long) s.generation + 1;
                touched(s, params.now);
            }
            """;

    // A deletion already asked for keeps its date: what matters is when it was first asked
    private static final String REQUEST_DELETION = RECONCILE_FUNCTIONS + """
            def s = reconcileState(ctx._source);
            if (s.deletionRequested != null) {
                ctx.op = 'noop';
            } else {
                s.deletionRequested = params.at;
                touched(s, params.now);
            }
            """;

    private final CrudServiceTemplate crudServiceTemplate;
    private final WatchedStateRepository watchedStateRepository;

    /**
     * @param indexName the index to search
     * @param type      the record type
     * @param pageable  the page to return
     * @return the records that are not in their desired state
     */
    public <R> Future<Page<R>> findUnreconciled(String indexName, Class<R> type, Pageable pageable) {
        Validate.notBlank(indexName, "indexName cannot be blank");
        return crudServiceTemplate.search(indexName, pageable, type,
                                          b -> b.query(crudServiceTemplate.termFilter("state.reconciled", false)));
    }

    /**
     * Writes what the record should be, bumping its generation, and records it. An intent equal to
     * the one already written leaves the record as it is. A record that does not exist yet is created
     * from {@code upsert} with the intent applied, so the first intent for a record is what creates it;
     * without an upsert, the write fails for a record that does not exist. Visible to search on
     * completion.
     *
     * @param document the record
     * @param desired  what the record should be
     * @param upsert   the record to create when none exists, without its intent, or null when a
     *                 missing record is an error
     * @param source   what caused it, for the ledger
     * @return the record as written, or null when the intent was already in place
     */
    public Future<Map<String, Object>> updateDesired(WatchedDocument document, Object desired, Object upsert, String source) {
        Validate.notNull(document, "document cannot be null");
        Validate.notNull(desired, "desired cannot be null");
        Validate.notBlank(source, "source cannot be blank");
        Map<String, Object> params = new HashMap<>();
        params.put("desired", desired);
        params.put("now", System.currentTimeMillis());
        @SuppressWarnings("unchecked")
        Map<String, Object> upsertDocument = upsert == null ? null : crudServiceTemplate.getObjectMapper().convertValue(upsert, Map.class);
        return crudServiceTemplate.scriptedUpdateReturningSourceSync(document.index().name(), document.documentId(), UPDATE_DESIRED, params,
                                                                     u -> {
                                                                         if (document.routing() != null) {
                                                                             u.routing(document.routing());
                                                                         }
                                                                         if (upsertDocument != null) {
                                                                             u.upsert(upsertDocument).scriptedUpsert(true);
                                                                         }
                                                                     })
                                  .compose(written -> recorded(document, written,
                                                               new WatchedChange(WatchEventKind.DESIRED_UPDATED, source,
                                                                                 "Desired " + desired, desired)));
    }

    /**
     * Writes what the record's authority reports it is, and which generation of intent that report
     * answers, and records it. A report equal to the last leaves the record as it is. Visible to
     * search on completion.
     *
     * @param document the record
     * @param observed what the record is
     * @param seen     the generation of intent the report answers
     * @param source   what caused it, for the ledger
     * @return the record as written, or null when the report was already in place
     */
    public Future<Map<String, Object>> reportObserved(WatchedDocument document, Object observed, long seen, String source) {
        Validate.notNull(document, "document cannot be null");
        Validate.notNull(observed, "observed cannot be null");
        Validate.notBlank(source, "source cannot be blank");
        Map<String, Object> params = new HashMap<>();
        params.put("observed", observed);
        params.put("seen", seen);
        params.put("now", System.currentTimeMillis());
        return watchedStateRepository.run(document, REPORT_OBSERVED, params)
                                     .compose(written -> recorded(document, written,
                                                                  new WatchedChange(WatchEventKind.OBSERVED_REPORTED, source,
                                                                                    "Observed " + observed, observed)));
    }

    /**
     * Writes the record's intent again as new, bumping its generation without changing what it is, so
     * the worker answers it once more: what a restart from the console asks for. A record with no
     * intent is left as it is. Visible to search on completion.
     *
     * @param document the record
     * @param source   what caused it, for the ledger
     * @return the record as written, or null when it holds no intent
     */
    public Future<Map<String, Object>> renewDesired(WatchedDocument document, String source) {
        Validate.notNull(document, "document cannot be null");
        Validate.notBlank(source, "source cannot be blank");
        Map<String, Object> params = new HashMap<>();
        params.put("now", System.currentTimeMillis());
        return watchedStateRepository.run(document, RENEW_DESIRED, params)
                                     .compose(written -> recorded(document, written,
                                                                  new WatchedChange(WatchEventKind.DESIRED_RENEWED, source,
                                                                                    "Desired renewed", null)));
    }

    /**
     * Writes that the record should be deleted, and records it. Deletion is intent: the record's
     * worker finalizes it and deletes the record. A deletion already asked for is left as it is.
     * Visible to search on completion.
     *
     * @param document the record
     * @param source   what caused it, for the ledger
     * @return the record as written, or null when its deletion was already asked for
     */
    public Future<Map<String, Object>> requestDeletion(WatchedDocument document, String source) {
        Validate.notNull(document, "document cannot be null");
        Validate.notBlank(source, "source cannot be blank");
        Date at = new Date();
        Map<String, Object> params = new HashMap<>();
        params.put("at", at.toInstant().toString());
        params.put("now", System.currentTimeMillis());
        return watchedStateRepository.run(document, REQUEST_DELETION, params)
                                     .compose(written -> recorded(document, written,
                                                                  new WatchedChange(WatchEventKind.DELETION_REQUESTED, source,
                                                                                    "Deletion requested", at)));
    }

    // A script that declined returns no document, and a write that did not happen is not recorded
    private Future<Map<String, Object>> recorded(WatchedDocument document, Map<String, Object> written, WatchedChange change) {
        Future<Map<String, Object>> ret;
        if (written == null) {
            ret = Future.succeededFuture();
        } else {
            ret = watchedStateRepository.record(document, written, change).map(written);
        }
        return ret;
    }
}
