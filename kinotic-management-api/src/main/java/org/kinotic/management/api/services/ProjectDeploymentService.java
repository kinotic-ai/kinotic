package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.idl.api.annotations.McpTool;
import org.kinotic.management.api.model.ProjectDeployment;

/**
 * Read access to {@link ProjectDeployment} records for the current participant's
 * organization, so the portal and tools can follow where a project is deployed and how
 * its latest deployment went.
 */
@Publish
@McpTool
public interface ProjectDeploymentService {

    /**
     * Finds the deployment record of the given project in the current participant's
     * organization.
     *
     * @param projectId id of the project the deployment belongs to
     * @return a {@link Future} emitting the deployment record, or {@code null} when the
     *         project has never been deployed
     */
    @McpTool(title = "Find Project Deployment", readOnlyHint = true)
    Future<ProjectDeployment> findByProjectId(String projectId);

}
