package org.kinotic.orchestrator.internal.api.github;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Wire mirror of {@code org.kinotic.github.api.model.GitHubProjectEvent}: one GitHub
 * delivery resolved to a Kinotic Project backed by the repository it concerns.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class GitHubProjectEvent {

    private String organizationId;

    private String projectId;

    private GitHubWebhookEvent webhookEvent;
}
