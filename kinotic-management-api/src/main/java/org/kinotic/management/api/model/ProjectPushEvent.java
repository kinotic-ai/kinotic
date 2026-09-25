package org.kinotic.management.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A push to the default branch of the repository backing a project, delivered to every node through
 * the event fabric: the commit the project should now be deployed at.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ProjectPushEvent {

    private String organizationId;

    private String projectId;

    private String commitSha;
}
