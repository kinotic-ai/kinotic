package org.kinotic.orchestrator.internal.api.workload;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.orchestrator.api.config.KinoticOrchestratorProperties;
import org.kinotic.orchestrator.api.config.VmNodeProperties;
import org.kinotic.orchestrator.api.workload.VmNodeOrchestrationService;
import org.kinotic.os.api.model.workload.VmNode;
import org.kinotic.orchestrator.api.workload.VmNodeRegistration;
import org.kinotic.os.api.model.workload.VmNodeStatus;
import org.kinotic.os.api.model.workload.Workload;
import org.kinotic.os.api.model.workload.WorkloadStatus;
import org.kinotic.os.api.services.VmNodeService;
import org.kinotic.os.api.services.WorkloadService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultVmNodeOrchestrationService implements VmNodeOrchestrationService {

    private final KinoticOrchestratorProperties orchestratorProperties;
    private final VmNodeService vmNodeService;
    private final WorkloadService workloadService;
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        VmNodeProperties nodeProps = orchestratorProperties.getOrchestrator().getVmNode();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "node-health-check");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::checkNodeHealth,
                                      nodeProps.getHealthCheckIntervalSeconds(),
                                      nodeProps.getHealthCheckIntervalSeconds(),
                                      TimeUnit.SECONDS);
        log.info("Node health check scheduled every {}s, timeout {}s",
                 nodeProps.getHealthCheckIntervalSeconds(), nodeProps.getHeartbeatTimeoutSeconds());
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Override
    public CompletableFuture<VmNode> registerNode(VmNodeRegistration registration) {
        Validate.notNull(registration, "Registration cannot be null");
        Validate.notNull(registration.getId(), "Node id cannot be null");

        return vmNodeService.findById(registration.getId())
                .thenCompose(existing -> {
                    if (existing != null) {
                        existing.setHostname(registration.getHostname())
                                .setName(registration.getName())
                                .setTotalCpus(registration.getTotalCpus())
                                .setTotalMemoryMb(registration.getTotalMemoryMb())
                                .setTotalDiskMb(registration.getTotalDiskMb())
                                .setStatus(VmNodeStatus.ONLINE);
                        log.info("Re-registering VmNode: {} ({})", existing.getName(), existing.getId());
                        return vmNodeService.saveSync(existing);
                    } else {
                        VmNode node = new VmNode(registration.getId(), registration.getName(), registration.getHostname());
                        node.setTotalCpus(registration.getTotalCpus());
                        node.setTotalMemoryMb(registration.getTotalMemoryMb());
                        node.setTotalDiskMb(registration.getTotalDiskMb());
                        node.setStatus(VmNodeStatus.ONLINE);
                        log.info("Registering new VmNode: {} ({})", node.getName(), node.getId());
                        return vmNodeService.saveSync(node);
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
                    return vmNodeService.saveSync(node);
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
    public CompletableFuture<VmNode> findAvailableNode(int requiredCpus, int requiredMemoryMb, int requiredDiskMb) {
        return vmNodeService.findAvailableNode(requiredCpus, requiredMemoryMb, requiredDiskMb);
    }

    /**
     * Periodically checks all ONLINE nodes and marks any that haven't sent a heartbeat
     * within the timeout as OFFLINE. Running workloads on offline nodes are marked FAILED.
     */
    private void checkNodeHealth() {
        try {
            long heartbeatTimeoutSeconds = orchestratorProperties.getOrchestrator().getVmNode().getHeartbeatTimeoutSeconds();
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
                                vmNodeService.saveSync(node)
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
                                return workloadService.saveSync(workload).thenApply(w -> null);
                            });
                        }
                    }
                    return chain;
                });
    }
}
