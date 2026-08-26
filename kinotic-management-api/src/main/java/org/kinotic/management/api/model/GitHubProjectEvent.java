package org.kinotic.management.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A GitHub repository delivery paired with the Kinotic Project whose repository it came from.
 * One is emitted per backing project, so a repository connected to more than one project yields
 * one event per project.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class GitHubProjectEvent {

    /** Organization owning {@link #projectId}. */
    private String organizationId;

    /** Id of the Project backed by the repository the delivery came from. */
    private String projectId;

    /** The verified delivery, exactly as GitHub sent it. */
    private GitHubWebhookEvent webhookEvent;
}
