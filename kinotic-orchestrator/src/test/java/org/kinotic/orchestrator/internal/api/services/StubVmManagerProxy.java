package org.kinotic.orchestrator.internal.api.services;

import io.vertx.core.Future;
import org.kinotic.orchestrator.api.model.workload.Workload;
import org.kinotic.orchestrator.api.model.workload.WorkloadStatus;
import org.kinotic.orchestrator.api.workload.VmManagerProxy;

import java.util.List;

/**
 * Stand-in for the vm-manager on a node. {@link #startWorkload} succeeds immediately with the
 * node's RUNNING view of the workload, returned as a detached copy the way a wire round-trip
 * produces one.
 */
public class StubVmManagerProxy implements VmManagerProxy {

    /** The node's view of the most recently started workload. */
    public Workload lastStarted;

    /** Runs after the node has "started" the workload, before the start reply returns. */
    public Runnable onStart;

    @Override
    public Future<Workload> startWorkload(String nodeId, Workload workload) {
        Workload started = copy(workload);
        started.setStatus(WorkloadStatus.RUNNING);
        lastStarted = started;
        if (onStart != null) {
            onStart.run();
        }
        return Future.succeededFuture(started);
    }

    @Override
    public Future<Workload> restartWorkload(String nodeId, String workloadId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Void> stopWorkload(String nodeId, String workloadId) {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> destroyWorkload(String nodeId, String workloadId) {
        return Future.succeededFuture();
    }

    @Override
    public Future<Workload> getWorkload(String nodeId, String workloadId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<List<Workload>> listWorkloads(String nodeId) {
        throw new UnsupportedOperationException();
    }

    private Workload copy(Workload workload) {
        return new Workload(workload.getName(), workload.getImage())
                .setId(workload.getId())
                .setNodeId(workload.getNodeId())
                .setStatus(workload.getStatus())
                .setExitCode(workload.getExitCode())
                .setVcpus(workload.getVcpus())
                .setMemoryMb(workload.getMemoryMb())
                .setDiskSizeMb(workload.getDiskSizeMb())
                .setCreated(workload.getCreated())
                .setUpdated(workload.getUpdated());
    }
}
