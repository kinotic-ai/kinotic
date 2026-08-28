package org.kinotic.github.internal.api.services;

import io.vertx.core.Future;
import org.kinotic.github.api.model.GitHubProjectEvent;
import org.kinotic.github.api.model.GitHubWebhookEvent;
import org.kinotic.github.api.services.GitHubProjectEventService;

/**
 * Internal-only service the gateway's webhook handler calls after HMAC verification.
 * Resolves the delivery to a Kinotic Project, mutates installation state for management
 * events, and emits one {@link GitHubProjectEvent} per backing project onto the event fabric.
 * <p>
 * Not {@code @Publish}ed — {@link #process(GitHubWebhookEvent)} may only ever be called by the
 * gateway's webhook handler, in-process. {@link GitHubProjectEventService} publishes the event
 * stream to remote subscribers, scoped to the organization they belong to.
 */
public interface GitHubWebhookEventService {

    /**
     * Processes one verified GitHub delivery. Always completes successfully (never
     * failed) so the gateway can return 204 quickly even when downstream resolution
     * can't find a backing project. There is no platform-side delivery dedup —
     * GitHub may redeliver, and consumers must be idempotent.
     */
    Future<Void> process(GitHubWebhookEvent event);
}
