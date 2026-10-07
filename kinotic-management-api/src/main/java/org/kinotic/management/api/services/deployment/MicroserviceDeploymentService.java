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
import org.kinotic.management.api.model.deployment.MicroserviceDeployment;

import java.util.List;

/**
 * The microservice deployments of the caller's organization's projects, as the console shows
 * and acts on them. Removal is the one path that stops a microservice's VM for good and removes
 * its identity; a deployment whose microservice a commit dropped stays orphaned until it is
 * removed here.
 *
 * <p>A deployment is named by its id alone, which is in no authorization graph, so a function naming one is
 * checked on the caller's organization: reading a deployment's history needs {@code can_view_deployments} of
 * the organization, and removing or restarting one {@code can_manage_deployments}. A listing names the project
 * or the application it is of, and is checked on that.
 */
@Publish
@AuthzResource(value = AuthzUtil.ORGANIZATION_TYPE, resourceId = "{@organizationId}")
public interface MicroserviceDeploymentService {

    /**
     * Lists the microservice deployments of one of the caller's organization's projects,
     * ordered by microservice name. A project that has never deployed has none.
     *
     * @param projectId a project belonging to the caller's organization
     * @return a future emitting the deployments, empty when the project has none
     */
    @McpTool
    @AuthzCheck(resource = AuthzUtil.PROJECT_TYPE, resourceId = "{projectId}", permission = AuthzUtil.CAN_VIEW)
    Future<List<MicroserviceDeployment>> findAllForProject(String projectId);

    /**
     * Lists what happened to one of the caller's organization's microservice deployments and to
     * the VMs it ran, newest first: each change of what the deployment should be and of what it
     * is, each status a VM's run passed through, and each mark set beside them, with what caused
     * it.
     *
     * @param deploymentId the deployment of a microservice of one of the caller's organization's projects
     * @param pageable     the page to return
     * @return a future emitting a page of ledger entries, empty when nothing has happened to the deployment
     */
    @McpTool
    @AuthzCheck(permission = "can_view_deployments")
    Future<Page<WatchEvent>> findHistory(String deploymentId, Pageable pageable);

    /**
     * Runs the microservice in a fresh VM from the project's current deployment: a VM still
     * running is stopped, and the deployment's worker replaces it once the run has ended, keeping
     * the ended run's record and logs; a deployment without a running VM is deployed again. Fails
     * when the project has never been deployed.
     *
     * @param deploymentId the deployment of a microservice of one of the caller's organization's projects
     * @return a future emitting the deployment as it stood when the restart was asked for
     */
    @McpTool
    @AuthzCheck(permission = "can_manage_deployments")
    Future<MicroserviceDeployment> restart(String deploymentId);

    /**
     * Asks for the deployment's removal: its worker stops the microservice's VM, removes its
     * machine identity, and deletes the record. A microservice the project's current commit still
     * contains is deployed again by the next deployment.
     *
     * @param deploymentId the deployment of a microservice of one of the caller's organization's projects
     * @return a future completing when the removal is asked for
     */
    @AuthzCheck(permission = "can_manage_deployments")
    Future<Void> remove(String deploymentId);

}
