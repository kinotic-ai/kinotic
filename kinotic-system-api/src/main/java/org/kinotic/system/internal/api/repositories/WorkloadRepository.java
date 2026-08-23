package org.kinotic.system.internal.api.repositories;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.system.api.model.workload.Workload;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
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

    public Future<Long> countForNode(String nodeId) {
        return count(b -> b.query(termFilter("nodeId", nodeId)));
    }
}
