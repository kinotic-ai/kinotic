package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.kinotic.system.api.workload.VmManagerProxy;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.core.api.event.ListenerStatus;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.reconcile.StatusCondition;
import org.kinotic.core.api.reconcile.StatusConditionType;
import org.kinotic.core.api.reconcile.StatusConditions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.config.VmNodeProperties;
import org.kinotic.system.api.services.VmNodeOrchestrationService;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.workload.VmNodeRegistration;
import org.kinotic.system.api.workload.WorkloadStatusReport;
import org.kinotic.system.api.model.workload.VmNodeStatus;
import org.kinotic.system.api.model.workload.VmNodeStatusType;
import org.kinotic.system.api.model.workload.WorkloadReservation;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.system.api.services.VmNodeService;
import org.kinotic.system.api.services.WorkloadService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultVmNodeOrchestrationService implements VmNodeOrchestrationService {

    // Workloads read per node in one page: what a node can host is far below this
    private static final int WORKLOAD_PAGE_SIZE = 500;

    private final KinoticSystemApiProperties orchestratorProperties;
    private final VmNodeService vmNodeService;
    private final WorkloadService workloadService;
    private final EventBusService eventBusService;
    private final Vertx vertx;
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        VmNodeProperties nodeProps = orchestratorProperties.getSystemApi().getVmNode();
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

        return Future.all(vmNodeService.findById(registration.getId()),
                          workloadService.findAllForNode(registration.getId(), Pageable.create(0, WORKLOAD_PAGE_SIZE, null)))
                .compose(results -> {
                    VmNode existing = results.resultAt(0);
                    Page<Workload> placed = results.resultAt(1);
                    VmNode node;
                    if (existing != null) {
                        node = existing;
                        log.info("Re-registering VmNode: {} ({})", registration.getName(), registration.getId());
                    } else {
                        node = new VmNode(registration.getId(), registration.getName(), registration.getHostname());
                        log.info("Registering new VmNode: {} ({})", registration.getName(), registration.getId());
                    }
                    // The workload records are what runs on the node: the ledger is rebuilt from
                    // them, so a node that re-registers with different hardware, or after a release
                    // the node never saw, still accounts for exactly what it hosts.
                    List<WorkloadReservation> reservations = reservationsOf(placed.getContent());
                    node.setHostname(registration.getHostname())
                        .setName(registration.getName())
                        .setProviderType(registration.getProviderType())
                        .setTotalCpus(registration.getTotalCpus())
                        .setTotalMemoryMb(registration.getTotalMemoryMb())
                        .setTotalDiskMb(registration.getTotalDiskMb())
                        .setWorkloadDataDir(registration.getWorkloadDataDir())
                        .setReservations(reservations)
                        .setFreeCpus(registration.getTotalCpus()
                                - reservations.stream().mapToDouble(WorkloadReservation::getCpus).sum())
                        .setFreeMemoryMb(registration.getTotalMemoryMb()
                                - reservations.stream().mapToInt(WorkloadReservation::getMemoryMb).sum())
                        .setFreeDiskMb(registration.getTotalDiskMb()
                                - reservations.stream().mapToInt(WorkloadReservation::getDiskMb).sum())
                        .setStatus(new VmNodeStatus())
                        .setLastSeen(new Date());
                    return vmNodeService.saveSync(node);
                });
    }

    /** The room the given workloads hold: one reservation per run that has not ended. */
    private static List<WorkloadReservation> reservationsOf(List<Workload> workloads) {
        List<WorkloadReservation> ret = new ArrayList<>();
        for (Workload workload : workloads) {
            if (runOpen(workload)) {
                ret.add(WorkloadReservation.forRun(workload));
            }
        }
        return ret;
    }

    // A STOPPING workload is one whose stop never got an answer from the node
    private static boolean runOpen(Workload workload) {
        return workload.getStatus() == WorkloadStatus.STARTING
                || workload.getStatus() == WorkloadStatus.RUNNING
                || workload.getStatus() == WorkloadStatus.STOPPING;
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
                        // a heartbeat is the only evidence the node is alive; no other save stamps lastSeen
                        node.setLastSeen(new Date());
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
                        node.setLastSeen(new Date());
                        // Only checkNodeHealth reads lastSeen, through a search against the
                        // heartbeatTimeoutSeconds cutoff, so waiting for the index refresh buys nothing
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

    private Future<Void> applyStatusReport(String nodeId, WorkloadStatusReport report) {
        return workloadService.findById(report.getWorkloadId())
                .compose(workload -> {
                    Future<Void> ret;

                    if (workload == null) {
                        // Destroyed since the node recorded the status — stale report
                        ret = Future.succeededFuture();
                    } else if (!nodeId.equals(workload.getNodeId())) {
                        log.warn("Ignoring status report from node {} for workload {} deployed on node {}",
                                 nodeId, report.getWorkloadId(), workload.getNodeId());
                        ret = Future.succeededFuture();
                    } else if (StatusConditions.has(workload.getState().getConditions(), StatusConditionType.NODE_UNREACHABLE)) {
                        // The condition was set from the node's silence, on the server's clock, so the
                        // report that ends the silence is applied whatever the timestamps say and whether
                        // or not the status moved: the node is the authority on its run
                        log.info("Workload {} status {} -> {} per report from node {}, reachable again",
                                 report.getWorkloadId(), workload.getStatus(), report.getStatus(), nodeId);
                        ret = workloadService.clearCondition(workload.getId(), StatusConditionType.NODE_UNREACHABLE)
                                             .compose(cleared -> applyReport(nodeId, workload, report));
                    } else if (workload.getStatus() == report.getStatus()) {
                        // Same state; still adopt an exit code the record lacks — stopWorkload
                        // records STOPPED before the node's exit-code-bearing report arrives
                        if (report.getExitCode() != null && workload.getExitCode() == null) {
                            ret = workloadService.updateRunSync(workload.getId(), workload.getStatus(), report.getExitCode());
                        } else {
                            ret = Future.succeededFuture();
                        }
                    } else if (workload.getUpdated() != null
                            && report.getUpdated() <= workload.getUpdated().getTime()) {
                        // A report older than the record's last transition must not clobber it
                        ret = Future.succeededFuture();
                    } else {
                        log.info("Workload {} status {} -> {} per report from node {}",
                                 report.getWorkloadId(), workload.getStatus(), report.getStatus(), nodeId);
                        ret = applyReport(nodeId, workload, report);
                    }

                    return ret;
                });
    }

    // Records the report's status and exit code, and returns the run's room once the report says it ended
    private Future<Void> applyReport(String nodeId, Workload workload, WorkloadStatusReport report) {
        Future<Void> ret = workloadService.updateRunSync(workload.getId(), report.getStatus(), report.getExitCode());
        if (report.getStatus().isComplete()) {
            ret = ret.compose(v -> vmNodeService.releaseSync(nodeId, workload.getId()));
        }
        return ret;
    }

    @Override
    public Future<Void> deregisterNode(String nodeId) {
        Validate.notNull(nodeId, "Node id cannot be null");

        return vmNodeService.findById(nodeId)
                .compose(node -> {
                    Future<Void> ret;
                    if (node != null && node.getStatus().getType() == VmNodeStatusType.OFFLINE) {
                        // The operator's word that the node is not coming back settles what its silence
                        // left open. The node record and its ledger go with it, so no room is returned.
                        log.info("Deregistering OFFLINE VmNode {}: its open runs are recorded FAILED", nodeId);
                        ret = forEachOpenRun(nodeId, workload -> {
                            log.warn("Recording workload {} FAILED: node {} was deregistered while it was {}",
                                     workload.getId(), nodeId, workload.getStatus());
                            return workloadService.updateRunSync(workload.getId(), WorkloadStatus.FAILED, null);
                        }).compose(v -> vmNodeService.deleteById(nodeId));
                    } else {
                        ret = workloadService.countRunningForNode(nodeId)
                                .compose(count -> {
                                    Future<Void> deleted;
                                    if (count > 0) {
                                        deleted = Future.failedFuture(
                                                new IllegalStateException("Cannot deregister node with running workloads. "
                                                        + "Stop or destroy the workloads running on node " + nodeId + " first."));
                                    } else {
                                        log.info("Deregistering VmNode: {}", nodeId);
                                        deleted = vmNodeService.deleteById(nodeId);
                                    }
                                    return deleted;
                                });
                    }
                    return ret;
                });
    }

    @Override
    public Future<VmNode> findAvailableNode(double requiredCpus, int requiredMemoryMb, int requiredDiskMb) {
        return vmNodeService.findAvailableNode(requiredCpus, requiredMemoryMb, requiredDiskMb);
    }

    @Override
    public Future<Void> verifyNode(String nodeId) {
        Validate.notNull(nodeId, "Node id cannot be null");
        ServiceIdentifier vmManager = KinoticUtil.serviceIdentifierOf(VmManagerProxy.class);
        CRI address = new ServiceIdentifier(vmManager.zone(), vmManager.namespace(), vmManager.name(), nodeId, vmManager.version()).cri();
        // the first emission is the current registration state; it arrives on the monitor's delivery
        // context, and the caller's context is restored before anything else runs
        return Future.fromCompletionStage(eventBusService.monitorListenerStatus(address).next().toFuture(),
                                          vertx.getOrCreateContext())
                           .compose(status -> status == ListenerStatus.ACTIVE
                                   ? Future.succeededFuture()
                                   : vmNodeService.findById(nodeId).compose(node -> markUnreachable(nodeId, node)));
    }

    private Future<Void> markUnreachable(String nodeId, VmNode node) {
        Future<Void> ret;
        if (node == null || node.getStatus().getType() == VmNodeStatusType.OFFLINE
                || node.getStatus().getType() == VmNodeStatusType.UNREACHABLE) {
            ret = Future.succeededFuture();
        } else {
            log.warn("VmNode {} ({}) is unreachable and holds no vm-manager registration, taking no workloads", node.getName(), nodeId);
            // Only the status is written, so a heartbeat that landed after the read keeps its lastSeen.
            // findAvailableNode selects on status.type with a search, so the change has to be indexed first.
            ret = vmNodeService.updateStatusSync(nodeId,
                                                 new VmNodeStatus(VmNodeStatusType.UNREACHABLE, node.getStatus().getHealthMessage()));
        }
        return ret;
    }

    /**
     * Periodically marks every node that has not sent a heartbeat within the timeout OFFLINE, whatever it
     * reported last, and marks every workload whose run is still open on it NODE_UNREACHABLE.
     */
    private void checkNodeHealth() {
        try {
            long heartbeatTimeoutSeconds = orchestratorProperties.getSystemApi().getVmNode().getHeartbeatTimeoutSeconds();
            long cutoff = System.currentTimeMillis() - (heartbeatTimeoutSeconds * 1000);
            Date cutoffDate = new Date(cutoff);

            vmNodeService.findAll(Pageable.create(0, 500, null))
                    .onSuccess(page -> {
                        for (VmNode node : page.getContent()) {
                            // a DRAINING or UNREACHABLE node that falls silent is as dead as an ONLINE one
                            if (node.getStatus().getType() != VmNodeStatusType.OFFLINE
                                    && node.getLastSeen() != null
                                    && node.getLastSeen().before(cutoffDate)) {

                                log.warn("VmNode {} ({}) missed heartbeat, marking OFFLINE",
                                         node.getName(), node.getId());

                                // Only the status is written, so a heartbeat that landed after findAll read
                                // the node keeps its lastSeen
                                vmNodeService.updateStatusSync(node.getId(),
                                                               new VmNodeStatus(VmNodeStatusType.OFFLINE, node.getStatus().getHealthMessage()))
                                        .compose(v -> markNodeWorkloadsUnreachable(node.getId()))
                                        .onFailure(error -> log.error("Error handling offline node {}", node.getId(), error));
                            }
                        }
                    })
                    .onFailure(error -> log.error("Error during node health check", error));
        } catch (Exception e) {
            log.error("Unexpected error during node health check", e);
        }
    }

    // A run the node last reported open may still be running on the other side of a partition, so
    // the record keeps its status and its room; the node's next report settles both, and a
    // deregistration settles them when the node is not coming back
    private Future<Void> markNodeWorkloadsUnreachable(String nodeId) {
        StatusCondition unreachable = new StatusCondition(StatusConditionType.NODE_UNREACHABLE,
                                                          "Node " + nodeId + " missed its heartbeat",
                                                          new Date());
        return forEachOpenRun(nodeId, workload -> workloadService.setCondition(workload.getId(), unreachable)
                .onSuccess(set -> {
                    if (set) {
                        log.warn("Marked workload {} unreachable: node {} missed its heartbeat", workload.getId(), nodeId);
                    }
                })
                .mapEmpty());
    }

    // Applies the change to every open run on the node, one at a time
    private Future<Void> forEachOpenRun(String nodeId, Function<Workload, Future<Void>> change) {
        return workloadService.findAllForNode(nodeId, Pageable.create(0, WORKLOAD_PAGE_SIZE, null))
                .compose(page -> {
                    Future<Void> chain = Future.succeededFuture();
                    for (Workload workload : page.getContent()) {
                        if (runOpen(workload)) {
                            chain = chain.compose(v -> change.apply(workload));
                        }
                    }
                    return chain;
                });
    }
}
