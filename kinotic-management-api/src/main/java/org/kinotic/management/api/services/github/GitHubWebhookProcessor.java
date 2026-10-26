package org.kinotic.management.api.services.github;

import io.vertx.core.Future;
import org.kinotic.management.api.model.deployment.ProjectPushEvent;
import org.kinotic.management.api.model.github.GitHubWebhookEvent;

/**
 * Internal-only service the gateway's webhook handler calls after HMAC verification.
 * Resolves the delivery to a Kinotic Project, mutates installation state for management
 * events, and publishes a {@link ProjectPushEvent} per backing project to the cluster-wide
 * event fabric for a push to the repository's default branch.
 * <p>
 * Not {@code @Publish}ed — {@link #process(GitHubWebhookEvent)} may only ever be called by the
 * gateway's webhook handler, in-process.
 */
public interface GitHubWebhookProcessor {

    /**
     * The management server's route a repository host delivers its webhooks to: the system's GitHub App, or each
     * repository's push webhook when Gitea hosts the repositories.
     */
    String WEBHOOK_PATH = "/api/github/webhook";

    /**
     * Processes one verified GitHub delivery. Always completes successfully (never
     * failed) so the gateway can return 204 quickly even when downstream resolution
     * can't find a backing project. There is no platform-side delivery dedup —
     * GitHub may redeliver, and consumers must be idempotent.
     */
    Future<Void> process(GitHubWebhookEvent event);
}
