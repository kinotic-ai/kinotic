package org.kinotic.management.internal.api.services;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.annotations.Emitter;
import org.kinotic.management.api.model.deployment.ProjectPushEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Publishes to the event fabric that a commit landed on the default branch of a project's repository, which deploys
 * the project at that commit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectPushPublisher {

    private final Vertx vertx;

    // Hot source of the @Emitter stream; never terminates. Best-effort delivery keeps a slow fabric uplink from
    // stalling the caller, matching the fabric's at-most-once semantics.
    private final Sinks.Many<ProjectPushEvent> sink = Sinks.many().multicast().directBestEffort();

    // One shared context every emission is delivered on: it serializes concurrent publications (Sinks reject
    // concurrent emission) and keeps subscriber chains off the callers' threads.
    private Context deliveryContext;

    @PostConstruct
    void init() {
        deliveryContext = vertx.getOrCreateContext();
    }

    @Emitter
    Flux<ProjectPushEvent> projectPushes() {
        return sink.asFlux();
    }

    /**
     * Publishes the push, delivered asynchronously and at most once.
     *
     * @param event the project and the commit that landed on its repository's default branch
     */
    public void publish(ProjectPushEvent event) {
        deliveryContext.runOnContext(v -> {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                log.warn("Failed to emit the push of {} for project {}: {}", event.getCommitSha(), event.getProjectId(), result);
            }
        });
    }
}
