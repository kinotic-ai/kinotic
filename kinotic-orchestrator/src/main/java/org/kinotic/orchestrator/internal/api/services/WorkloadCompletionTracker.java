package org.kinotic.orchestrator.internal.api.services;

import org.kinotic.orchestrator.api.model.workload.Workload;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of callers waiting for a workload's run to end. A waiter is registered
 * with {@link #awaitCompletion} and settled when the orchestrator observes the run's end —
 * a terminal status persisted from a node report, an explicit stop, a node going offline —
 * or the workload being destroyed.
 */
@Component
public class WorkloadCompletionTracker {

    private final ConcurrentHashMap<String, CompletableFuture<Workload>> waiters = new ConcurrentHashMap<>();

    /**
     * Returns a future that completes with the workload's final state once its run has ended,
     * or fails if the workload is destroyed first. Concurrent waits on the same workload share
     * one future.
     *
     * @param workloadId the id of the workload to wait for
     * @return a future settled by the next {@link #workloadChanged} or {@link #workloadDestroyed}
     *         that ends the run
     */
    public CompletableFuture<Workload> awaitCompletion(String workloadId) {
        return waiters.computeIfAbsent(workloadId, id -> new CompletableFuture<>());
    }

    /**
     * Settles waiters when the given persisted state says the workload's run has ended;
     * no-op otherwise.
     *
     * @param workload the workload as it was just persisted
     */
    public void workloadChanged(Workload workload) {
        if (workload.getStatus().isComplete()) {
            CompletableFuture<Workload> waiter = waiters.remove(workload.getId());
            if (waiter != null) {
                waiter.complete(workload);
            }
        }
    }

    /**
     * Fails waiters for a workload destroyed before its run ended.
     *
     * @param workloadId the id of the destroyed workload
     */
    public void workloadDestroyed(String workloadId) {
        CompletableFuture<Workload> waiter = waiters.remove(workloadId);
        if (waiter != null) {
            waiter.completeExceptionally(
                    new IllegalStateException("Workload " + workloadId + " was destroyed before its run ended"));
        }
    }
}
