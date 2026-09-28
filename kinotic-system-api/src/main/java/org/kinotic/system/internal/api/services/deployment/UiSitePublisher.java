package org.kinotic.system.internal.api.services.deployment;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.domain.api.model.AppHost;
import org.kinotic.domain.api.model.WatchedParent;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.deployment.UiArtifact;
import org.kinotic.management.api.model.deployment.UiDeployment;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.config.UiDeploymentProperties;
import org.kinotic.system.api.model.deployment.DeployTarget;
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
    private final SiteStorageService siteStorageService;
    private final SiteWorkloadFactory siteWorkloadFactory;
    private final WorkloadOrchestrationService workloadOrchestrationService;
    private final KinoticSystemApiProperties properties;

    /**
     * The row a UI publishes to: its existing site, or one minted for its first publish. Fails when
     * the site of a UI of the same name belongs to another project of the application.
     */
    public Future<UiDeployment> deploymentFor(Project project, UiArtifact ui, UiDeployment existing) {
        return existing == null ? mintDeployment(project, ui) : Future.succeededFuture(existing);
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
        return uiDeployment().resolveHostname(row.getId());
    }

    /**
     * Mints the site's label, {@code <org>--<app>--<ui>}, and its URL under the sites domain. The store
     * enforces the label's uniqueness on create, which keeps a UI name to one project of the application.
     */
    private Future<UiDeployment> mintDeployment(Project project, UiArtifact ui) {
        String label = new AppHost(project.getOrganizationId(), project.getApplicationId()).siteLabel(ui.name());
        Future<UiDeployment> ret;
        if (label.length() > AppHost.MAX_LABEL_LENGTH) {
            ret = Future.failedFuture(new IllegalStateException("The hostname label " + label + " for UI " + ui.name()
                    + " of application " + project.getApplicationId() + " of organization " + project.getOrganizationId()
                    + " is longer than " + AppHost.MAX_LABEL_LENGTH + " characters; shorten the application or UI name"));
        } else {
            UiDeployment deployment = new UiDeployment()
                    .setId(label)
                    .setUrl(uiDeployment().resolveSiteUrl(label))
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
                            : error));
        }
        return ret;
    }

    private UiDeploymentProperties uiDeployment() {
        return properties.getSystemApi().getUiDeployment();
    }

}
