package org.kinotic.orchestrator.internal.api.workload;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.orchestrator.api.workload.NodeOrchestrationService;
import org.kinotic.os.api.model.workload.VmNode;
import org.kinotic.os.api.model.workload.VmNodeStatus;
import org.kinotic.os.api.model.workload.Workload;
import org.kinotic.os.api.model.workload.WorkloadStatus;
import org.kinotic.os.api.services.VmNodeService;
import org.kinotic.os.api.services.WorkloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class DefaultNodeOrchestrationService implements NodeOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultNodeOrchestrationService.class);

    /**
     * How long (in seconds) since the last heartbeat before a node is considered stale and marked OFFLINE.
     */
    @Value("${kinotic.orchestrator.node.heartbeatTimeoutSeconds:90}")
    private long heartbeatTimeoutSeconds;

    /**
     * How often (in seconds) the health check runs to look for stale nodes.
     */
    @Value("${kinotic.orchestrator.node.healthCheckIntervalSeconds:30}")
    private long healthCheckIntervalSeconds;

    private final VmNodeService vmNodeService;
    private final WorkloadService workloadService;
    private ScheduledExecutorService scheduler;

    public DefaultNodeOrchestrationService(VmNodeService vmNodeService,
                                           WorkloadService workloadService) {
        this.vmNodeService = vmNodeService;
        this.workloadService = workloadService;
    }

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "node-health-check");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::checkNodeHealth,
                                      healthCheckIntervalSeconds,
                                      healthCheckIntervalSeconds,
                                      TimeUnit.SECONDS);
        log.info("Node health check scheduled every {}s, timeout {}s",
                 healthCheckIntervalSeconds, heartbeatTimeoutSeconds);
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Override
    public CompletableFuture<VmNode> registerNode(VmNode node) {
        Validate.notNull(node, "Node cannot be null");
        Validate.notNull(node.getId(), "Node id cannot be null");

        return vmNodeService.findById(node.getId())
                .thenCompose(existing -> {
                    if (existing != null) {
                        existing.setHostname(node.getHostname())
                                .setName(node.getName())
                                .setTotalCpus(node.getTotalCpus())
                                .setTotalMemoryMb(node.getTotalMemoryMb())
                                .setTotalDiskMb(node.getTotalDiskMb())
                                .setStatus(VmNodeStatus.ONLINE)
                                .setLastSeen(new Date());
                        log.info("Re-registering VmNode: {} ({})", existing.getName(), existing.getId());
                        return vmNodeService.save(existing);
                    } else {
                        node.setStatus(VmNodeStatus.ONLINE);
                        node.setLastSeen(new Date());
                        log.info("Registering new VmNode: {} ({})", node.getName(), node.getId());
                        return vmNodeService.save(node);
                    }
                });
    }

    @Override
    public CompletableFuture<VmNode> heartbeat(String nodeId) {
        Validate.notNull(nodeId, "Node id cannot be null");

        return vmNodeService.findById(nodeId)
                .thenCompose(node -> {
                    if (node == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Node not registered: " + nodeId));
                    }
                    node.setLastSeen(new Date());
                    if (node.getStatus() == VmNodeStatus.OFFLINE) {
                        log.info("VmNode {} came back online", nodeId);
                        node.setStatus(VmNodeStatus.ONLINE);
                    }
                    return vmNodeService.save(node);
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

    /**
     * Periodically checks all ONLINE nodes and marks any that haven't sent a heartbeat
     * within the timeout as OFFLINE. Running workloads on offline nodes are marked FAILED.
     */
    private void checkNodeHealth() {
        try {
            long cutoff = System.currentTimeMillis() - (heartbeatTimeoutSeconds * 1000);
            Date cutoffDate = new Date(cutoff);

            vmNodeService.findAll(Pageable.create(0, 500, null))
                    .thenAccept(page -> {
                        for (VmNode node : page.getContent()) {
                            if (node.getStatus() == VmNodeStatus.ONLINE
                                    && node.getLastSeen() != null
                                    && node.getLastSeen().before(cutoffDate)) {

                                log.warn("VmNode {} ({}) missed heartbeat, marking OFFLINE",
                                         node.getName(), node.getId());

                                node.setStatus(VmNodeStatus.OFFLINE);
                                vmNodeService.save(node)
                                        .thenCompose(offlineNode -> markNodeWorkloadsFailed(offlineNode.getId()))
                                        .exceptionally(error -> {
                                            log.error("Error handling offline node {}", node.getId(), error);
                                            return null;
                                        });
                            }
                        }
                    })
                    .exceptionally(error -> {
                        log.error("Error during node health check", error);
                        return null;
                    });
        } catch (Exception e) {
            log.error("Unexpected error during node health check", e);
        }
    }

    private CompletableFuture<Void> markNodeWorkloadsFailed(String nodeId) {
        return workloadService.findAllForNode(nodeId, Pageable.create(0, 500, null))
                .thenCompose(page -> {
                    CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                    for (Workload workload : page.getContent()) {
                        if (workload.getStatus() == WorkloadStatus.RUNNING
                                || workload.getStatus() == WorkloadStatus.STARTING) {
                            chain = chain.thenCompose(v -> {
                                log.warn("Marking workload {} as FAILED due to node {} going offline",
                                         workload.getId(), nodeId);
                                workload.setStatus(WorkloadStatus.FAILED);
                                return workloadService.save(workload).thenApply(w -> null);
                            });
                        }
                    }
                    return chain;
                });
    }
}
