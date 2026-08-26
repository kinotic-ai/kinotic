package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.kinotic.domain.api.model.workload.Workload;
import org.kinotic.domain.api.model.workload.WorkloadStatus;
import org.kinotic.system.api.workload.VmManagerProxy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stand-in for the vm-manager on a node, honoring the detached contract: a detached start or
 * restart succeeds immediately with the node's RUNNING view of the workload, while a
 * non-detached one stays pending until the test settles {@link #pendingReply} — the way the
 * real node holds the reply open until the run ends. Replies are detached copies the way a
 * wire round-trip produces them.
 */
public class StubVmManagerProxy implements VmManagerProxy {

    /** The node's view of each started workload, keyed by workload id. */
    public final Map<String, Workload> started = new LinkedHashMap<>();

    /** The node's view of the most recently started or restarted workload. */
    public Workload lastStarted;

    /** The held-open reply of the most recent non-detached start or restart. */
    public Promise<Workload> pendingReply;

    /** Runs after the node has "started" the workload, before the start reply returns. */
    public Runnable onStart;

    /** When set, every start fails with this error instead of starting. */
    public Exception failStartWith;

    @Override
    public Future<Workload> startWorkload(String nodeId, Workload workload) {
        if (failStartWith != null) {
            return Future.failedFuture(failStartWith);
        }
        Workload startedWorkload = copy(workload);
        startedWorkload.setStatus(WorkloadStatus.RUNNING);
        started.put(startedWorkload.getId(), startedWorkload);
        lastStarted = startedWorkload;
        if (onStart != null) {
            onStart.run();
        }
        return reply(startedWorkload);
    }

    @Override
    public Future<Workload> restartWorkload(String nodeId, String workloadId) {
        Workload restarted = copy(started.get(workloadId));
        restarted.setStatus(WorkloadStatus.RUNNING);
        restarted.setExitCode(null);
        lastStarted = restarted;
        return reply(restarted);
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

    /**
     * Settles {@link #pendingReply} with the node's final view of the run that just ended.
     */
    public void completeRun(WorkloadStatus status, Integer exitCode) {
        Workload finished = copy(lastStarted);
        finished.setStatus(status);
        finished.setExitCode(exitCode);
        pendingReply.complete(finished);
    }

    private Future<Workload> reply(Workload running) {
        Future<Workload> ret;
        if (running.isDetached()) {
            ret = Future.succeededFuture(running);
        } else {
            pendingReply = Promise.promise();
            ret = pendingReply.future();
        }
        return ret;
    }

    private Workload copy(Workload workload) {
        return new Workload(workload.getName(), workload.getImage())
                .setId(workload.getId())
                .setNodeId(workload.getNodeId())
                .setStatus(workload.getStatus())
                .setExitCode(workload.getExitCode())
                .setDetached(workload.isDetached())
                .setVcpus(workload.getVcpus())
                .setMemoryMb(workload.getMemoryMb())
                .setDiskSizeMb(workload.getDiskSizeMb())
                .setEnvironment(new LinkedHashMap<>(workload.getEnvironment()))
                .setSecrets(new LinkedHashMap<>(workload.getSecrets()))
                .setEntrypoint(new ArrayList<>(workload.getEntrypoint()))
                .setCmd(new ArrayList<>(workload.getCmd()))
                .setVolumeMounts(new ArrayList<>(workload.getVolumeMounts()))
                .setCreated(workload.getCreated())
                .setUpdated(workload.getUpdated());
    }
}
