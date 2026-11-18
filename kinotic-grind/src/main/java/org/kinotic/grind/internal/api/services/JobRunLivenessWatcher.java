package org.kinotic.grind.internal.api.services;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.Date;
import java.util.Set;

/**
 * Ends the runs a server node leaves behind: on every change to the cluster membership, each run
 * still executing on a node that is not a member is recorded {@link ExecutionStatus#FAILED} with the
 * condition {@link StatusConditionType#SERVER_NODE_LEFT}, since no node will report its end. The
 * runs are searched for a second time a few seconds later, for a run recorded as its node left, and
 * a search or a write that fails is made again until it succeeds. Every node watches; a run already
 * ended is left as it is, so the first node to see the departure ends the run and the others write
 * nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobRunLivenessWatcher {

    private static final int PAGE_SIZE = 200;

    // How long after the runs were ended they are searched for again: a run recorded within the
    // index's refresh interval of its node leaving is in no search made at once
    private static final long RESCAN_MS = 5_000;

    // How long after a search or a write failed the runs are searched for again, and the longest two attempts are apart
    private static final long RETRY_MS = 1_000;
    private static final long RETRY_MAX_MS = 300_000;

    private final EventBusService eventBusService;
    private final JobRunRepository jobRunRepository;
    private final Vertx vertx;
    private Disposable membership;

    // The one timer for the next search, guarded by this with the fields below: the searches made
    // for two changes to the membership complete on different threads
    private Long timerId;
    // When the second search is due, RESCAN_MS after the latest change to the membership, or 0 once
    // a search that started at or after that time has succeeded
    private long rescanDueAt;
    private boolean stopped;

    // The membership last reported. Ending a departed node's runs takes a write per run, so each page
    // and each run is judged against this rather than the membership the ending started with: a node
    // that joined meanwhile keeps its runs
    private volatile Set<String> members;

    @PostConstruct
    void start() {
        membership = eventBusService.monitorClusterNodes()
                                    .subscribe(this::membershipChanged,
                                               throwable -> log.error("Cluster membership monitoring failed, runs left by a departed node can no longer be ended", throwable));
    }

    @PreDestroy
    synchronized void stop() {
        membership.dispose();
        stopped = true;
        cancelTimer();
    }

    private void membershipChanged(Set<String> current) {
        members = current;
        synchronized (this) {
            rescanDueAt = System.currentTimeMillis() + RESCAN_MS;
        }
        sweep(RETRY_MS);
    }

    private void sweep(long retryMs) {
        long started = System.currentTimeMillis();
        endRunsElsewhere().onComplete(swept -> swept(swept, started, retryMs));
    }

    // Sets the timer for the search that follows this one: the same search again when it failed, the
    // second search while it is due. Either stands in for the search of a timer already set.
    private synchronized void swept(AsyncResult<Void> swept, long started, long retryMs) {
        if (!stopped) {
            if (swept.failed()) {
                log.error("Runs left by departed nodes could not be ended, next attempt in {}ms", retryMs, swept.cause());
                setTimer(retryMs, () -> sweep(Math.min(RETRY_MAX_MS, retryMs * 2)));
            } else if (rescanDueAt != 0) {
                // judged by when this search started, so a search a timer set for an earlier change
                // starts is not taken for the second search of a later one
                if (started >= rescanDueAt) {
                    rescanDueAt = 0;
                } else {
                    setTimer(Math.max(1, rescanDueAt - System.currentTimeMillis()), () -> sweep(RETRY_MS));
                }
            }
        }
    }

    // Called holding the lock
    private void setTimer(long delayMs, Runnable search) {
        cancelTimer();
        timerId = vertx.setTimer(delayMs, id -> search.run());
    }

    // Called holding the lock
    private void cancelTimer() {
        if (timerId != null) {
            vertx.cancelTimer(timerId);
            timerId = null;
        }
    }

    // An ended run leaves the search, so the first page is read until it holds no run to end. A page
    // that ended none holds only runs that ended since the search, and is the last one read.
    private Future<Void> endRunsElsewhere() {
        return jobRunRepository.findRunningElsewhere(members, Pageable.create(0, PAGE_SIZE, null))
                               .compose(page -> {
                                   Future<Boolean> anyEnded = Future.succeededFuture(false);
                                   for (JobRun run : page.getContent()) {
                                       anyEnded = anyEnded.compose(before -> end(run).map(ended -> before || ended));
                                   }
                                   return anyEnded;
                               })
                               .compose(anyEnded -> anyEnded ? endRunsElsewhere() : Future.succeededFuture());
    }

    private Future<Boolean> end(JobRun run) {
        Future<Boolean> ret;
        if (members.contains(run.getNodeId())) {
            // its node joined after the page was searched
            ret = Future.succeededFuture(false);
        } else {
            StatusCondition left = new StatusCondition(StatusConditionType.SERVER_NODE_LEFT,
                                                       "Node " + run.getNodeId() + " left the cluster while the run was live",
                                                       new Date());
            ret = jobRunRepository.recordAbandoned(run.getId(), left, "cluster membership")
                                  .onSuccess(ended -> {
                                      if (ended) {
                                          log.warn("Ended run {} ({}) as failed: node {} left the cluster while it was live", run.getId(), run.getName(), run.getNodeId());
                                      }
                                  });
        }
        return ret;
    }
}
