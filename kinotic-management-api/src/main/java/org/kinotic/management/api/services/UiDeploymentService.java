package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.management.api.model.UiDeployment;

import java.util.List;

/**
 * The UI deployments of the caller's organization's projects, as the console shows and acts
 * on them: the sites serving each project's UIs. Removal is the one path that takes a site
 * down and deletes its files; a deployment whose UI a commit dropped stays orphaned, still
 * serving, until it is removed here.
 */
@Publish
public interface UiDeploymentService {

    /**
     * Lists the UI deployments of one of the caller's organization's projects, ordered by UI
     * name, advancing any left provisioning whose site has since become ready or failed. A
     * project that has never published a UI has none.
     *
     * @param projectId a project belonging to the caller's organization
     * @return a future emitting the deployments, empty when the project has none
     */
    Future<List<UiDeployment>> findAllForProject(String projectId);

    /**
     * Provisions the deployment's site again, completing whatever an earlier attempt left
     * missing and validating its hostname again when that lapsed. The deployment comes back
     * ready, provisioning or failed with the reason.
     *
     * @param deploymentId the deployment of a UI of one of the caller's organization's projects
     * @return a future emitting the deployment with its status
     */
    Future<UiDeployment> retryProvisioning(String deploymentId);

    /**
     * Removes the deployment: takes its site down, deletes the UI's published files, and
     * deletes the record. A UI the project's current commit still contains is published
     * again, at a site minted anew, by the next deployment.
     *
     * @param deploymentId the deployment of a UI of one of the caller's organization's projects
     * @return a future completing when everything is gone
     */
    Future<Void> remove(String deploymentId);

}
