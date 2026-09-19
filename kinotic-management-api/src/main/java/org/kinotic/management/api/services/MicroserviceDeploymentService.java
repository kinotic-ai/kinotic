package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.management.api.model.MicroserviceDeployment;

import java.util.List;

/**
 * The microservice deployments of the caller's organization's projects, as the console shows
 * and acts on them. Removal is the one path that destroys a microservice's VM and identity;
 * a deployment whose microservice a commit dropped stays orphaned until it is removed here.
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
    Future<List<MicroserviceDeployment>> findAllForProject(String projectId);

    /**
     * Runs the microservice in a fresh VM from the project's current deployment, stopping the
     * VM that runs it first when one does. The ended run keeps its record and logs. Fails when
     * the project has never been deployed.
     *
     * @param deploymentId the deployment of a microservice of one of the caller's organization's projects
     * @return a future emitting the deployment
     */
    Future<MicroserviceDeployment> restart(String deploymentId);

    /**
     * Removes the deployment: destroys the microservice's VM, removes its machine identity, and
     * deletes the record. A microservice the project's current commit still contains is
     * deployed again by the next deployment.
     *
     * @param deploymentId the deployment of a microservice of one of the caller's organization's projects
     * @return a future completing when everything is gone
     */
    Future<Void> remove(String deploymentId);

}
