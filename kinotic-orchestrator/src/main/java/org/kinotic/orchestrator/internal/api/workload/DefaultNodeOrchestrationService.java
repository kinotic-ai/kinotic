package org.kinotic.orchestrator.internal.api.workload;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.orchestrator.api.workload.NodeOrchestrationService;
import org.kinotic.os.api.model.workload.VmNode;
import org.kinotic.os.api.services.VmNodeService;
import org.kinotic.os.api.services.WorkloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultNodeOrchestrationService implements NodeOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultNodeOrchestrationService.class);

    private final VmNodeService vmNodeService;
    private final WorkloadService workloadService;

    public DefaultNodeOrchestrationService(VmNodeService vmNodeService,
                                           WorkloadService workloadService) {
        this.vmNodeService = vmNodeService;
        this.workloadService = workloadService;
    }

    @Override
    public CompletableFuture<VmNode> registerNode(VmNode node) {
        Validate.notNull(node, "Node cannot be null");
        Validate.notNull(node.getId(), "Node id cannot be null");

        return vmNodeService.findById(node.getId())
                .thenCompose(existing -> {
                    if (existing != null) {
                        // Update existing node with new resource info and mark online
                        existing.setHostname(node.getHostname())
                                .setName(node.getName())
                                .setTotalCpus(node.getTotalCpus())
                                .setTotalMemoryMb(node.getTotalMemoryMb())
                                .setTotalDiskMb(node.getTotalDiskMb())
                                .setStatus(node.getStatus())
                                .setLastSeen(new Date());
                        log.info("Re-registering VmNode: {} ({})", existing.getName(), existing.getId());
                        return vmNodeService.save(existing);
                    } else {
                        node.setLastSeen(new Date());
                        log.info("Registering new VmNode: {} ({})", node.getName(), node.getId());
                        return vmNodeService.save(node);
                    }
                });
    }

    @Override
    public CompletableFuture<Void> deregisterNode(String nodeId) {
        Validate.notNull(nodeId, "Node id cannot be null");

        return workloadService.countForNode(nodeId)
                .thenCompose(count -> {
                    if (count > 0) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("Cannot deregister node with active workloads. "
                                        + "Destroy all workloads on node " + nodeId + " first."));
                    }
                    log.info("Deregistering VmNode: {}", nodeId);
                    return vmNodeService.deleteById(nodeId);
                });
    }

    @Override
    public CompletableFuture<VmNode> getNode(String nodeId) {
        Validate.notNull(nodeId, "Node id cannot be null");
        return vmNodeService.findById(nodeId);
    }

    @Override
    public CompletableFuture<Page<VmNode>> listNodes(Pageable pageable) {
        return vmNodeService.findAll(pageable);
    }

    @Override
    public CompletableFuture<VmNode> findAvailableNode(int requiredCpus, int requiredMemoryMb, int requiredDiskMb) {
        return vmNodeService.findAvailableNode(requiredCpus, requiredMemoryMb, requiredDiskMb);
    }
}
