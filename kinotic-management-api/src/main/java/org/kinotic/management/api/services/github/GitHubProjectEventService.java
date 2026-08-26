package org.kinotic.management.api.services.github;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.management.api.model.GitHubProjectEvent;
import reactor.core.publisher.Flux;

/**
 * Live GitHub activity for the projects the authenticated participant may see: an organization
 * participant sees its own organization's projects, a system participant sees any.
 */
@Publish
public interface GitHubProjectEventService {

    /**
     * Opens a live stream of GitHub deliveries for repositories backing Kinotic Projects. The stream
     * stays open until the caller unsubscribes.
     * <p>
     * Delivery is best-effort: a subscriber that can't keep up loses events rather than slowing the
     * webhook handler, and only deliveries arriving while subscribed are seen. GitHub may redeliver,
     * so subscribers must be idempotent.
     *
     * @return a {@link Flux} emitting one {@link GitHubProjectEvent} per backing project
     */
    Flux<GitHubProjectEvent> events();
}
