package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.workload.WorkloadStatus;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.config.VmNodeProperties;
import org.kinotic.system.api.services.VmNodeOrchestrationService;
import org.kinotic.domain.api.model.workload.VmNode;
import org.kinotic.system.api.workload.VmNodeRegistration;
import org.kinotic.system.api.workload.WorkloadStatusReport;
import org.kinotic.domain.api.model.workload.VmNodeStatus;
import org.kinotic.domain.api.model.workload.VmNodeStatusType;
import org.kinotic.domain.api.model.workload.Workload;
import org.kinotic.system.api.services.VmNodeService;
import org.kinotic.system.api.services.WorkloadService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultVmNodeOrchestrationService implements VmNodeOrchestrationService {

    private final KinoticSystemApiProperties orchestratorProperties;
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
    public Future<VmNode> registerNode(VmNodeRegistration registration) {
        Validate.notNull(registration, "Registration cannot be null");
        Validate.notNull(registration.getId(), "Node id cannot be null");

        return vmNodeService.findById(registration.getId())
                .compose(existing -> {
                    Future<VmNode> ret;
                    if (existing != null) {
                        existing.setHostname(registration.getHostname())
                                .setName(registration.getName())
                                .setProviderType(registration.getProviderType())
                                .setTotalCpus(registration.getTotalCpus())
                                .setTotalMemoryMb(registration.getTotalMemoryMb())
                                .setTotalDiskMb(registration.getTotalDiskMb())
                                .setWorkloadDataDir(registration.getWorkloadDataDir())
                                .setStatus(new VmNodeStatus());
                        log.info("Re-registering VmNode: {} ({})", existing.getName(), existing.getId());
                        ret = vmNodeService.saveSync(existing);
                    } else {
                        VmNode node = new VmNode(registration.getId(), registration.getName(), registration.getHostname());
                        node.setProviderType(registration.getProviderType());
                        node.setTotalCpus(registration.getTotalCpus());
                        node.setTotalMemoryMb(registration.getTotalMemoryMb());
                        node.setTotalDiskMb(registration.getTotalDiskMb());
                        node.setWorkloadDataDir(registration.getWorkloadDataDir());
                        node.setStatus(new VmNodeStatus());
                        log.info("Registering new VmNode: {} ({})", node.getName(), node.getId());
                        ret = vmNodeService.saveSync(node);
                    }
                    return ret;
                });
    }

    @Override
    public Future<VmNode> heartbeat(String nodeId, List<String> problems) {
        Validate.notNull(nodeId, "Node id cannot be null");
        List<String> found = problems == null ? List.of() : problems;
        String message = found.isEmpty() ? null : String.join("; ", found);
        VmNodeStatusType reported = found.isEmpty() ? VmNodeStatusType.ONLINE : VmNodeStatusType.DRAINING;

        return vmNodeService.findById(nodeId)
                .compose(node -> {
                    Future<VmNode> ret;
                    if (node == null) {
                        ret = Future.failedFuture(
                                new IllegalArgumentException("Node not registered: " + nodeId));
                    } else if (node.getStatus().getType() != reported
                            || !Objects.equals(node.getStatus().getHealthMessage(), message)) {
                        if (message != null) {
                            log.warn("VmNode {} is not fit for workloads: {}", nodeId, message);
                        } else {
                            log.info("VmNode {} is fit for workloads again", nodeId);
                        }
                        node.setStatus(new VmNodeStatus(reported, message));
                        // findAvailableNode selects on status.type with a search, so the change
                        // has to be in the index before the next placement reads it
                        ret = vmNodeService.saveSync(node);
                    } else {
                        // lastSeen is stamped by DefaultVmNodeService.beforeSave. Only
                        // checkNodeHealth reads it, via a search against a heartbeatTimeoutSeconds
                        // cutoff, so a refresh wait costs the caller the index refresh interval
                        // and buys nothing.
                        ret = vmNodeService.save(node);
                    }
                    return ret;
                });
    }

    @Override
    public Future<Void> reportWorkloadStatus(String nodeId, List<WorkloadStatusReport> reports) {
        Validate.notNull(nodeId, "Node id cannot be null");
        Validate.notNull(reports, "Reports cannot be null");

        return Future.all(reports.stream()
                                 .map(report -> applyStatusReport(nodeId, report))
                                 .toList())
                     .mapEmpty();
    }

    private Future<Workload> applyStatusReport(String nodeId, WorkloadStatusReport report) {
        return workloadService.findById(report.getWorkloadId())
                .compose(workload -> {
                    Future<Workload> ret;
                    if (workload == null) {
                        // Destroyed since the node recorded the status — stale report
                        ret = Future.succeededFuture();
                    } else if (!nodeId.equals(workload.getNodeId())) {
                        log.warn("Ignoring status report from node {} for workload {} deployed on node {}",
                                 nodeId, report.getWorkloadId(), workload.getNodeId());
                        ret = Future.succeededFuture(workload);
                    } else if (workload.getStatus() == report.getStatus()) {
                        // Same state; still adopt an exit code the record lacks — stopWorkload
                        // persists STOPPED before the node's exit-code-bearing report arrives
                        if (report.getExitCode() != null && workload.getExitCode() == null) {
                            workload.setExitCode(report.getExitCode());
                            ret = workloadService.saveSync(workload);
                        } else {
                            ret = Future.succeededFuture(workload);
                        }
                    } else if (workload.getUpdated() != null
                            && report.getUpdated() <= workload.getUpdated().getTime()) {
                        // A report older than the record's last transition must not clobber it
                        ret = Future.succeededFuture(workload);
                    } else {
                        log.info("Workload {} status {} -> {} per report from node {}",
                                 report.getWorkloadId(), workload.getStatus(), report.getStatus(), nodeId);
                        workload.setStatus(report.getStatus());
                        workload.setExitCode(report.getExitCode());
                        ret = workloadService.saveSync(workload);
                    }
                    return ret;
                });
    }

    @Override
    public Future<Void> deregisterNode(String nodeId) {
        Validate.notNull(nodeId, "Node id cannot be null");

        return workloadService.countForNode(nodeId)
                .compose(count -> {
                    if (count > 0) {
                        return Future.failedFuture(
                                new IllegalStateException("Cannot deregister node with active workloads. "
                                        + "Destroy all workloads on node " + nodeId + " first."));
                    }
                    log.info("Deregistering VmNode: {}", nodeId);
                    return vmNodeService.deleteById(nodeId);
                });
    }

    @Override
    public Future<VmNode> findAvailableNode(int requiredCpus, int requiredMemoryMb, int requiredDiskMb) {
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
                    .onSuccess(page -> {
                        for (VmNode node : page.getContent()) {
                            if (node.getStatus().getType() == VmNodeStatusType.ONLINE
                                    && node.getLastSeen() != null
                                    && node.getLastSeen().before(cutoffDate)) {

                                log.warn("VmNode {} ({}) missed heartbeat, marking OFFLINE",
                                         node.getName(), node.getId());

                                node.setStatus(new VmNodeStatus(VmNodeStatusType.OFFLINE, node.getStatus().getHealthMessage()));
                                vmNodeService.saveSync(node)
                                        .compose(offlineNode -> markNodeWorkloadsFailed(offlineNode.getId()))
                                        .onFailure(error -> log.error("Error handling offline node {}", node.getId(), error));
                            }
                        }
                    })
                    .onFailure(error -> log.error("Error during node health check", error));
        } catch (Exception e) {
            log.error("Unexpected error during node health check", e);
        }
    }

    private Future<Void> markNodeWorkloadsFailed(String nodeId) {
        return workloadService.findAllForNode(nodeId, Pageable.create(0, 500, null))
                .compose(page -> {
                    Future<Void> chain = Future.succeededFuture();
                    for (Workload workload : page.getContent()) {
                        if (workload.getStatus() == WorkloadStatus.RUNNING
                                || workload.getStatus() == WorkloadStatus.STARTING) {
                            chain = chain.compose(v -> {
                                log.warn("Marking workload {} as FAILED due to node {} going offline",
                                         workload.getId(), nodeId);
                                workload.setStatus(WorkloadStatus.FAILED);
                                return workloadService.saveSync(workload)
                                                      .mapEmpty();
                            });
                        }
                    }
                    return chain;
                });
    }
}
