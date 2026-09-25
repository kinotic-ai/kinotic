package org.kinotic.management.api.repositories;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.reconcile.StatusCondition;
import org.kinotic.core.api.reconcile.StatusConditionType;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.repositories.WatchedStateRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class WorkloadRepository extends AbstractRepository<Workload> {

    private final WatchedStateRepository watchedStateRepository;

    public WorkloadRepository(CrudServiceTemplate crudServiceTemplate, WatchedStateRepository watchedStateRepository) {
        super("kinotic_workload", Workload.class, crudServiceTemplate);
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
     * Records the status and exit code of the workload's run, leaving every other field, the state
     * the platform keeps included, as it is; visible to search on completion.
     *
     * @param workloadId the workload whose run is reported
     * @param status     the run's status
     * @param exitCode   the run's exit code, or null to leave the recorded one as it is
     */
    public Future<Void> updateRunSync(String workloadId, WorkloadStatus status, Integer exitCode) {
        Map<String, Object> partial = new HashMap<>();
        partial.put("status", status.name());
        if (exitCode != null) {
            partial.put("exitCode", exitCode);
        }
        partial.put("updated", new Date());
        partial.put("state", Map.of("dirty", true, "dirtyAt", System.currentTimeMillis()));
        return crudServiceTemplate.partialUpdateSync(indexName, workloadId, partial, false);
    }

    /**
     * @see WatchedStateRepository#setCondition(String, String, StatusCondition)
     */
    public Future<Boolean> setCondition(String workloadId, StatusCondition condition) {
        return watchedStateRepository.setCondition(indexName, workloadId, condition);
    }

    /**
     * @see WatchedStateRepository#clearCondition(String, String, StatusConditionType)
     */
    public Future<Boolean> clearCondition(String workloadId, StatusConditionType type) {
        return watchedStateRepository.clearCondition(indexName, workloadId, type);
    }
}
