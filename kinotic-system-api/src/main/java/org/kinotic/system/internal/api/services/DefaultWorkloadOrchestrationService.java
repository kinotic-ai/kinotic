package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.exceptions.RpcServiceUnavailableException;
import org.kinotic.core.api.exceptions.RpcMissingServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.system.api.services.VmNodeService;
import org.kinotic.system.api.services.WorkloadService;
import org.kinotic.system.api.services.VmNodeOrchestrationService;
import org.kinotic.system.api.workload.VmManagerProxy;
import org.kinotic.system.api.services.WorkloadOrchestrationService;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.model.workload.VmNodeStatusType;
import org.kinotic.system.api.model.workload.WorkloadReservation;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultWorkloadOrchestrationService implements WorkloadOrchestrationService {

    // What the persisted record holds in place of every secret value; only the node
    // ever receives the real values
    private static final String REDACTED_SECRET_VALUE = "<redacted>";

    // Placements a deploy may make before it gives up on the room concurrent deploys keep taking
    private static final int PLACEMENT_ATTEMPTS = 3;

    private final VmNodeOrchestrationService nodeOrchestrationService;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private final VmManagerProxy vmManagerProxy;
    private final VmNodeService vmNodeService;
    private final WorkloadService workloadService;

    // A call the bus could not deliver, or whose serving node left, is the earliest sign a node is gone;
    // the orchestrator checks the node at once instead of waiting for the heartbeat timeout
    private <T> Future<T> verifyingNodeOnFailure(String nodeId, Future<T> call) {
        return call.onFailure(error -> {
            if (error instanceof RpcMissingServiceException || error instanceof RpcServiceUnavailableException) {
                nodeOrchestrationService.verifyNode(nodeId)
                                        .onFailure(verifyError -> log.warn("Could not verify node {} after an unreachable vm-manager", nodeId, verifyError));
            }
        });
    }

    @Override
    public Future<Workload> deployWorkload(Workload workload) {
        Validate.notNull(workload, "Workload cannot be null");
        Validate.notNull(workload.getName(), "Workload name cannot be null");
        Validate.notNull(workload.getImage(), "Workload image cannot be null");
        // the node records the reservation under the workload's id, so the id exists before placement
        if (workload.getId() == null) {
            workload.setId(UUID.randomUUID().toString());
        }

        Future<VmNode> nodeFuture = workload.getNodeId() == null
                ? placeWorkload(workload, 1)
                : reserveOnPinnedNode(workload);
        return nodeFuture
                .compose(node -> {
                    log.info("Selected node {} for workload {}", node.getId(), workload.getName());

                    // Assign the workload to the selected node
                    workload.setNodeId(node.getId());
                    workload.setStatus(WorkloadStatus.STARTING);

                    return persistRedacted(workload)
                            // the reservation is the workload's; a record that cannot be written hands it back
                            .recover(error -> vmNodeService.releaseSync(node.getId(), workload.getId())
                                                           .transform(_ -> Future.failedFuture(error)))
                            .compose(savedWorkload ->
                                // Dispatch to the VmManager on the selected node. For a
                                // non-detached workload the reply arrives once the run ends.
                                verifyingNodeOnFailure(node.getId(), vmManagerProxy.startWorkload(node.getId(), savedWorkload))
                                        .compose(this::applyStartReply)
                                        .recover(error -> {
                                            log.error("Failed to start workload {} on node {}",
                                                      savedWorkload.getId(), node.getId(), error);
                                            // a foreground workload leaves nothing behind whatever ended
                                            // it; a detached one is recorded failed with its VM kept
                                            Future<Workload> ended = savedWorkload.isDetached()
                                                    ? recordRunEnded(savedWorkload, WorkloadStatus.FAILED)
                                                    : destroyWorkload(savedWorkload.getId()).map(savedWorkload);
                                            return ended.transform(_ -> Future.failedFuture(error));
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

                    // the run that ended gave its CPU and memory back, so a new run takes them again
                    return vmNodeService.reserveSync(workload.getNodeId(), WorkloadReservation.forRun(workload))
                            .compose(reserved -> {
                                Future<Workload> ret;
                                if (reserved) {
                                    workload.setStatus(WorkloadStatus.STARTING);
                                    ret = workloadService.saveSync(workload);
                                } else {
                                    ret = Future.failedFuture(new IllegalStateException(
                                            "Node " + workload.getNodeId() + " lacks capacity to restart workload " + workloadId));
                                }
                                return ret;
                            });
                })
                .compose(workload ->
                    verifyingNodeOnFailure(workload.getNodeId(), vmManagerProxy.restartWorkload(workload.getNodeId(), workloadId))
                            .compose(this::applyStartReply)
                            .recover(error -> {
                                log.error("Failed to restart workload {} on node {}",
                                          workloadId, workload.getNodeId(), error);
                                return recordRunEnded(workload, WorkloadStatus.FAILED)
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
                    verifyingNodeOnFailure(workload.getNodeId(), vmManagerProxy.stopWorkload(workload.getNodeId(), workloadId))
                            .compose(v -> recordRunEnded(workload, WorkloadStatus.STOPPED))
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
                    return verifyingNodeOnFailure(workload.getNodeId(), vmManagerProxy.destroyWorkload(workload.getNodeId(), workloadId))
                            .compose(v -> vmNodeService.releaseSync(workload.getNodeId(), workloadId))
                            .compose(v -> workloadService.deleteById(workloadId));
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
                    } else if (startedWorkload.getStatus().isComplete() && !startedWorkload.isDetached()) {
                        // A foreground run is over once its reply arrives, and nothing of it stays
                        // on the node or in the store: its logs are in the log store, and the
                        // caller holds its outcome in the reply
                        ret = destroyWorkload(startedWorkload.getId()).map(startedWorkload);
                    } else if (startedWorkload.getStatus().isComplete()) {
                        // A detached run's VM is kept for a restart, so the run's reservation ends
                        // and its disk stays held
                        ret = persistRedacted(startedWorkload)
                                .compose(persisted -> vmNodeService.releaseRunSync(persisted.getNodeId(), persisted.getId())
                                                                   .map(persisted));
                    } else if (current.getStatus() == WorkloadStatus.STARTING) {
                        // A RUNNING reply only promotes from STARTING: a short-lived detached
                        // workload's terminal status report can be applied before the reply
                        // gets here, and must not be clobbered.
                        ret = persistRedacted(startedWorkload);
                    } else {
                        ret = Future.succeededFuture(current);
                    }
                    return ret;
                });
    }

    /**
     * Picks a node with room for the workload and reserves that room on it. The pick reads the index and the
     * reservation is atomic on the node, so a concurrent deploy that took the same room in between is answered
     * by a declined reservation, and the pick runs again on the capacity that is left.
     */
    private Future<VmNode> placeWorkload(Workload workload, int attempt) {
        return nodeOrchestrationService.findAvailableNode(workload.getCpus(), workload.getMemoryMb(), workload.getDiskSizeMb())
                .compose(node -> {
                    Future<VmNode> ret;
                    if (node == null) {
                        ret = Future.failedFuture(
                                new IllegalStateException("No available node with sufficient resources to deploy workload"));
                    } else {
                        ret = reserve(node, workload)
                                .compose(reserved -> {
                                    Future<VmNode> placed;
                                    if (reserved) {
                                        placed = Future.succeededFuture(node);
                                    } else if (attempt < PLACEMENT_ATTEMPTS) {
                                        log.info("Node {} was allocated to another workload while placing {}, picking again",
                                                 node.getId(), workload.getName());
                                        placed = placeWorkload(workload, attempt + 1);
                                    } else {
                                        placed = Future.failedFuture(new IllegalStateException(
                                                "No available node with sufficient resources to deploy workload after "
                                                        + PLACEMENT_ATTEMPTS + " placements"));
                                    }
                                    return placed;
                                });
                    }
                    return ret;
                });
    }

    /**
     * Reserves the workload's room on its pre-assigned node, failing unless the node is registered, ONLINE,
     * and has that room, the same gates placement applies when it picks a node.
     */
    private Future<VmNode> reserveOnPinnedNode(Workload workload) {
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
                    } else {
                        ret = reserve(node, workload)
                                .compose(reserved -> reserved
                                        ? Future.succeededFuture(node)
                                        : Future.failedFuture(new IllegalStateException(
                                                "Node " + nodeId + " lacks capacity for workload " + workload.getName())));
                    }
                    return ret;
                });
    }

    private Future<Boolean> reserve(VmNode node, Workload workload) {
        return vmNodeService.reserveSync(node.getId(), WorkloadReservation.forRun(workload));
    }

    /**
     * Persists the terminal status of a run and gives the run's CPU and memory back to the node;
     * the disk stays held until the workload is destroyed.
     */
    private Future<Workload> recordRunEnded(Workload workload, WorkloadStatus status) {
        workload.setStatus(status);
        return persistRedacted(workload)
                .compose(persisted -> vmNodeService.releaseRunSync(persisted.getNodeId(), persisted.getId())
                                                   .map(persisted));
    }

    /**
     * Persists the workload with every secret value replaced by
     * {@value REDACTED_SECRET_VALUE}, then restores the real values on the object — the
     * record never holds a secret value, while dispatch to the node still carries them.
     */
    private Future<Workload> persistRedacted(Workload workload) {
        Future<Workload> ret;
        Map<String, String> secrets = workload.getSecrets();
        if (secrets == null || secrets.isEmpty()) {
            ret = workloadService.saveSync(workload);
        } else {
            Map<String, String> redacted = new LinkedHashMap<>();
            secrets.keySet().forEach(key -> redacted.put(key, REDACTED_SECRET_VALUE));
            workload.setSecrets(redacted);
            ret = workloadService.saveSync(workload)
                    .andThen(result -> workload.setSecrets(secrets));
        }
        return ret;
    }
}
