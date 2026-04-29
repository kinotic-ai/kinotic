package org.kinotic.os.github.api.services;

import org.kinotic.os.github.api.model.GitHubWebhookEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Internal-only service the gateway's webhook handler calls after HMAC verification.
 * Resolves the delivery to a Kinotic Project, mutates installation state for management
 * events, and republishes domain events on the Kinotic event bus at
 * {@code evt://github/<eventType>/<orgId>/<projectId>}.
 * <p>
 * Not {@code @Publish}ed — only the gateway's webhook handler invokes this, in-process.
 */
public interface GitHubWebhookEventService {

    /**
     * Processes one verified GitHub delivery. Idempotent: a duplicate {@code deliveryId}
     * is swallowed silently. Always completes (never failed) so the gateway can return
     * 204 quickly even if downstream resolution can't find a link.
     */
    CompletableFuture<Void> process(GitHubWebhookEvent event);
}
