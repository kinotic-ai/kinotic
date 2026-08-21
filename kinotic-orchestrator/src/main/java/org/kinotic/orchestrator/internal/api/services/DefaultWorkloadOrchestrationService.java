package org.kinotic.orchestrator.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.orchestrator.api.services.VmNodeService;
import org.kinotic.orchestrator.api.services.WorkloadService;
import org.kinotic.orchestrator.api.services.VmNodeOrchestrationService;
import org.kinotic.orchestrator.api.workload.VmManagerProxy;
import org.kinotic.orchestrator.api.services.WorkloadOrchestrationService;
import org.kinotic.orchestrator.api.model.workload.VmNode;
import org.kinotic.orchestrator.api.model.workload.Workload;
import org.kinotic.orchestrator.api.model.workload.WorkloadStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultWorkloadOrchestrationService implements WorkloadOrchestrationService {

    private final VmNodeOrchestrationService nodeOrchestrationService;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private final VmManagerProxy vmManagerProxy;
    private final VmNodeService vmNodeService;
    private final WorkloadService workloadService;
    private final WorkloadCompletionTracker completionTracker;
    private final Vertx vertx;

    @Override
    public Future<Workload> deployWorkload(Workload workload) {
        Validate.notNull(workload, "Workload cannot be null");
        Validate.notNull(workload.getName(), "Workload name cannot be null");
        Validate.notNull(workload.getImage(), "Workload image cannot be null");

        // Find a node with sufficient resources
        return nodeOrchestrationService.findAvailableNode(workload.getVcpus(), workload.getMemoryMb(), workload.getDiskSizeMb())
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
                    return workloadService.saveSync(workload)
                            .compose(savedWorkload -> {
                                node.setAllocatedCpus(node.getAllocatedCpus() + savedWorkload.getVcpus());
                                node.setAllocatedMemoryMb(node.getAllocatedMemoryMb() + savedWorkload.getMemoryMb());
                                node.setAllocatedDiskMb(node.getAllocatedDiskMb() + savedWorkload.getDiskSizeMb());
                                return vmNodeService.saveSync(node)
                                        .map(savedWorkload);
                            })
                            .compose(savedWorkload ->
                                // Dispatch to the VmManager on the selected node
                                vmManagerProxy.startWorkload(node.getId(), savedWorkload)
                                        .compose(this::markRunning)
                                        .recover(error -> {
                                            log.error("Failed to start workload {} on node {}",
                                                      savedWorkload.getId(), node.getId(), error);
                                            savedWorkload.setStatus(WorkloadStatus.FAILED);
                                            return workloadService.saveSync(savedWorkload)
                                                    .compose(failed -> Future.failedFuture(error));
                                        })
                            );
                });
    }

    @Override
    public Future<Workload> runWorkload(Workload workload) {
        return deployWorkload(workload)
                .compose(deployed -> {
                    CompletableFuture<Workload> completion = completionTracker.awaitCompletion(deployed.getId());
                    // The node's terminal report can be applied before the waiter registers, so
                    // settle from the persisted record when the run has already ended
                    return workloadService.findById(deployed.getId())
                            .compose(current -> {
                                if (current == null) {
                                    completionTracker.workloadDestroyed(deployed.getId());
                                } else {
                                    completionTracker.workloadChanged(current);
                                }
                                return Future.fromCompletionStage(completion, vertx.getOrCreateContext());
                            });
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
                            .compose(this::markRunning)
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
                                return workloadService.saveSync(workload)
                                        .onSuccess(completionTracker::workloadChanged);
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
                            .compose(node -> workloadService.deleteById(workloadId))
                            .onSuccess(v -> completionTracker.workloadDestroyed(workloadId));
                });
    }

    /**
     * Persists the node's just-started view of the workload as RUNNING, keeping the persisted
     * record instead when it has already moved past STARTING.
     */
    private Future<Workload> markRunning(Workload startedWorkload) {
        return workloadService.findById(startedWorkload.getId())
                .compose(current -> {
                    Future<Workload> ret;
                    if (current == null) {
                        // Destroyed while starting — a save here would resurrect the record
                        ret = Future.succeededFuture(startedWorkload);
                    } else if (current.getStatus() != WorkloadStatus.STARTING) {
                        // A short-lived workload's terminal status report can be applied before
                        // the start reply gets here; that newer state must not be clobbered
                        ret = Future.succeededFuture(current);
                    } else {
                        startedWorkload.setStatus(WorkloadStatus.RUNNING);
                        ret = workloadService.saveSync(startedWorkload);
                    }
                    return ret;
                });
    }
}
