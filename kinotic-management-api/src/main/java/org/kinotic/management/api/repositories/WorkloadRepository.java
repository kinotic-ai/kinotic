package org.kinotic.management.api.repositories;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.WatchEventKind;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.internal.api.repositories.AbstractWatchedRepository;
import org.kinotic.domain.internal.api.repositories.WatchedChange;
import org.kinotic.domain.internal.api.repositories.WatchedIndex;
import org.kinotic.domain.internal.api.repositories.WatchedStateRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Component
public class WorkloadRepository extends AbstractWatchedRepository<Workload> {

    private static final WatchedIndex WATCHED = new WatchedIndex(WatchedType.WORKLOAD, "kinotic_workload");

    // The run's fields are the node's; the state the platform keeps is touched the way every other
    // write touches it, in the same shard operation
    private static final String UPDATE_RUN = WatchedStateRepository.STATE_FUNCTIONS + """
            ctx._source.status = params.status;
            if (params.exitCode != null) {
                ctx._source.exitCode = params.exitCode;
            }
            ctx._source.updated = params.updated;
            touched(state(ctx._source), params.now);
            """;

    // A run ends once: one that has ended keeps its outcome and the script declines, so the caller
    // that returns the run's room to its node does so exactly once
    private static final String END_RUN = WatchedStateRepository.STATE_FUNCTIONS + """
            if (params.terminal.contains(ctx._source.status)) {
                ctx.op = 'noop';
            } else {
                ctx._source.status = params.status;
                if (params.exitCode != null) {
                    ctx._source.exitCode = params.exitCode;
                }
                ctx._source.updated = params.updated;
                touched(state(ctx._source), params.now);
            }
            """;

    public WorkloadRepository(CrudServiceTemplate crudServiceTemplate, WatchedStateRepository watchedStateRepository) {
        super(WATCHED, Workload.class, crudServiceTemplate, watchedStateRepository);
    }

    @Override
    public String scopeOf(Workload record) {
        return record.getOrganizationId();
    }

    public Future<Page<Workload>> findAllForNode(String nodeId, Pageable pageable) {
        return findAll(pageable, b -> b.query(termFilter("nodeId", nodeId)));
    }

    /**
     * Counts the workloads on a node whose run has not ended: the ones still holding a VM there.
     */
    public Future<Long> countRunningForNode(String nodeId) {
        return count(b -> b.query(composeFilter(termFilter("nodeId", nodeId),
                                                statusIn(status -> status.isOpen()))));
    }

    /**
     * The workloads whose run ended before the cutoff, oldest first: the ones the cleanup
     * deletes.
     */
    public Future<Page<Workload>> findEndedBefore(Date cutoff, Pageable pageable) {
        String before = cutoff.toInstant().toString();
        return findAll(pageable, b -> b.query(composeFilter(statusIn(WorkloadStatus::isComplete),
                                                            Query.of(q -> q.range(r -> r.date(d -> d.field("updated").lt(before))))))
                                       .sort(so -> so.field(f -> f.field("updated").order(SortOrder.Asc))));
    }

    private static Query statusIn(Predicate<WorkloadStatus> which) {
        List<FieldValue> statuses = Stream.of(WorkloadStatus.values())
                                          .filter(which)
                                          .map(status -> FieldValue.of(status.name()))
                                          .toList();
        return Query.of(q -> q.terms(t -> t.field("status").terms(v -> v.value(statuses))));
    }

    /**
     * Records the status and exit code of the workload's run and enters the change in the ledger,
     * leaving every other field as it is; visible to search on completion.
     *
     * @param workloadId the workload whose run is reported
     * @param status     the run's status
     * @param exitCode   the run's exit code, or null to leave the recorded one as it is
     * @param source     what caused it, for the ledger
     */
    public Future<Void> updateRunSync(String workloadId, WorkloadStatus status, Integer exitCode, String source) {
        return runScript(workloadId, UPDATE_RUN, status, exitCode, source).mapEmpty();
    }

    /**
     * Records the end of the workload's run, its terminal status and exit code, and enters the change
     * in the ledger; a run that has already ended keeps its outcome. Visible to search on completion.
     * @param workloadId the workload whose run ended
     * @param status     the terminal status
     * @param exitCode   the run's exit code, or null to leave the recorded one as it is
     * @param source     what caused it, for the ledger
     * @return true when this write ended the run, false when it had ended already
     */
    public Future<Boolean> endRunSync(String workloadId, WorkloadStatus status, Integer exitCode, String source) {
        return runScript(workloadId, END_RUN, status, exitCode, source);
    }

    // The terminal statuses go in as a parameter, so the script and WorkloadStatus.isComplete agree on
    // what an ended run is; a script that declined wrote nothing and enters nothing in the ledger
    private Future<Boolean> runScript(String workloadId, String script, WorkloadStatus status, Integer exitCode, String source) {
        Map<String, Object> params = new HashMap<>();
        params.put("status", status.name());
        if (exitCode != null) {
            params.put("exitCode", exitCode);
        }
        params.put("updated", Instant.now().toString());
        params.put("now", System.currentTimeMillis());
        params.put("terminal", Stream.of(WorkloadStatus.values()).filter(WorkloadStatus::isComplete).map(Enum::name).toList());
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("status", status);
        run.put("exitCode", exitCode);
        return crudServiceTemplate.scriptedUpdateReturningSourceSync(indexName, workloadId, script, params)
                                  .compose(document -> document == null
                                          ? Future.succeededFuture(false)
                                          : watchedStateRepository.record(
                                                  document(workloadId), document,
                                                  new WatchedChange(WatchEventKind.STATUS_CHANGED, source,
                                                                    exitCode != null ? "Run " + status + " with exit code " + exitCode : "Run " + status,
                                                                    run)).map(true));
    }

}
