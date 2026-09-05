package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.services.VmNodeService;
import org.kinotic.system.internal.api.repositories.VmNodeRepository;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class DefaultVmNodeService extends AbstractCrudService<VmNode> implements VmNodeService {

    private final VmNodeRepository vmNodeRepository;

    public DefaultVmNodeService(VmNodeRepository repository) {
        super(repository);
        this.vmNodeRepository = repository;
    }

    @Override
    public Future<VmNode> findAvailableNode(int requiredCpus, int requiredMemoryMb, int requiredDiskMb) {
        return vmNodeRepository.findAvailableNode(requiredCpus, requiredMemoryMb, requiredDiskMb);
    }

    @Override
    protected Future<Void> beforeSave(VmNode entity) {
        Validate.notNull(entity, "VmNode cannot be null");
        Validate.notNull(entity.getId(), "VmNode id cannot be null");
        entity.setLastSeen(new Date());
        return Future.succeededFuture();
    }

}
