package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.idl.api.annotations.McpTool;
import org.kinotic.management.api.model.MicroserviceDeployment;

import java.util.List;

/**
 * The microservice deployments of the caller's organization's projects, as the console shows
 * and acts on them. Removal is the one path that stops a microservice's VM for good and removes
 * its identity; a deployment whose microservice a commit dropped stays orphaned until it is
 * removed here.
 */
@Publish
public interface MicroserviceDeploymentService {

    /**
     * Lists the microservice deployments of one of the caller's organization's projects,
     * ordered by microservice name. A project that has never deployed has none.
     *
     * @param projectId a project belonging to the caller's organization
     * @return a future emitting the deployments, empty when the project has none
     */
    @McpTool
    Future<List<MicroserviceDeployment>> findAllForProject(String projectId);

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
    Future<MicroserviceDeployment> restart(String deploymentId);

    /**
     * Asks for the deployment's removal: its worker stops the microservice's VM, removes its
     * machine identity, and deletes the record. A microservice the project's current commit still
     * contains is deployed again by the next deployment.
     *
     * @param deploymentId the deployment of a microservice of one of the caller's organization's projects
     * @return a future completing when the removal is asked for
     */
    Future<Void> remove(String deploymentId);

}
