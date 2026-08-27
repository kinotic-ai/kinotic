package org.kinotic.system.internal.api.github;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import tools.jackson.databind.JsonNode;

/**
 * Wire mirror of {@code org.kinotic.github.api.model.GitHubWebhookEvent}. The orchestrator
 * cannot compile against kinotic-github (the dependency arrow runs the other way), so the
 * event stream proxy deserializes into this mirror — field names are the wire contract.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class GitHubWebhookEvent {

    /** Value of {@code X-GitHub-Event}, e.g. {@code push}, {@code pull_request}. */
    private String eventType;

    /** Value of {@code X-GitHub-Delivery}. */
    private String deliveryId;

    /** GitHub installation id, or {@code null} for events that lack it. */
    private Long installationId;

    /** {@code owner/repo}, or {@code null} for installation-scope events. */
    private String repoFullName;

    /** Full webhook JSON payload. */
    private JsonNode payload;
}
