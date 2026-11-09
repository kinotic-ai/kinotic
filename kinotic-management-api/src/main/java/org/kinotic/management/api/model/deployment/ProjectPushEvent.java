package org.kinotic.management.api.model.deployment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A push to the default branch of the repository backing a project, published to every node consuming
 * it: the commit the project should now be deployed at.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public final class ProjectPushEvent {

    private String organizationId;

    private String projectId;

    private String commitSha;
}
