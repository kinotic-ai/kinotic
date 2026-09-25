package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.repositories.WorkloadRepository;
import org.kinotic.system.api.services.WorkloadService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultWorkloadService extends AbstractCrudService<Workload> implements WorkloadService {

    private final WorkloadRepository workloadRepository;

    public DefaultWorkloadService(WorkloadRepository repository) {
        super(repository);
        this.workloadRepository = repository;
    }

    @Override
    public Future<Page<Workload>> findAllForNode(String nodeId, Pageable pageable) {
        return workloadRepository.findAllForNode(nodeId, pageable);
    }

    @Override
    public Future<Page<WatchEvent>> findHistory(String workloadId, Pageable pageable) {
        Validate.notNull(workloadId, "Workload id cannot be null");
        Validate.notNull(pageable, "Pageable cannot be null");
        return findById(workloadId).compose(workload -> workload == null
                ? Future.succeededFuture(new Page<>(List.of(), 0L))
                : workloadRepository.findHistory(workload, pageable));
    }

    @Override
    protected Future<Void> beforeSave(Workload entity) {
        Validate.notNull(entity, "Workload cannot be null");
        Validate.notNull(entity.getName(), "Workload name cannot be null");
        Validate.notNull(entity.getImage(), "Workload image cannot be null");
        return Future.succeededFuture();
    }

}
