package org.kinotic.github.api.services;

import org.kinotic.github.api.model.GitHubProjectEvent;
import org.kinotic.github.api.model.GitHubWebhookEvent;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;

/**
 * Internal-only service the gateway's webhook handler calls after HMAC verification.
 * Resolves the delivery to a Kinotic Project, mutates installation state for management
 * events, and republishes repository events to subscribers of {@link #events()}.
 * <p>
 * Not {@code @Publish}ed — only the gateway's webhook handler invokes this, in-process.
 */
public interface GitHubWebhookEventService {

    /**
     * Processes one verified GitHub delivery. Always completes successfully (never
     * failed) so the gateway can return 204 quickly even when downstream resolution
     * can't find a backing project. There is no platform-side delivery dedup —
     * GitHub may redeliver, and subscribers must be idempotent.
     */
    CompletableFuture<Void> process(GitHubWebhookEvent event);

    /**
     * A hot stream of repository deliveries that resolved to a Kinotic Project, shared by every
     * subscriber and carrying every organization's events — filter by
     * {@link GitHubProjectEvent#getOrganizationId()} before exposing anything derived from it.
     * <p>
     * The stream never completes, and delivery is best-effort: a subscriber that can't keep up
     * loses events rather than applying backpressure to the webhook handler. Only deliveries
     * arriving while subscribed are seen, and only on the node that received them.
     *
     * @return a {@link Flux} emitting one {@link GitHubProjectEvent} per backing project
     */
    Flux<GitHubProjectEvent> events();
}
