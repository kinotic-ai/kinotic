package org.kinotic.os.github.api.model;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Pre-parsed webhook delivery the gateway hands to {@code GitHubWebhookEventService}
 * after HMAC verification. {@code installationId} and {@code repoFullName} are pulled
 * out for fast lookup; the full payload is preserved so the dispatcher can surface
 * the relevant fields per event type.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class GitHubWebhookEvent {
    /** Value of {@code X-GitHub-Event}, e.g. {@code push}, {@code pull_request}. */
    private String eventType;

    /** Value of {@code X-GitHub-Delivery}; used for re-delivery dedup. */
    private String deliveryId;

    /** GitHub installation id pulled from the payload, or {@code null} for events that lack it. */
    private String installationId;

    /** {@code owner/repo}, or {@code null} for installation-scope events. */
    private String repoFullName;

    /** Full webhook JSON payload. */
    private JsonObject payload;
}
