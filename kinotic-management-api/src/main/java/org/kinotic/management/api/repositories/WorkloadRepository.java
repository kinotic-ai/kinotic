package org.kinotic.management.api.repositories;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.kinotic.management.api.model.workload.WorkloadStatus;

import java.util.List;
import java.util.stream.Stream;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkloadRepository extends AbstractRepository<Workload> {

    public WorkloadRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_workload", Workload.class, crudServiceTemplate);
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
}
