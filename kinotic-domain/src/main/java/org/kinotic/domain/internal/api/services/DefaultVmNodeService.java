package org.kinotic.domain.internal.api.services;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.workload.VmNode;
import org.kinotic.domain.internal.api.repositories.VmNodeRepository;
import org.kinotic.domain.api.services.VmNodeService;
import org.springframework.stereotype.Component;

@Component
public class DefaultVmNodeService extends AbstractCrudService<VmNode> implements VmNodeService {

    private final VmNodeRepository vmNodeRepository;

    public DefaultVmNodeService(VmNodeRepository repository) {
        super(repository);
        this.vmNodeRepository = repository;
    }

    @Override
    public CompletableFuture<VmNode> findAvailableNode(int requiredCpus, int requiredMemoryMb, int requiredDiskMb) {
        // Elasticsearch can't express "available = total - allocated" in a server-side query, so
        // we fetch the online nodes and filter locally.
        return vmNodeRepository.findOnlineNodes()
                .thenApply(page -> page.getContent()
                                       .stream()
                                       .filter(node -> node.getAvailableCpus() >= requiredCpus
                                               && node.getAvailableMemoryMb() >= requiredMemoryMb
                                               && node.getAvailableDiskMb() >= requiredDiskMb)
                                       .findFirst()
                                       .orElse(null));
    }

    @Override
    protected CompletableFuture<Void> beforeSave(VmNode entity) {
        Validate.notNull(entity, "VmNode cannot be null");
        Validate.notNull(entity.getId(), "VmNode id cannot be null");
        entity.setLastSeen(new Date());
        return CompletableFuture.completedFuture(null);
    }

}
