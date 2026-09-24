package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.kinotic.core.api.reconcile.StatusCondition;
import org.kinotic.core.api.reconcile.StatusConditionType;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.system.api.services.WorkloadService;
import org.kinotic.management.api.repositories.WorkloadRepository;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

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
    public Future<Long> countRunningForNode(String nodeId) {
        return workloadRepository.countRunningForNode(nodeId);
    }

    @Override
    public Future<Page<Workload>> findEndedBefore(Date cutoff, Pageable pageable) {
        Validate.notNull(cutoff, "Cutoff cannot be null");
        return workloadRepository.findEndedBefore(cutoff, pageable);
    }

    @Override
    public Future<Void> updateRunSync(String workloadId, WorkloadStatus status, Integer exitCode, String source) {
        Validate.notNull(workloadId, "Workload id cannot be null");
        Validate.notNull(status, "Workload status cannot be null");
        Validate.notBlank(source, "Source cannot be blank");
        return workloadRepository.updateRunSync(workloadId, status, exitCode, source);
    }

    @Override
    public Future<Boolean> setCondition(String workloadId, StatusCondition condition, String source) {
        Validate.notNull(workloadId, "Workload id cannot be null");
        Validate.notNull(condition, "Condition cannot be null");
        Validate.notBlank(source, "Source cannot be blank");
        return workloadRepository.setCondition(workloadId, condition, source);
    }

    @Override
    public Future<Boolean> clearCondition(String workloadId, StatusConditionType type, String source) {
        Validate.notNull(workloadId, "Workload id cannot be null");
        Validate.notNull(type, "Condition type cannot be null");
        Validate.notBlank(source, "Source cannot be blank");
        return workloadRepository.clearCondition(workloadId, type, source);
    }

    @Override
    protected Future<Void> beforeSave(Workload entity) {
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
        return Future.succeededFuture();
    }

}
