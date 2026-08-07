package org.kinotic.orchestrator.internal.api.services;

import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.orchestrator.api.model.workload.Workload;
import org.kinotic.orchestrator.internal.api.repositories.WorkloadRepository;
import org.kinotic.orchestrator.api.services.WorkloadService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultWorkloadService extends AbstractCrudService<Workload> implements WorkloadService {

    private final WorkloadRepository workloadRepository;

    public DefaultWorkloadService(WorkloadRepository repository) {
        super(repository);
        this.workloadRepository = repository;
    }

    @Override
    public CompletableFuture<Page<Workload>> findAllForNode(String nodeId, Pageable pageable) {
        return workloadRepository.findAllForNode(nodeId, pageable);
    }

    @Override
    public CompletableFuture<Long> countForNode(String nodeId) {
        return workloadRepository.countForNode(nodeId);
    }

    @Override
    protected CompletableFuture<Void> beforeSave(Workload entity) {
        Validate.notNull(entity, "Workload cannot be null");
        Validate.notNull(entity.getName(), "Workload name cannot be null");
        Validate.notNull(entity.getImage(), "Workload image cannot be null");

        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
        }
        entity.setUpdated(new Date());
        if (entity.getCreated() == null) {
            entity.setCreated(new Date());
        }
        return CompletableFuture.completedFuture(null);
    }

}
