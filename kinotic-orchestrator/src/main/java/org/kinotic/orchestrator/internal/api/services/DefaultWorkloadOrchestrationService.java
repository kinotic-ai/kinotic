package org.kinotic.orchestrator.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.orchestrator.api.services.VmNodeService;
import org.kinotic.orchestrator.api.services.WorkloadService;
import org.kinotic.orchestrator.api.services.VmNodeOrchestrationService;
import org.kinotic.orchestrator.api.workload.VmManagerProxy;
import org.kinotic.orchestrator.api.services.WorkloadOrchestrationService;
import org.kinotic.orchestrator.api.model.workload.VmNode;
import org.kinotic.orchestrator.api.model.workload.VmNodeStatusType;
import org.kinotic.orchestrator.api.model.workload.Workload;
import org.kinotic.orchestrator.api.model.workload.WorkloadStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultWorkloadOrchestrationService implements WorkloadOrchestrationService {

    // What the persisted record holds in place of every environment value; only the node
    // ever receives the real values
    private static final String REDACTED_ENV_VALUE = "<redacted>";

    private final VmNodeOrchestrationService nodeOrchestrationService;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private final VmManagerProxy vmManagerProxy;
    private final VmNodeService vmNodeService;
    private final WorkloadService workloadService;

    @Override
    public Future<Workload> deployWorkload(Workload workload) {
        Validate.notNull(workload, "Workload cannot be null");
        Validate.notNull(workload.getName(), "Workload name cannot be null");
        Validate.notNull(workload.getImage(), "Workload image cannot be null");

        Future<VmNode> nodeFuture = workload.getNodeId() == null
                ? nodeOrchestrationService.findAvailableNode(workload.getVcpus(), workload.getMemoryMb(), workload.getDiskSizeMb())
                : requirePinnedNode(workload);
        return nodeFuture
                .compose(node -> {
                    if (node == null) {
                        return Future.failedFuture(
                                new IllegalStateException("No available node with sufficient resources to deploy workload"));
                    }

                    log.info("Selected node {} for workload {}", node.getId(), workload.getName());

                    // Assign the workload to the selected node
                    workload.setNodeId(node.getId());
                    workload.setStatus(WorkloadStatus.STARTING);

                    // Persist the workload and update node resource allocation
                    return persistRedacted(workload)
                            .compose(savedWorkload -> {
                                node.setAllocatedCpus(node.getAllocatedCpus() + savedWorkload.getVcpus());
                                node.setAllocatedMemoryMb(node.getAllocatedMemoryMb() + savedWorkload.getMemoryMb());
                                node.setAllocatedDiskMb(node.getAllocatedDiskMb() + savedWorkload.getDiskSizeMb());
                                return vmNodeService.saveSync(node)
                                        .map(savedWorkload);
                            })
                            .compose(savedWorkload ->
                                // Dispatch to the VmManager on the selected node. For a
                                // non-detached workload the reply arrives once the run ends.
                                vmManagerProxy.startWorkload(node.getId(), savedWorkload)
                                        .compose(this::applyStartReply)
                                        .recover(error -> {
                                            log.error("Failed to start workload {} on node {}",
                                                      savedWorkload.getId(), node.getId(), error);
                                            savedWorkload.setStatus(WorkloadStatus.FAILED);
                                            return persistRedacted(savedWorkload)
                                                    .compose(failed -> Future.failedFuture(error));
                                        })
                            );
                });
    }

    @Override
    public Future<Workload> restartWorkload(String workloadId) {
        Validate.notNull(workloadId, "Workload id cannot be null");

        return workloadService.findById(workloadId)
                .compose(workload -> {
                    if (workload == null) {
                        return Future.failedFuture(
                                new IllegalArgumentException("Workload not found: " + workloadId));
                    }
                    if (workload.getStatus() != WorkloadStatus.STOPPED) {
                        return Future.failedFuture(new IllegalStateException(
                                "Workload " + workloadId + " is not stopped (status: " + workload.getStatus() + ")"));
                    }

                    workload.setStatus(WorkloadStatus.STARTING);
                    return workloadService.saveSync(workload);
                })
                .compose(workload ->
                    vmManagerProxy.restartWorkload(workload.getNodeId(), workloadId)
                            .compose(this::applyStartReply)
                            .recover(error -> {
                                log.error("Failed to restart workload {} on node {}",
                                          workloadId, workload.getNodeId(), error);
                                workload.setStatus(WorkloadStatus.FAILED);
                                return workloadService.saveSync(workload)
                                        .compose(failed -> Future.failedFuture(error));
                            })
                );
    }

    @Override
    public Future<Void> stopWorkload(String workloadId) {
        Validate.notNull(workloadId, "Workload id cannot be null");

        return workloadService.findById(workloadId)
                .compose(workload -> {
                    if (workload == null) {
                        return Future.failedFuture(
                                new IllegalArgumentException("Workload not found: " + workloadId));
                    }

                    workload.setStatus(WorkloadStatus.STOPPING);
                    return workloadService.saveSync(workload);
                })
                .compose(workload ->
                    vmManagerProxy.stopWorkload(workload.getNodeId(), workloadId)
                            .compose(v -> {
                                workload.setStatus(WorkloadStatus.STOPPED);
                                return workloadService.saveSync(workload);
                            })
                )
                .mapEmpty();
    }

    @Override
    public Future<Void> destroyWorkload(String workloadId) {
        Validate.notNull(workloadId, "Workload id cannot be null");

        return workloadService.findById(workloadId)
                .compose(workload -> {
                    if (workload == null) {
                        return Future.failedFuture(
                                new IllegalArgumentException("Workload not found: " + workloadId));
                    }

                    // Dispatch destroy to the VmManager on the workload's node
                    return vmManagerProxy.destroyWorkload(workload.getNodeId(), workloadId)
                            .compose(v ->
                                // Free allocated resources on the node
                                vmNodeService.findById(workload.getNodeId())
                                        .compose(node -> {
                                            Future<VmNode> ret;
                                            if (node != null) {
                                                node.setAllocatedCpus(Math.max(0, node.getAllocatedCpus() - workload.getVcpus()));
                                                node.setAllocatedMemoryMb(Math.max(0, node.getAllocatedMemoryMb() - workload.getMemoryMb()));
                                                node.setAllocatedDiskMb(Math.max(0, node.getAllocatedDiskMb() - workload.getDiskSizeMb()));
                                                ret = vmNodeService.saveSync(node);
                                            } else {
                                                ret = Future.succeededFuture(node);
                                            }
                                            return ret;
                                        })
                            )
                            .compose(node -> workloadService.deleteById(workloadId));
                });
    }

    /**
     * Persists the node's reply to a start or restart dispatch and returns the workload's
     * resulting state.
     */
    private Future<Workload> applyStartReply(Workload startedWorkload) {
        return workloadService.findById(startedWorkload.getId())
                .compose(current -> {
                    Future<Workload> ret;
                    if (current == null) {
                        // Destroyed while the dispatch was in flight — a save here would
                        // resurrect the record
                        ret = Future.succeededFuture(startedWorkload);
                    } else if (startedWorkload.getStatus().isComplete()
                            || current.getStatus() == WorkloadStatus.STARTING) {
                        // A terminal reply — a non-detached run that already ended — is the
                        // node's final word. A RUNNING reply only promotes from STARTING: a
                        // short-lived detached workload's terminal status report can be
                        // applied before the reply gets here, and must not be clobbered.
                        ret = persistRedacted(startedWorkload);
                    } else {
                        ret = Future.succeededFuture(current);
                    }
                    return ret;
                });
    }

    /**
     * Resolves a workload's pre-assigned node, failing unless it is registered, ONLINE, and
     * has the capacity the workload requires — the same gates placement applies when it
     * selects a node.
     */
    private Future<VmNode> requirePinnedNode(Workload workload) {
        String nodeId = workload.getNodeId();
        return vmNodeService.findById(nodeId)
                .compose(node -> {
                    Future<VmNode> ret;
                    if (node == null) {
                        ret = Future.failedFuture(
                                new IllegalArgumentException("Node not registered: " + nodeId));
                    } else if (node.getStatus().getType() != VmNodeStatusType.ONLINE) {
                        ret = Future.failedFuture(new IllegalStateException(
                                "Node " + nodeId + " is not taking workloads (status: "
                                        + node.getStatus().getType() + ")"));
                    } else if (node.getAvailableCpus() < workload.getVcpus()
                            || node.getAvailableMemoryMb() < workload.getMemoryMb()
                            || node.getAvailableDiskMb() < workload.getDiskSizeMb()) {
                        ret = Future.failedFuture(new IllegalStateException(
                                "Node " + nodeId + " lacks capacity for workload " + workload.getName()));
                    } else {
                        ret = Future.succeededFuture(node);
                    }
                    return ret;
                });
    }

    /**
     * Persists the workload with every environment value replaced by
     * {@value REDACTED_ENV_VALUE}, then restores the real values on the object — the record
     * never holds an environment value, while dispatch to the node still carries them.
     */
    private Future<Workload> persistRedacted(Workload workload) {
        Future<Workload> ret;
        Map<String, String> environment = workload.getEnvironment();
        if (environment == null || environment.isEmpty()) {
            ret = workloadService.saveSync(workload);
        } else {
            Map<String, String> redacted = new LinkedHashMap<>();
            environment.keySet().forEach(key -> redacted.put(key, REDACTED_ENV_VALUE));
            workload.setEnvironment(redacted);
            ret = workloadService.saveSync(workload)
                    .andThen(result -> workload.setEnvironment(environment));
        }
        return ret;
    }
}
