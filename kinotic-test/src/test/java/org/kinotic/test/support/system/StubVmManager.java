package org.kinotic.test.support.system;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

/**
 * Stand-in for the vm-manager of one node, honoring the detached contract: a detached start replies
 * at once with the node's RUNNING view of the workload, while a non-detached one holds its reply
 * until the test ends the run with {@link #completeRun}, the way the real node holds the reply open
 * until the run ends. Every call arrives through the platform's RPC, so what the node sees is what
 * went over the wire.
 */
public class StubVmManager implements VmManager {

    /** The node's view of each started workload, keyed by workload id. */
    public final Map<String, Workload> started = Collections.synchronizedMap(new LinkedHashMap<>());

    /** The node's view of the most recently started workload. */
    public volatile Workload lastStarted;

    /** The ids of the workloads the node was told to destroy, in order. */
    public final List<String> destroyed = new CopyOnWriteArrayList<>();

    /** The held-open reply of the most recent non-detached start. */
    public volatile Promise<Workload> pendingReply;

    /** Runs after the node has "started" the workload and completes before the start reply returns. */
    public volatile Function<Workload, Future<Void>> onStart;

    /** When set, every start fails with this error instead of starting. */
    public volatile Exception failStartWith;

    /** When set, every stop fails with it. */
    public volatile Exception failStopWith;

    /** Counts down once the first start or stop has reached the node. */
    public final CountDownLatch reached = new CountDownLatch(1);

    @Override
    public Future<Workload> startWorkload(Workload workload) {
        reached.countDown();
        Future<Workload> ret;
        if (failStartWith != null) {
            ret = Future.failedFuture(failStartWith);
        } else {
            workload.setStatus(WorkloadStatus.RUNNING);
            started.put(workload.getId(), workload);
            lastStarted = workload;
            Future<Void> before = onStart == null ? Future.succeededFuture() : onStart.apply(workload);
            ret = before.compose(v -> reply(workload));
        }
        return ret;
    }

    @Override
    public Future<Void> stopWorkload(String workloadId) {
        reached.countDown();
        Future<Void> ret;
        if (failStopWith != null) {
            ret = Future.failedFuture(failStopWith);
        } else {
            ret = Future.succeededFuture();
        }
        return ret;
    }

    @Override
    public Future<Void> destroyWorkload(String workloadId) {
        destroyed.add(workloadId);
        return Future.succeededFuture();
    }

    @Override
    public Future<Workload> getWorkload(String workloadId) {
        return Future.failedFuture(new UnsupportedOperationException());
    }

    @Override
    public Future<List<Workload>> listWorkloads() {
        return Future.failedFuture(new UnsupportedOperationException());
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

    private static Workload copy(Workload workload) {
        return new Workload(workload.getName(), workload.getImage())
                .setId(workload.getId())
                .setNodeId(workload.getNodeId())
                .setStatus(workload.getStatus())
                .setExitCode(workload.getExitCode())
                .setDetached(workload.isDetached())
                .setCpus(workload.getCpus())
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
