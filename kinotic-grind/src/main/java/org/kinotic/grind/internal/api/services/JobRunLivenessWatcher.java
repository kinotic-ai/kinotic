package org.kinotic.grind.internal.api.services;

import io.vertx.core.Future;
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
 * condition {@link StatusConditionType#SERVER_NODE_LEFT}, since no node will report its end. Every
 * node watches; a run already ended is left as it is, so the first node to see the departure ends
 * the run and the others write nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobRunLivenessWatcher {

    private static final int PAGE_SIZE = 200;

    private final EventBusService eventBusService;
    private final JobRunRepository jobRunRepository;
    private Disposable membership;

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
    void stop() {
        membership.dispose();
    }

    private void membershipChanged(Set<String> current) {
        members = current;
        endRunsElsewhere()
                .onFailure(error -> log.error("Runs left by departed nodes could not be ended", error));
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
