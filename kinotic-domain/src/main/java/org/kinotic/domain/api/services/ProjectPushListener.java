package org.kinotic.domain.api.services;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.Project;

/**
 * Callback contract for reacting to commits pushed to the default branch of a project's
 * backing GitHub repository. Implementations are collected by the GitHub webhook pipeline
 * and invoked once per affected project per push delivery.
 * <p>
 * GitHub delivers webhooks at-most-once and the pipeline performs no dedup, so
 * implementations must tolerate both missed and duplicate notifications.
 */
public interface ProjectPushListener {

    /**
     * Called when commits land on the default branch of the project's backing repository.
     * The returned future is awaited by the webhook pipeline but a failure is logged and
     * dropped; it does not affect webhook processing or other listeners.
     *
     * @param project   the project backed by the repository that received the push
     * @param commitSha the sha of the new head commit of the default branch
     * @return a future that completes when the listener has finished reacting to the push
     */
    Future<Void> onPush(Project project, String commitSha);

}
