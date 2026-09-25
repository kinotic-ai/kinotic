package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
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
    @McpTool
    Future<List<UiDeployment>> findAllForProject(String projectId);

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
    Future<Page<WatchEvent>> findHistory(String deploymentId, Pageable pageable);

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
