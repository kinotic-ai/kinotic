package org.kinotic.management.api.services.github;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.annotations.McpTool;
import org.kinotic.management.api.model.ProjectRepoToken;
import org.kinotic.management.api.services.ProjectService;

/**
 * Short-lived credentials for writing the backing repositories of the caller's organization's projects, for a user of
 * that organization, or a service acting for one, that commits code into a project.
 */
@Publish
@AuthzResource(value = ProjectService.RESOURCE_TYPE, parent = AuthzConstants.APPLICATION_TYPE)
@McpTool
public interface ProjectRepoCredentialService {

    /**
     * Issues a token authorizing writes to the contents of the project's backing repository: commits, branches and
     * tags. A push to the repository's default branch deploys the project. The token is good for an hour and comes
     * with the repository's clone URL and default branch; Git presents it over HTTP as the password of the user
     * {@code x-access-token}.
     *
     * @param projectId the project of the caller's organization, which the caller may edit, whose repository the token is for
     * @return a future completing with the token, when it expires, and the repository's clone URL and default branch
     */
    @AuthzCheck(permission = AuthzConstants.CAN_EDIT)
    Future<ProjectRepoToken> issueWriteToken(String projectId);
}
