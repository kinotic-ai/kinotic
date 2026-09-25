package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.exceptions.RpcServiceUnavailableException;
import org.kinotic.core.api.exceptions.RpcMissingServiceException;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.apache.ignite.Ignite;
import org.kinotic.management.api.model.TelemetryTenant;
import org.kinotic.management.api.services.LokiClient;
import org.kinotic.system.api.services.VmNodeService;
import org.kinotic.system.api.services.WorkloadService;
import org.kinotic.system.api.services.VmNodeOrchestrationService;
import org.kinotic.system.api.workload.VmManagerProxy;
import org.kinotic.system.api.services.WorkloadOrchestrationService;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.model.workload.WorkloadReservation;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
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

    // Workload ids one Loki delete request names: the selector travels in the URL
    private static final int LOG_DELETE_BATCH = 50;

    private final VmNodeOrchestrationService nodeOrchestrationService;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private final VmManagerProxy vmManagerProxy;
    private final VmNodeService vmNodeService;
    private final WorkloadService workloadService;
    private final LokiClient lokiClient;
    private final Ignite ignite;

    // Every node requests the deployment; Ignite elects a single host for it cluster-wide
    @EventListener(ApplicationReadyEvent.class)
    public void deployRetentionSweep() {
        ignite.services().deployClusterSingleton(WorkloadCleanupService.SINGLETON_NAME, new WorkloadCleanupService());
    }

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
                                            Future<Void> recorded;
                                            if (error instanceof RpcServiceUnavailableException) {
                                                // The node may have started it: the record keeps STARTING and
                                                // its room, and the node's next report settles it
                                                log.warn("Start of workload {} on node {} went unanswered",
                                                         savedWorkload.getId(), node.getId(), error);
                                                recorded = markUnreachable(savedWorkload,
                                                                           "Node " + node.getId() + " did not answer the start");
                                            } else {
                                                log.error("Failed to start workload {} on node {}",
                                                          savedWorkload.getId(), node.getId(), error);
                                                recorded = recordRunEnded(savedWorkload, WorkloadStatus.FAILED, "start failed").mapEmpty();
                                            }
                                            return recorded.transform(_ -> Future.failedFuture(error));
                                        })
                            );
                });
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
                    return workloadService.updateRunSync(workloadId, WorkloadStatus.STOPPING, null, "stopWorkload").map(workload);
                })
                .compose(workload ->
                    verifyingNodeOnFailure(workload.getNodeId(), vmManagerProxy.stopWorkload(workload.getNodeId(), workloadId))
                            .compose(v -> recordRunEnded(workload, WorkloadStatus.STOPPED, "stopWorkload"))
                            .recover(error -> {
                                Future<Void> recorded;
                                if (error instanceof RpcServiceUnavailableException) {
                                    // The node may have stopped it: the record keeps STOPPING and the
                                    // node's next report settles it
                                    recorded = markUnreachable(workload,
                                                               "Node " + workload.getNodeId() + " did not answer the stop");
                                } else {
                                    recorded = Future.succeededFuture();
                                }
                                return recorded.transform(_ -> Future.failedFuture(error));
                            })
                )
                .mapEmpty();
    }

    @Override
    public Future<Void> destroyWorkload(String workloadId) {
        Validate.notNull(workloadId, "Workload id cannot be null");

        return workloadService.findById(workloadId)
                .compose(workload -> {
                    Future<Void> ret;
                    if (workload == null) {
                        ret = Future.failedFuture(new IllegalArgumentException("Workload not found: " + workloadId));
                    } else if (workload.getNodeId() == null) {
                        // never placed: there is nothing on any node
                        ret = Future.succeededFuture();
                    } else {
                        String nodeId = workload.getNodeId();
                        ret = verifyingNodeOnFailure(nodeId, vmManagerProxy.destroyWorkload(nodeId, workloadId))
                                // the destroy ended a run the node will not report the end of; an
                                // ended run holds no room, so the release is for one never reported
                                .compose(v -> workload.getStatus().isOpen()
                                        ? recordRunEnded(workload, WorkloadStatus.STOPPED, "destroyWorkload").mapEmpty()
                                        : vmNodeService.releaseSync(nodeId, workloadId));
                    }
                    return ret;
                });
    }

    @Override
    public Future<Void> deleteWorkload(String workloadId) {
        Validate.notNull(workloadId, "Workload id cannot be null");
        return deleteWorkloads(List.of(workloadId));
    }

    @Override
    public Future<Void> deleteWorkloads(List<String> workloadIds) {
        Validate.notEmpty(workloadIds, "Workload ids cannot be empty");

        return Future.all(workloadIds.stream().map(workloadService::findById).toList())
                .compose(found -> {
                    List<Workload> workloads = found.list();
                    Future<Void> ret = Future.succeededFuture();
                    for (int i = 0; i < workloads.size() && ret.succeeded(); i++) {
                        Workload workload = workloads.get(i);
                        if (workload == null) {
                            ret = Future.failedFuture(new IllegalArgumentException("Workload not found: " + workloadIds.get(i)));
                        } else if (workload.getStatus().isOpen()) {
                            ret = Future.failedFuture(new IllegalStateException("Workload " + workload.getId() + " is " + workload.getStatus()
                                    + "; stop or destroy it before deleting it"));
                        }
                    }
                    if (ret.succeeded()) {
                        // the logs go first: a record without logs is an ended run like any other, while
                        // logs without a record could never be found again
                        ret = deleteLogs(workloads)
                                .compose(v -> Future.all(workloads.stream().map(workload -> workloadService.deleteById(workload.getId())).toList()))
                                // one refresh for the batch, so a listing made right after no longer shows any of them
                                .compose(v -> workloadService.syncIndex());
                    }
                    return ret;
                });
    }

    // One delete request per organization, in selectors of bounded length: Loki filters every pending
    // request against every query in the tenant until its compactor has applied it
    private Future<Void> deleteLogs(List<Workload> workloads) {
        Map<String, List<String>> idsByTenant = new LinkedHashMap<>();
        for (Workload workload : workloads) {
            idsByTenant.computeIfAbsent(TelemetryTenant.of(workload.getOrganizationId()), tenant -> new ArrayList<>()).add(workload.getId());
        }
        long now = System.currentTimeMillis();
        Future<Void> ret = Future.succeededFuture();
        for (Map.Entry<String, List<String>> tenant : idsByTenant.entrySet()) {
            List<String> ids = tenant.getValue();
            for (int from = 0; from < ids.size(); from += LOG_DELETE_BATCH) {
                List<String> batch = ids.subList(from, Math.min(ids.size(), from + LOG_DELETE_BATCH));
                ret = ret.compose(v -> lokiClient.delete(tenant.getKey(), TelemetryTenant.workloadLogSelector(batch), 0, now));
            }
        }
        return ret;
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
                    } else if (startedWorkload.getStatus().isComplete()) {
                        // A terminal reply — a non-detached run that already ended — is the
                        // node's final word
                        ret = recordRunEnded(startedWorkload, startedWorkload.getStatus(), "node " + startedWorkload.getNodeId());
                    } else if (current.getStatus() == WorkloadStatus.STARTING) {
                        // A RUNNING reply only promotes from STARTING: a short-lived detached
                        // workload's terminal status report can be applied before the reply
                        // gets here, and must not be clobbered.
                        ret = workloadService.updateRunSync(startedWorkload.getId(), startedWorkload.getStatus(), startedWorkload.getExitCode(),
                                                            "node " + startedWorkload.getNodeId())
                                             .map(startedWorkload);
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
     * Reserves the workload's room on its pre-assigned node, failing unless the node is registered, in its
     * desired state, and has that room, the same gates placement applies when it picks a node.
     */
    private Future<VmNode> reserveOnPinnedNode(Workload workload) {
        String nodeId = workload.getNodeId();
        return vmNodeService.findById(nodeId)
                .compose(node -> {
                    Future<VmNode> ret;
                    if (node == null) {
                        ret = Future.failedFuture(
                                new IllegalArgumentException("Node not registered: " + nodeId));
                    } else if (!node.getState().isReconciled()) {
                        ret = Future.failedFuture(new IllegalStateException(
                                "Node " + nodeId + " is not taking workloads (reported "
                                        + (node.getState().getObserved() == null ? null : node.getState().getObserved().phase())
                                        + ", conditions " + node.getState().getConditions() + ")"));
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
     * Records that the node's answer for the workload is unknown: its status and its room stay as they
     * are, marked NODE_UNREACHABLE until the node's next report.
     */
    private Future<Void> markUnreachable(Workload workload, String message) {
        return workloadService.setCondition(workload.getId(),
                                            new StatusCondition(StatusConditionType.NODE_UNREACHABLE, message, new Date()),
                                            "unanswered call")
                              .mapEmpty();
    }

    /**
     * Records the terminal status of a run and gives its room back to the node. The record stays
     * as the run's outcome; the node removes the VM on its own once the run has ended.
     */
    private Future<Workload> recordRunEnded(Workload workload, WorkloadStatus status, String source) {
        workload.setStatus(status);
        return workloadService.updateRunSync(workload.getId(), status, workload.getExitCode(), source)
                .compose(v -> vmNodeService.releaseSync(workload.getNodeId(), workload.getId()))
                .map(workload);
    }

    /**
     * Creates the workload's record with every secret value replaced by
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
