package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.kinotic.management.api.model.workload.VmNode;
import org.kinotic.system.api.services.VmNodeService;
import org.kinotic.management.internal.api.repositories.VmNodeRepository;
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
        // Elasticsearch can't express "available = total - allocated" in a server-side query, so
        // we fetch the online nodes and filter locally.
        return vmNodeRepository.findOnlineNodes()
                .map(page -> page.getContent()
                                 .stream()
                                 .filter(node -> node.getAvailableCpus() >= requiredCpus
                                         && node.getAvailableMemoryMb() >= requiredMemoryMb
                                         && node.getAvailableDiskMb() >= requiredDiskMb)
                                 .findFirst()
                                 .orElse(null));
    }

    @Override
    protected Future<Void> beforeSave(VmNode entity) {
        Validate.notNull(entity, "VmNode cannot be null");
        Validate.notNull(entity.getId(), "VmNode id cannot be null");
        entity.setLastSeen(new Date());
        return Future.succeededFuture();
    }

}
