package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.model.MicroserviceDeployment;

import java.util.List;

/**
 * The microservice deployments of the caller's organization's projects, as the console shows
 * and acts on them. Removal is the one path that destroys a microservice's VM and identity;
 * a deployment whose microservice a commit dropped stays orphaned until it is removed here.
 * Published in the management-api zone so organization members can reach it.
 */
@Publish
@Zone(DomainUtil.MANAGEMENT_API_ZONE)
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
     * Restarts the microservice's VM in place: a running VM is stopped and booted again with
     * its disk intact, a stopped or failed one is booted again. Fails when the deployment has
     * no VM, which the next deployment of the project resolves.
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
