package org.kinotic.grind.internal.api.services;

import io.vertx.core.Future;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.reconcile.StatusCondition;
import org.kinotic.core.api.reconcile.StatusConditionType;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.Date;
import java.util.Set;

/**
 * Marks the runs a server node leaves behind: on every change to the cluster membership, each run
 * still executing on a node that is not a member is marked {@link StatusConditionType#SERVER_NODE_LEFT},
 * since no node will report its end. Every node watches and marks; a mark already in place is left as
 * it is, so the first node to see the departure writes it and the others write nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobRunLivenessWatcher {

    private static final int PAGE_SIZE = 200;

    private final EventBusService eventBusService;
    private final JobRunRepository jobRunRepository;
    private Disposable membership;

    @PostConstruct
    void start() {
        membership = eventBusService.monitorClusterNodes()
                                    .subscribe(this::membershipChanged,
                                               throwable -> log.error("Cluster membership monitoring failed, runs left by a departed node can no longer be marked", throwable));
    }

    @PreDestroy
    void stop() {
        membership.dispose();
    }

    private void membershipChanged(Set<String> members) {
        jobRunRepository.findRunningElsewhere(members, Pageable.create(0, PAGE_SIZE, null))
                        .compose(page -> {
                            Future<Void> chain = Future.succeededFuture();
                            for (JobRun run : page.getContent()) {
                                chain = chain.compose(v -> mark(run));
                            }
                            return chain;
                        })
                        .onFailure(error -> log.error("Runs left by departed nodes could not be marked", error));
    }

    private Future<Void> mark(JobRun run) {
        StatusCondition left = new StatusCondition(StatusConditionType.SERVER_NODE_LEFT,
                                                   "Node " + run.getNodeId() + " left the cluster while the run was live",
                                                   new Date());
        return jobRunRepository.setCondition(run.getId(), left, "cluster membership")
                               .onSuccess(set -> {
                                   if (set) {
                                       log.warn("Marked run {} ({}): node {} left the cluster while it was live", run.getId(), run.getName(), run.getNodeId());
                                   }
                               })
                               .mapEmpty();
    }
}
