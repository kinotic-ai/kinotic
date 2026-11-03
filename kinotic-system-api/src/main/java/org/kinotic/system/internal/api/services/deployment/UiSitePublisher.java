package org.kinotic.system.internal.api.services.deployment;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.core.api.utils.ZoneUtil;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.config.PublishedSitesProperties;
import org.kinotic.domain.api.utils.HostLabelUtil;
import org.kinotic.domain.api.model.WatchedParent;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.services.OrganizationDnsRecordProvisioner;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.deployment.UiArtifact;
import org.kinotic.management.api.model.deployment.UiDeployment;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.management.api.model.deployment.DeployTarget;
import org.kinotic.system.api.services.deployment.SiteStorageService;
import org.kinotic.system.api.services.workload.WorkloadOrchestrationService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * The site side of publishing a project's UIs: the deployment row a UI publishes to, minted on the
 * UI's first publish with the hostname label that names its application and UI, and the upload of
 * the built UIs through a foreground publish workload holding nothing but a short-lived upload URL
 * per site.
 */
@Component
@RequiredArgsConstructor
public class UiSitePublisher {

    /** Longer than any upload takes, and short enough that a leaked URL is soon worthless. */
    private static final Duration UPLOAD_URL_TTL = Duration.ofHours(1);

    private final UiDeploymentRepository uiDeploymentRepository;
    private final ApplicationRepository applicationRepository;
    private final SiteStorageService siteStorageService;
    private final SiteWorkloadFactory siteWorkloadFactory;
    private final WorkloadOrchestrationService workloadOrchestrationService;
    private final OrganizationDnsRecordProvisioner dnsRecordProvisioner;
    private final KinoticDomainProperties domainProperties;

    /**
     * The row a UI publishes to: its existing site, or one minted for its first publish, which also ensures
     * the organization's subdomain is provisioned at the edge and makes the UI the application's primary UI
     * when the application has none. An existing site is linked at its address under the current sites
     * domain, as is the application's primary UI when it is that site. Fails when the site of a UI of the same
     * name belongs to another project of the application, or the edge refused the subdomain.
     */
    public Future<UiDeployment> deploymentFor(Project project, UiArtifact ui, UiDeployment existing) {
        return existing == null
                ? dnsRecordProvisioner.ensure(project.getOrganizationId()).compose(v -> mintDeployment(project, ui))
                : relink(project, existing);
    }

    // a site's address is its id under the sites domain, so a site minted before the domain changed is published
    // under the new address, and is linked there, with the organization's subdomain provisioned under it
    private Future<UiDeployment> relink(Project project, UiDeployment existing) {
        String url = publishedSites().resolveSiteUrl(existing.getId());
        Future<UiDeployment> ret;
        if (url.equals(existing.getUrl())) {
            ret = Future.succeededFuture(existing);
        } else {
            ret = dnsRecordProvisioner.ensure(project.getOrganizationId())
                                      .compose(v -> uiDeploymentRepository.recordUrl(existing.getId(), url))
                                      .compose(v -> applicationRepository.updatePrimaryUiUrl(project.applicationKey(), existing.getName(), url))
                                      .map(v -> existing.setUrl(url));
        }
        return ret;
    }

    /**
     * Runs the publish workload with one upload URL per site, each scoped to that site's
     * directory in the sites account.
     *
     * @return the publish workload once its run ended, whatever the outcome
     */
    public Future<Workload> upload(Project project, DeployTarget target, List<UiDeployment> rows, String commitSha) {
        return Future.all(rows.stream()
                              .map(row -> siteStorageService.issueUploadUrl(hostname(row), UPLOAD_URL_TTL)
                                                            .map(url -> Map.entry(row.getName(), url)))
                              .toList())
                .map(all -> {
                    JsonObject urls = new JsonObject();
                    all.<Map.Entry<String, String>>list().forEach(entry -> urls.put(entry.getKey(), entry.getValue()));
                    return siteWorkloadFactory.publish(project, target, urls, commitSha);
                })
                .compose(workloadOrchestrationService::deployWorkload);
    }

    private String hostname(UiDeployment row) {
        return publishedSites().resolveHostname(row.getId());
    }

    /**
     * Mints the site's id, {@code <app>--<ui>.<org>}, its hostname under the sites domain, and its URL, and makes
     * the UI the application's primary UI when the application has none. The store enforces the id's uniqueness
     * on create, which keeps a UI name to one project of the application.
     */
    private Future<UiDeployment> mintDeployment(Project project, UiArtifact ui) {
        String label = HostLabelUtil.siteLabel(project.applicationKey(), ui.name());
        // guards rows written around the services that validate each part, such as a migration's seed data:
        // a label outside DNS's grammar names no host under the sites domain
        ZoneUtil.validateLabel(label);
        ZoneUtil.validateLabel(project.getOrganizationId());
        Future<UiDeployment> ret;
        if (label.length() > HostLabelUtil.MAX_SITE_LABEL_LENGTH) {
            ret = Future.failedFuture(new IllegalStateException("The hostname label " + label + " for UI " + ui.name()
                    + " of application " + project.getApplicationId() + " of organization " + project.getOrganizationId()
                    + " is longer than " + HostLabelUtil.MAX_SITE_LABEL_LENGTH
                    + " characters, the most a site label may take so a tenant's name fits in front of it; shorten the application or UI name"));
        } else {
            String siteId = HostLabelUtil.siteId(project.applicationKey(), ui.name());
            UiDeployment deployment = new UiDeployment()
                    .setId(siteId)
                    .setUrl(publishedSites().resolveSiteUrl(siteId))
                    .setOrganizationId(project.getOrganizationId())
                    .setApplicationId(project.getApplicationId())
                    .setProjectId(project.getId())
                    .setName(ui.name())
                    .setCreated(new Date())
                    .setUpdated(new Date());
            deployment.getState().setParent(new WatchedParent(WatchedType.PROJECT_DEPLOYMENT, project.getOrganizationId(), project.getId()));
            ret = uiDeploymentRepository.create(deployment)
                    .recover(error -> Future.failedFuture(error instanceof AlreadyExistsException
                            ? new IllegalStateException("The site of UI " + ui.name() + " of application " + project.getApplicationId()
                                    + " belongs to another project of the application; UI names are unique within an application")
                            : error))
                    .compose(created -> applicationRepository.claimPrimaryUi(project.applicationKey(), created.getName(), created.getUrl())
                                                             .map(created));
        }
        return ret;
    }

    private PublishedSitesProperties publishedSites() {
        return domainProperties.getDomain().getPublishedSites();
    }

}
