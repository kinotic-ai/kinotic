package org.kinotic.orchestrator.internal.api.workload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.orchestrator.api.workload.VmNodeOrchestrationService;
import org.kinotic.orchestrator.api.workload.VmManagerProxy;
import org.kinotic.orchestrator.api.workload.WorkloadOrchestrationService;
import org.kinotic.os.api.model.workload.Workload;
import org.kinotic.os.api.model.workload.WorkloadStatus;
import org.kinotic.os.api.services.VmNodeService;
import org.kinotic.os.api.services.WorkloadService;
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

    @Override
    public CompletableFuture<Workload> deployWorkload(Workload workload) {
        Validate.notNull(workload, "Workload cannot be null");
        Validate.notNull(workload.getName(), "Workload name cannot be null");
        Validate.notNull(workload.getImage(), "Workload image cannot be null");

        // Find a node with sufficient resources
        return nodeOrchestrationService.findAvailableNode(workload.getVcpus(), workload.getMemoryMb(), workload.getDiskSizeMb())
                .thenCompose(node -> {
                    if (node == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("No available node with sufficient resources to deploy workload"));
                    }

                    log.info("Selected node {} for workload {}", node.getId(), workload.getName());

                    // Assign the workload to the selected node
                    workload.setNodeId(node.getId());
                    workload.setStatus(WorkloadStatus.STARTING);

                    // Persist the workload and update node resource allocation
                    return workloadService.saveSync(workload)
                            .thenCompose(savedWorkload -> {
                                node.setAllocatedCpus(node.getAllocatedCpus() + savedWorkload.getVcpus());
                                node.setAllocatedMemoryMb(node.getAllocatedMemoryMb() + savedWorkload.getMemoryMb());
                                node.setAllocatedDiskMb(node.getAllocatedDiskMb() + savedWorkload.getDiskSizeMb());
                                return vmNodeService.saveSync(node)
                                        .thenApply(updatedNode -> savedWorkload);
                            })
                            .thenCompose(savedWorkload ->
                                // Dispatch to the VmManager on the selected node
                                vmManagerProxy.startWorkload(node.getId(), savedWorkload)
                                        .thenCompose(startedWorkload -> {
                                            // VmManager started the workload, mark as RUNNING
                                            startedWorkload.setStatus(WorkloadStatus.RUNNING);
                                            return workloadService.saveSync(startedWorkload);
                                        })
                                        .exceptionallyCompose(error -> {
                                            log.error("Failed to start workload {} on node {}",
                                                      savedWorkload.getId(), node.getId(), error);
                                            savedWorkload.setStatus(WorkloadStatus.FAILED);
                                            return workloadService.saveSync(savedWorkload)
                                                    .thenCompose(failed -> CompletableFuture.failedFuture(error));
                                        })
                            );
                });
    }

    @Override
    public CompletableFuture<Void> stopWorkload(String workloadId) {
        Validate.notNull(workloadId, "Workload id cannot be null");

        return workloadService.findById(workloadId)
                .thenCompose(workload -> {
                    if (workload == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Workload not found: " + workloadId));
                    }

                    workload.setStatus(WorkloadStatus.STOPPING);
                    return workloadService.saveSync(workload);
                })
                .thenCompose(workload ->
                    vmManagerProxy.stopWorkload(workload.getNodeId(), workloadId)
                            .thenCompose(v -> {
                                workload.setStatus(WorkloadStatus.STOPPED);
                                return workloadService.saveSync(workload);
                            })
                )
                .thenApply(workload -> null);
    }

    @Override
    public CompletableFuture<Void> destroyWorkload(String workloadId) {
        Validate.notNull(workloadId, "Workload id cannot be null");

        return workloadService.findById(workloadId)
                .thenCompose(workload -> {
                    if (workload == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Workload not found: " + workloadId));
                    }

                    // Dispatch destroy to the VmManager on the workload's node
                    return vmManagerProxy.destroyWorkload(workload.getNodeId(), workloadId)
                            .thenCompose(v ->
                                // Free allocated resources on the node
                                vmNodeService.findById(workload.getNodeId())
                                        .thenCompose(node -> {
                                            if (node != null) {
                                                node.setAllocatedCpus(Math.max(0, node.getAllocatedCpus() - workload.getVcpus()));
                                                node.setAllocatedMemoryMb(Math.max(0, node.getAllocatedMemoryMb() - workload.getMemoryMb()));
                                                node.setAllocatedDiskMb(Math.max(0, node.getAllocatedDiskMb() - workload.getDiskSizeMb()));
                                                return vmNodeService.saveSync(node);
                                            }
                                            return CompletableFuture.completedFuture(node);
                                        })
                            )
                            .thenCompose(node -> workloadService.deleteById(workloadId));
                });
    }

    @Override
    public CompletableFuture<Workload> getWorkload(String workloadId) {
        Validate.notNull(workloadId, "Workload id cannot be null");
        return workloadService.findById(workloadId);
    }

    @Override
    public CompletableFuture<Page<Workload>> listWorkloads(Pageable pageable) {
        return workloadService.findAll(pageable);
    }

    @Override
    public CompletableFuture<Page<Workload>> listWorkloadsForNode(String nodeId, Pageable pageable) {
        return workloadService.findAllForNode(nodeId, pageable);
    }
}
