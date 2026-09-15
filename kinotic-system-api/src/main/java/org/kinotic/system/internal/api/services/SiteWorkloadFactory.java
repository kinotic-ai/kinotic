package org.kinotic.system.internal.api.services;

import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.UiDeployment;
import org.kinotic.management.api.model.workload.VolumeMount;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.system.api.config.DeploymentProperties;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.model.deployment.DeployTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/**
 * Builds the one-shot workloads that act on a site's files in the sites account, which the
 * server itself never touches: the publish that uploads a deploy's built UIs, and the removal
 * that deletes a site. Each carries nothing but the URLs issued for its run, holds no Kinotic
 * credentials and no machine identity, and may reach the sites account's host alone.
 */
@Component
@RequiredArgsConstructor
public class SiteWorkloadFactory {

    private final KinoticSystemApiProperties kinoticProperties;

    /**
     * The publish workload of a deploy: the checkout read-only at {@code /workspace}, and one
     * upload URL per UI, by name. Kept after its run, like the sync workload, so its logs stay
     * inspectable until the next run retires it.
     */
    public Workload publish(Project project, DeployTarget target, JsonObject uploadUrls, String commitSha) {
        Workload workload = siteWorkload("project-ui-publish-" + project.getId(), "UI publish for project " + project.getId(),
                                         target.nodeId(), project.getOrganizationId(), project.getApplicationId(), "src/publish-ui.ts");
        workload.setId(target.uiPublishWorkloadId());
        workload.getEnvironment().put("KINOTIC_UI_COMMIT", commitSha);
        // each URL is a credential for the run's length, so they travel as a secret
        workload.getSecrets().put("KINOTIC_UI_UPLOAD_URLS", uploadUrls.encode());
        workload.getVolumeMounts().add(new VolumeMount().setHostPath(target.hostDir())
                                                        .setGuestPath("/workspace")
                                                        .setReadOnly(true));
        // every URL names the one sites account
        allowSitesAccount(workload, uploadUrls.getString(uploadUrls.fieldNames().iterator().next()));
        return workload;
    }

    /**
     * The removal workload of a site, run on the node its project deploys to.
     */
    public Workload removal(UiDeployment deployment, String nodeId, String removalUrl) {
        Workload workload = siteWorkload("site-remove-" + deployment.getId(), "Removal of site " + deployment.getId(),
                                         nodeId, deployment.getOrganizationId(), deployment.getApplicationId(), "src/remove-ui.ts");
        workload.getSecrets().put("KINOTIC_UI_REMOVAL_URL", removalUrl);
        allowSitesAccount(workload, removalUrl);
        return workload;
    }

    private Workload siteWorkload(String name, String description, String nodeId, String organizationId, String applicationId, String entrypoint) {
        DeploymentProperties deployment = kinoticProperties.getSystemApi().getDeployment();
        Workload workload = new Workload(name, deployment.getWorkloadRunnerImage());
        workload.setDescription(description);
        workload.setNodeId(nodeId);
        workload.setOrganizationId(organizationId);
        workload.setApplicationId(applicationId);
        workload.setDetached(false);
        workload.setMemoryMb(deployment.getRuntimeMemoryMb());
        workload.setEntrypoint(List.of("bun", entrypoint));
        return workload;
    }

    // The account answers on its blob host and, for a directory's removal, its dfs host
    private static void allowSitesAccount(Workload workload, String url) {
        String blobHost = URI.create(url).getHost();
        workload.getNetwork().setAllowedHosts(List.of(blobHost, blobHost.replace(".blob.", ".dfs.")));
    }

}
