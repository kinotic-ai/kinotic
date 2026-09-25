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
     * name. A project that has never published a UI has none.
     *
     * @param projectId a project belonging to the caller's organization
     * @return a future emitting the deployments, empty when the project has none
     */
    Future<List<UiDeployment>> findAllForProject(String projectId);

    /**
     * Asks for the deployment's removal: its worker takes the site down, deletes the UI's
     * published files, and deletes the record. A UI the project's current commit still contains
     * is published again, at a site minted anew, by the next deployment.
     *
     * @param deploymentId the deployment of a UI of one of the caller's organization's projects
     * @return a future completing when the removal is asked for
     */
    Future<Void> remove(String deploymentId);

}
