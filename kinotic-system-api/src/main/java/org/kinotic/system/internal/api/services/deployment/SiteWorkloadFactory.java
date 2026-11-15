package org.kinotic.system.internal.api.services.deployment;

import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.WatchedParent;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.deployment.UiDeployment;
import org.kinotic.management.api.model.workload.VolumeMount;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.system.api.config.DeploymentProperties;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.management.api.model.deployment.DeployTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

/**
 * Builds the one-shot workloads that act on a site's files in the sites account, which the
 * server itself never touches: the publish that uploads a deploy's built UIs, the removal
 * that deletes a site, and the suspension and resumption that switch a site's index between
 * a redirect to its organization's deactivated page and its own. Each carries nothing but the URLs issued for its run, holds no Kinotic
 * credentials and no machine identity, and may reach the sites account's host alone.
 */
@Component
@RequiredArgsConstructor
public class SiteWorkloadFactory {

    /** The portal's page saying an organization is deactivated, followed by the organization's id. */
    private static final String DEACTIVATED_PATH = "/deactivated/";

    private final KinoticSystemApiProperties kinoticProperties;
    private final KinoticDomainProperties domainProperties;

    /**
     * The publish workload of a deploy: the checkout read-only at {@code /workspace}, and one
     * upload URL per UI, by name. The sites of a deactivated organization are published suspended.
     */
    public Workload publish(Project project, DeployTarget target, JsonObject uploadUrls, String commitSha, boolean organizationDeactivated) {
        Workload workload = siteWorkload("project-ui-publish-" + project.getId(), "UI publish for project " + project.getId(),
                                         new WatchedParent(WatchedType.PROJECT_DEPLOYMENT, project.getOrganizationId(), project.getId()),
                                         target.nodeId(), project.applicationKey(), "src/publish-ui.ts");
        workload.setId(target.uiPublishWorkloadId());
        workload.getEnvironment().put("KINOTIC_UI_COMMIT", commitSha);
        if (organizationDeactivated) {
            workload.getEnvironment().put("KINOTIC_UI_DEACTIVATED_URL", deactivatedUrl(project.getOrganizationId()));
        }
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
     * The removal workload of a site, run on any node with room.
     */
    public Workload removal(UiDeployment deployment, String removalUrl) {
        Workload workload = ofSite(deployment, "site-remove-", "Removal", "src/remove-ui.ts");
        workload.getSecrets().put("KINOTIC_UI_REMOVAL_URL", removalUrl);
        allowSitesAccount(workload, removalUrl);
        return workload;
    }

    /**
     * The suspension workload of a site, run on any node with room: the site serves a redirect to the
     * portal's page saying its organization is deactivated in place of its index.
     */
    public Workload suspension(UiDeployment deployment, String indexUrl) {
        Workload workload = ofSite(deployment, "site-suspend-", "Suspension", "src/suspend-ui.ts");
        workload.getSecrets().put("KINOTIC_UI_INDEX_URL", indexUrl);
        workload.getEnvironment().put("KINOTIC_UI_DEACTIVATED_URL", deactivatedUrl(deployment.getOrganizationId()));
        allowSitesAccount(workload, indexUrl);
        return workload;
    }

    /**
     * The resumption workload of a site, run on any node with room: the site serves its own index again.
     */
    public Workload resumption(UiDeployment deployment, String indexUrl) {
        Workload workload = ofSite(deployment, "site-resume-", "Resumption", "src/resume-ui.ts");
        workload.getSecrets().put("KINOTIC_UI_INDEX_URL", indexUrl);
        allowSitesAccount(workload, indexUrl);
        return workload;
    }

    private String deactivatedUrl(String organizationId) {
        return domainProperties.getDomain().getPortalBaseUrl() + DEACTIVATED_PATH + organizationId;
    }

    // nothing of a site lives on a node, so a workload acting on its files runs wherever there is room, which
    // outlives the node the site was published from
    private Workload ofSite(UiDeployment deployment, String namePrefix, String action, String entrypoint) {
        return siteWorkload(namePrefix + deployment.getId(), action + " of site " + deployment.getId(),
                            new WatchedParent(WatchedType.UI_DEPLOYMENT, deployment.getOrganizationId(), deployment.getId()),
                            null, new ApplicationKey(deployment.getOrganizationId(), deployment.getApplicationId()), entrypoint);
    }

    private Workload siteWorkload(String name, String description, WatchedParent parent, String nodeId,
                                  ApplicationKey applicationKey, String entrypoint) {
        DeploymentProperties deployment = kinoticProperties.getSystemApi().getDeployment();
        Workload workload = new Workload(name, deployment.getWorkloadRunnerImage());
        workload.setDescription(description);
        workload.getState().setParent(parent);
        workload.setNodeId(nodeId);
        workload.setOrganizationId(applicationKey.organizationId());
        workload.setApplicationId(applicationKey.applicationId());
        workload.setDetached(false);
        workload.setCpus(ProjectWorkloadSizes.RUNTIME_CPUS);
        workload.setMemoryMb(ProjectWorkloadSizes.RUNTIME_MEMORY_MB);
        workload.setDiskSizeMb(ProjectWorkloadSizes.RUNTIME_DISK_SIZE_MB);
        workload.setEntrypoint(List.of("bun", entrypoint));
        return workload;
    }

    // The account answers on its blob host and, for a directory's removal, its dfs host; an emulator's
    // single host serves both
    private static void allowSitesAccount(Workload workload, String url) {
        String blobHost = URI.create(url).getHost();
        workload.getNetwork().setAllowedHosts(Stream.of(blobHost, blobHost.replace(".blob.", ".dfs.")).distinct().toList());
    }

}
