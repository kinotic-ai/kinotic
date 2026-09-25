package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.ListenerStatus;
import org.kinotic.domain.api.model.ReconcileState;
import org.kinotic.domain.api.services.Reconciler;
import org.kinotic.domain.api.model.Requeue;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.StatusConditions;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.management.api.repositories.WorkloadRepository;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.model.workload.VmNodeState;
import org.kinotic.system.api.model.workload.VmNodeStatusType;
import org.kinotic.system.api.services.VmNodeOrchestrationService;
import org.kinotic.system.api.workload.VmManagerProxy;
import org.kinotic.system.api.workload.VmNodeRegistration;
import org.kinotic.system.api.workload.WorkloadStatusReport;
import org.kinotic.system.internal.api.repositories.VmNodeRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Keeps the record of each node true to what the node says and to what the platform can infer, and is
 * the node's worker: the reconcile master calls {@link #reconcile(VmNode)} for a node whose record
 * changed, for one not in its desired state, and whenever the node's heartbeat would next be overdue,
 * so a node that falls silent is marked unreachable with every run still open on it, and a node whose
 * deregistration was asked for is finalized.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultVmNodeOrchestrationService implements VmNodeOrchestrationService, Reconciler<VmNode> {

    // Workloads read per node in one page: what a node can host is far below this
    private static final int WORKLOAD_PAGE_SIZE = 500;

    private static final VmNodeState ONLINE = new VmNodeState(VmNodeStatusType.ONLINE);
    private static final VmNodeState DRAINING = new VmNodeState(VmNodeStatusType.DRAINING);

    private final KinoticSystemApiProperties orchestratorProperties;
    private final VmNodeRepository vmNodeRepository;
    private final WorkloadRepository workloadRepository;
    private final EventBusService eventBusService;
    private final Vertx vertx;

    @Override
    public Future<VmNode> registerNode(VmNodeRegistration registration) {
        Validate.notNull(registration, "Registration cannot be null");
        Validate.notNull(registration.getId(), "Node id cannot be null");
        String nodeId = registration.getId();

        return workloadRepository.findAllForNode(nodeId, Pageable.create(0, WORKLOAD_PAGE_SIZE, null))
                .compose(placed -> {
                    log.info("Registering VmNode: {} ({})", registration.getName(), nodeId);
                    // The workload records are what runs on the node: the free capacity is rebuilt
                    // from the runs still open, so a node that re-registers with different hardware,
                    // or after a release the node never saw, still accounts for exactly what it hosts.
                    List<Workload> open = placed.getContent().stream().filter(workload -> workload.getStatus().isOpen()).toList();
                    VmNode inventory = new VmNode(nodeId, registration.getName(), registration.getHostname())
                            .setProviderType(registration.getProviderType())
                            .setTotalCpus(registration.getTotalCpus())
                            .setTotalMemoryMb(registration.getTotalMemoryMb())
                            .setTotalDiskMb(registration.getTotalDiskMb())
                            .setWorkloadDataDir(registration.getWorkloadDataDir())
                            .setFreeCpus(registration.getTotalCpus() - open.stream().mapToDouble(Workload::getCpus).sum())
                            .setFreeMemoryMb(registration.getTotalMemoryMb() - open.stream().mapToInt(Workload::getMemoryMb).sum())
                            .setFreeDiskMb(registration.getTotalDiskMb() - open.stream().mapToInt(Workload::getDiskSizeMb).sum());
                    return vmNodeRepository.recordInventorySync(inventory);
                })
                // a registered node should be taking workloads; a node registering again holds that intent already
                .compose(v -> vmNodeRepository.updateDesired(nodeId, ONLINE, null, "registration"))
                // the registration is the node's own word that it is up, ahead of its first heartbeat
                .compose(node -> vmNodeRepository.reportObserved(nodeId, ONLINE, node.getState().getGeneration(), "registration"))
                .compose(v -> vmNodeRepository.clearCondition(nodeId, StatusConditionType.NODE_UNREACHABLE, "registration"))
                .compose(v -> vmNodeRepository.findById(nodeId));
    }

    @Override
    public Future<VmNode> heartbeat(String nodeId, List<String> problems) {
        Validate.notNull(nodeId, "Node id cannot be null");
        List<String> found = problems == null ? List.of() : problems;
        String message = found.isEmpty() ? null : String.join("; ", found);
        VmNodeState reported = found.isEmpty() ? ONLINE : DRAINING;

        return vmNodeRepository.findById(nodeId)
                .compose(node -> {
                    Future<VmNode> ret;
                    if (node == null) {
                        ret = Future.failedFuture(
                                new IllegalArgumentException("Node not registered: " + nodeId));
                    } else {
                        ReconcileState<VmNodeState> state = node.getState();
                        boolean reportChanged = !reported.equals(state.getObserved())
                                || state.getObservedGeneration() != state.getGeneration();
                        boolean marked = StatusConditions.has(state.getConditions(), StatusConditionType.NODE_UNREACHABLE);
                        if (reportChanged || !Objects.equals(node.getHealthMessage(), message)) {
                            if (message != null) {
                                log.warn("VmNode {} is not fit for workloads: {}", nodeId, message);
                            } else {
                                log.info("VmNode {} is fit for workloads again", nodeId);
                            }
                        }
                        // a heartbeat is the only evidence the node is alive; no other write stamps lastSeen
                        Future<Void> written = vmNodeRepository.recordHeartbeat(nodeId, message);
                        // the state is written only when the heartbeat changes it, which the read just made
                        // tells: the common heartbeat is one read and one write
                        if (reportChanged) {
                            written = written.compose(v -> vmNodeRepository.reportObserved(nodeId, reported, state.getGeneration(), "heartbeat"));
                        }
                        if (marked) {
                            // the heartbeat ends whatever silence or undelivered call the condition was inferred from
                            written = written.compose(v -> vmNodeRepository.clearCondition(nodeId, StatusConditionType.NODE_UNREACHABLE, "heartbeat")
                                                                       .mapEmpty());
                        }
                        ret = written.compose(v -> reportChanged || marked
                                ? vmNodeRepository.findById(nodeId)
                                : Future.succeededFuture(node.setLastSeen(new Date()).setHealthMessage(message)));
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
        return workloadRepository.findById(report.getWorkloadId())
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
                        // The condition was set from the node's silence, so the report that ends the
                        // silence is applied whatever it says and whether or not the status moved: the
                        // node is the authority on its run
                        log.info("Workload {} status {} -> {} per report from node {}, reachable again",
                                 report.getWorkloadId(), workload.getStatus(), report.getStatus(), nodeId);
                        ret = workloadRepository.clearCondition(workload.getId(), StatusConditionType.NODE_UNREACHABLE, "node " + nodeId)
                                             .compose(cleared -> applyReport(nodeId, workload, report));
                    } else if (workload.getStatus() == report.getStatus()) {
                        // Same state; still adopt an exit code the record lacks — stopWorkload
                        // records STOPPED before the node's exit-code-bearing report arrives
                        if (report.getExitCode() != null && workload.getExitCode() == null) {
                            ret = workloadRepository.updateRunSync(workload.getId(), workload.getStatus(), report.getExitCode(), "node " + nodeId);
                        } else {
                            ret = Future.succeededFuture();
                        }
                    } else if (!report.getStatus().isAfter(workload.getStatus())) {
                        // A run only moves forward, so a report of an earlier state is a snapshot the
                        // node took before the record's last transition, whichever clock stamped it
                        ret = Future.succeededFuture();
                    } else {
                        log.info("Workload {} status {} -> {} per report from node {}",
                                 report.getWorkloadId(), workload.getStatus(), report.getStatus(), nodeId);
                        ret = applyReport(nodeId, workload, report);
                    }

                    return ret;
                });
    }

    // Records the report's status and exit code, and returns the run's room when the report is what
    // ended it: a run the platform had recorded ended keeps its outcome, and its room stays returned
    private Future<Void> applyReport(String nodeId, Workload workload, WorkloadStatusReport report) {
        Future<Void> ret;
        if (report.getStatus().isComplete()) {
            ret = workloadRepository.endRunSync(workload.getId(), report.getStatus(), report.getExitCode(), "node " + nodeId)
                                 .compose(ended -> ended ? vmNodeRepository.releaseSync(nodeId, workload) : Future.succeededFuture());
        } else {
            ret = workloadRepository.updateRunSync(workload.getId(), report.getStatus(), report.getExitCode(), "node " + nodeId);
        }
        return ret;
    }

    @Override
    public Future<Void> deregisterNode(String nodeId) {
        Validate.notNull(nodeId, "Node id cannot be null");

        return vmNodeRepository.findById(nodeId)
                .compose(node -> {
                    Future<Void> ret;
                    if (node == null) {
                        ret = Future.failedFuture(new IllegalArgumentException("Node not registered: " + nodeId));
                    } else if (StatusConditions.has(node.getState().getConditions(), StatusConditionType.NODE_UNREACHABLE)) {
                        // The operator's word that a silent node is not coming back settles what its
                        // silence left open: the finalizer records its open runs FAILED
                        ret = vmNodeRepository.requestDeletion(nodeId, "deregisterNode");
                    } else {
                        ret = workloadRepository.countRunningForNode(nodeId)
                                .compose(count -> count > 0
                                        ? Future.failedFuture(new IllegalStateException("Cannot deregister node with running workloads. "
                                                + "Stop or destroy the workloads running on node " + nodeId + " first."))
                                        : vmNodeRepository.requestDeletion(nodeId, "deregisterNode"));
                    }
                    return ret;
                });
    }

    @Override
    public Future<VmNode> findAvailableNode(double requiredCpus, int requiredMemoryMb, int requiredDiskMb) {
        return vmNodeRepository.findAvailableNode(requiredCpus, requiredMemoryMb, requiredDiskMb);
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
                                   : vmNodeRepository.findById(nodeId).compose(node -> {
                                       // a call may name a node that was deregistered since, which is nothing to mark
                                       Future<Void> marked;
                                       if (node == null) {
                                           marked = Future.succeededFuture();
                                       } else {
                                           marked = markUnreachable(node, "Node " + nodeId + " holds no vm-manager registration",
                                                                    "unanswered call");
                                       }
                                       return marked;
                                   }));
    }

    @Override
    public WatchedType type() {
        return WatchedType.VM_NODE;
    }

    @Override
    public Future<Requeue> reconcile(VmNode node) {
        Future<Requeue> ret;
        if (node.getState().getDeletionRequested() != null) {
            ret = finalizeDeregistration(node);
        } else {
            ret = watchHeartbeat(node);
        }
        return ret;
    }

    /**
     * Marks a node silent past the heartbeat timeout unreachable, whatever it reported last, with every
     * run still open on it, and asks to be called again once the next heartbeat would be overdue.
     */
    private Future<Requeue> watchHeartbeat(VmNode node) {
        long timeoutMs = orchestratorProperties.getSystemApi().getVmNode().getHeartbeatTimeoutSeconds() * 1000;
        long silentMs = node.getLastSeen() == null ? 0 : System.currentTimeMillis() - node.getLastSeen().getTime();
        Future<Void> marked;
        long wait;
        if (node.getLastSeen() != null && silentMs >= timeoutMs) {
            // the open runs are marked again on every look, so a run placed between the node's mark
            // and the ledger read is not missed; a mark already in place is left as it is
            marked = markUnreachable(node, "Node " + node.getId() + " missed its heartbeat", "heartbeat timeout")
                    .compose(v -> markNodeWorkloadsUnreachable(node.getId()));
            wait = timeoutMs;
        } else {
            marked = Future.succeededFuture();
            wait = timeoutMs - silentMs;
        }
        return marked.map(Requeue.after(Duration.ofMillis(wait)));
    }

    private Future<Void> markUnreachable(VmNode node, String message, String source) {
        return vmNodeRepository.setCondition(node.getId(),
                                          new StatusCondition(StatusConditionType.NODE_UNREACHABLE, message, new Date()),
                                          source)
                            .onSuccess(set -> {
                                if (set) {
                                    log.warn("VmNode {} ({}) is unreachable, taking no workloads: {}", node.getName(), node.getId(), message);
                                }
                            })
                            .mapEmpty();
    }

    // A run the node last reported open may still be running on the other side of a partition, so
    // the record keeps its status and its room; the node's next report settles both, and a
    // deregistration settles them when the node is not coming back
    private Future<Void> markNodeWorkloadsUnreachable(String nodeId) {
        StatusCondition unreachable = new StatusCondition(StatusConditionType.NODE_UNREACHABLE,
                                                          "Node " + nodeId + " missed its heartbeat",
                                                          new Date());
        return forEachOpenRun(nodeId, workload -> workloadRepository.setCondition(workload.getId(), unreachable, "heartbeat timeout")
                .onSuccess(set -> {
                    if (set) {
                        log.warn("Marked workload {} unreachable: node {} missed its heartbeat", workload.getId(), nodeId);
                    }
                })
                .mapEmpty());
    }

    /**
     * Records every run still open on the node FAILED, which routes each to the deployment it belongs
     * to, and deletes the node. The runs' room goes with the node's record, so none is returned.
     */
    private Future<Requeue> finalizeDeregistration(VmNode node) {
        String nodeId = node.getId();
        log.info("Deregistering VmNode {}: its open runs are recorded FAILED", nodeId);
        return forEachOpenRun(nodeId, workload -> {
                    log.warn("Recording workload {} FAILED: node {} was deregistered while it was {}",
                             workload.getId(), nodeId, workload.getStatus());
                    return workloadRepository.endRunSync(workload.getId(), WorkloadStatus.FAILED, null, "deregistration of node " + nodeId).mapEmpty();
                })
                .compose(v -> vmNodeRepository.deleteByIdSync(nodeId))
                .map(Requeue.NONE);
    }

    // Applies the change to every open run on the node, one at a time
    private Future<Void> forEachOpenRun(String nodeId, Function<Workload, Future<Void>> change) {
        return workloadRepository.findAllForNode(nodeId, Pageable.create(0, WORKLOAD_PAGE_SIZE, null))
                .compose(page -> {
                    Future<Void> chain = Future.succeededFuture();
                    for (Workload workload : page.getContent()) {
                        if (workload.getStatus().isOpen()) {
                            chain = chain.compose(v -> change.apply(workload));
                        }
                    }
                    return chain;
                });
    }
}
