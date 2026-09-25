package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.model.workload.VmNodeState;
import org.kinotic.system.api.services.VmNodeService;
import org.kinotic.system.internal.api.repositories.VmNodeRepository;
import org.springframework.stereotype.Component;


@Component
public class DefaultVmNodeService extends AbstractCrudService<VmNode> implements VmNodeService {

    private final VmNodeRepository vmNodeRepository;

    public DefaultVmNodeService(VmNodeRepository repository) {
        super(repository);
        this.vmNodeRepository = repository;
    }

    @Override
    public Future<VmNode> findAvailableNode(double requiredCpus, int requiredMemoryMb, int requiredDiskMb) {
        return vmNodeRepository.findAvailableNode(requiredCpus, requiredMemoryMb, requiredDiskMb);
    }

    @Override
    public Future<Void> recordInventorySync(VmNode node) {
        Validate.notNull(node, "VmNode cannot be null");
        Validate.notNull(node.getId(), "VmNode id cannot be null");
        return vmNodeRepository.recordInventorySync(node);
    }

    @Override
    public Future<Void> recordHeartbeat(String nodeId, String healthMessage) {
        Validate.notNull(nodeId, "VmNode id cannot be null");
        return vmNodeRepository.recordHeartbeat(nodeId, healthMessage);
    }

    @Override
    public Future<VmNode> updateDesired(String nodeId, VmNodeState desired, String source) {
        Validate.notNull(nodeId, "VmNode id cannot be null");
        Validate.notNull(desired, "Desired state cannot be null");
        Validate.notBlank(source, "Source cannot be blank");
        return vmNodeRepository.updateDesired(nodeId, desired, null, source);
    }

    @Override
    public Future<Void> reportObserved(String nodeId, VmNodeState observed, long seen, String source) {
        Validate.notNull(nodeId, "VmNode id cannot be null");
        Validate.notNull(observed, "Observed state cannot be null");
        Validate.notBlank(source, "Source cannot be blank");
        return vmNodeRepository.reportObserved(nodeId, observed, seen, source);
    }

    @Override
    public Future<Boolean> setCondition(String nodeId, StatusCondition condition, String source) {
        Validate.notNull(nodeId, "VmNode id cannot be null");
        Validate.notNull(condition, "Condition cannot be null");
        Validate.notBlank(source, "Source cannot be blank");
        return vmNodeRepository.setCondition(nodeId, condition, source);
    }

    @Override
    public Future<Boolean> clearCondition(String nodeId, StatusConditionType type, String source) {
        Validate.notNull(nodeId, "VmNode id cannot be null");
        Validate.notNull(type, "Condition type cannot be null");
        Validate.notBlank(source, "Source cannot be blank");
        return vmNodeRepository.clearCondition(nodeId, type, source);
    }

    @Override
    public Future<Void> requestDeletion(String nodeId, String source) {
        Validate.notNull(nodeId, "VmNode id cannot be null");
        Validate.notBlank(source, "Source cannot be blank");
        return vmNodeRepository.requestDeletion(nodeId, source);
    }

    @Override
    public Future<Boolean> reserveSync(String nodeId, Workload workload) {
        Validate.notNull(nodeId, "VmNode id cannot be null");
        Validate.notNull(workload, "Workload cannot be null");
        return vmNodeRepository.reserveSync(nodeId, workload);
    }

    @Override
    public Future<Void> releaseSync(String nodeId, Workload workload) {
        Validate.notNull(nodeId, "VmNode id cannot be null");
        Validate.notNull(workload, "Workload cannot be null");
        return vmNodeRepository.releaseSync(nodeId, workload);
    }

    @Override
    protected Future<Void> beforeSave(VmNode entity) {
        Validate.notNull(entity, "VmNode cannot be null");
        Validate.notNull(entity.getId(), "VmNode id cannot be null");
        return Future.succeededFuture();
    }

}
