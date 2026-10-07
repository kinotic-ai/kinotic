package org.kinotic.management.api.services.deployment;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.idl.api.annotations.McpTool;
import org.kinotic.management.api.model.deployment.UiDeployment;

import java.util.List;

/**
 * The UI deployments of the caller's organization's projects, as the console shows and acts
 * on them: the sites serving each project's UIs. Removal is the one path that takes a site
 * down and deletes its files; a deployment whose UI a commit dropped stays orphaned, still
 * serving, until it is removed here.
 *
 * <p>A deployment is named by its id alone, which is in no authorization graph, so a function naming one is
 * checked on the caller's organization: reading a deployment's history needs {@code can_view_deployments} of
 * the organization, and removing or restarting one {@code can_manage_deployments}. A listing names the project
 * or the application it is of, and is checked on that.
 */
@Publish
@AuthzResource(value = AuthzUtil.ORGANIZATION_TYPE, resourceId = "{@organizationId}")
public interface UiDeploymentService {

    /**
     * Lists the UI deployments of one of the caller's organization's projects, ordered by UI
     * name. A project that has never published a UI has none.
     *
     * @param projectId a project belonging to the caller's organization
     * @return a future emitting the deployments, empty when the project has none
     */
    @McpTool
    @AuthzCheck(resource = AuthzUtil.PROJECT_TYPE, resourceId = "{projectId}", permission = AuthzUtil.CAN_VIEW)
    Future<List<UiDeployment>> findAllForProject(String projectId);

    /**
     * Lists the UI deployments of all the projects of one of the caller's organization's
     * applications, ordered by UI name. An application whose projects never published a UI has none.
     *
     * @param applicationId an application belonging to the caller's organization
     * @return a future emitting the deployments, empty when the application has none
     */
    @McpTool
    @AuthzCheck(resource = AuthzUtil.APPLICATION_TYPE, resourceId = "{applicationId}", permission = AuthzUtil.CAN_VIEW)
    Future<List<UiDeployment>> findAllForApplication(String applicationId);

    /**
     * Lists what happened to one of the caller's organization's UI deployments and to the uploads
     * it ran, newest first: each change of what the deployment should be and of what it is, each
     * status an upload's run passed through, and each mark set beside them, with what caused it.
     *
     * @param deploymentId the deployment of a UI of one of the caller's organization's projects
     * @param pageable     the page to return
     * @return a future emitting a page of ledger entries, empty when nothing has happened to the deployment
     */
    @McpTool
    @AuthzCheck(permission = "can_view_deployments")
    Future<Page<WatchEvent>> findHistory(String deploymentId, Pageable pageable);

    /**
     * Asks for the deployment's removal: its worker takes the site down, deletes the UI's
     * published files, and deletes the record. A UI the project's current commit still contains
     * is published again, at a site minted anew, by the next deployment.
     *
     * @param deploymentId the deployment of a UI of one of the caller's organization's projects
     * @return a future completing when the removal is asked for
     */
    @AuthzCheck(permission = "can_manage_deployments")
    Future<Void> remove(String deploymentId);

}
