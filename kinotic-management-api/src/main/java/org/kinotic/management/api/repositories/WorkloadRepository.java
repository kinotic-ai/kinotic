package org.kinotic.management.api.repositories;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.reconcile.StatusCondition;
import org.kinotic.core.api.reconcile.StatusConditionType;
import org.kinotic.core.api.reconcile.WatchedType;
import org.kinotic.domain.api.model.WatchEventKind;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.repositories.WatchedChange;
import org.kinotic.domain.internal.api.repositories.WatchedIndex;
import org.kinotic.domain.internal.api.repositories.WatchedStateRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class WorkloadRepository extends AbstractRepository<Workload> {

    public static final WatchedIndex WATCHED = new WatchedIndex(WatchedType.WORKLOAD, "kinotic_workload");

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

    private final WatchedStateRepository watchedStateRepository;

    public WorkloadRepository(CrudServiceTemplate crudServiceTemplate, WatchedStateRepository watchedStateRepository) {
        super(WATCHED.name(), Workload.class, crudServiceTemplate);
        this.watchedStateRepository = watchedStateRepository;
    }

    public Future<Page<Workload>> findAllForNode(String nodeId, Pageable pageable) {
        return findAll(pageable, b -> b.query(termFilter("nodeId", nodeId)));
    }

    /**
     * Counts the workloads on a node whose run has not ended: the ones still holding a VM there.
     */
    public Future<Long> countRunningForNode(String nodeId) {
        List<FieldValue> running = Stream.of(WorkloadStatus.STARTING, WorkloadStatus.RUNNING, WorkloadStatus.STOPPING)
                                         .map(status -> FieldValue.of(status.name()))
                                         .toList();
        return count(b -> b.query(composeFilter(termFilter("nodeId", nodeId),
                                                Query.of(q -> q.terms(t -> t.field("status").terms(v -> v.value(running)))))));
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
        Map<String, Object> params = new HashMap<>();
        params.put("status", status.name());
        if (exitCode != null) {
            params.put("exitCode", exitCode);
        }
        params.put("updated", Instant.now().toString());
        params.put("now", System.currentTimeMillis());
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("status", status);
        run.put("exitCode", exitCode);
        return crudServiceTemplate.scriptedUpdateReturningSourceSync(indexName, workloadId, UPDATE_RUN, params)
                                  .compose(document -> watchedStateRepository.record(
                                          WATCHED, workloadId, document,
                                          new WatchedChange(WatchEventKind.STATUS_CHANGED, source,
                                                            exitCode != null ? "Run " + status + " with exit code " + exitCode : "Run " + status,
                                                            run)));
    }

    /**
     * @see WatchedStateRepository#setCondition(WatchedIndex, String, StatusCondition, String)
     */
    public Future<Boolean> setCondition(String workloadId, StatusCondition condition, String source) {
        return watchedStateRepository.setCondition(WATCHED, workloadId, condition, source);
    }

    /**
     * @see WatchedStateRepository#clearCondition(WatchedIndex, String, StatusConditionType, String)
     */
    public Future<Boolean> clearCondition(String workloadId, StatusConditionType type, String source) {
        return watchedStateRepository.clearCondition(WATCHED, workloadId, type, source);
    }
}
