package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.DeploymentStatus;
import org.kinotic.domain.api.model.DeploymentStatusType;
import org.kinotic.management.api.model.UiDeployment;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.config.UiDeploymentProperties;
import org.kinotic.system.api.services.UiDeploymentProvisioner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Reports when a published UI serves through the platform's Front Door profile at
 * {@code <label>.<sitesDomain>}. The profile serves every site of the sites domain from the
 * sites storage account, keyed by hostname, through what terraform provisions once, so a site
 * needs nothing of its own: it is provisioning from its first publish until it serves the
 * deployment's commit at its hostname, which the provisioner keeps checking in the background.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kinotic.systemApi.uiDeployment.disableProvisioner",
                       havingValue = "false", matchIfMissing = true)
public class FrontDoorUiDeploymentProvisioner implements UiDeploymentProvisioner {

    private static final String VERSION_FILE = "version.json";
    private static final long HTTP_TIMEOUT_MS = 10_000;
    private static final long POLL_INTERVAL_MS = 30_000;
    /** Nothing is provisioned per site, so a site that does not serve within this is checked again only when listed. */
    private static final long POLL_TIMEOUT_MS = 15 * 60_000;

    private final Vertx vertx;
    private final KinoticSystemApiProperties kinoticProperties;
    private final UiDeploymentRepository uiDeploymentRepository;
    // Built on first use rather than at startup, so a server that never publishes a UI opens no client
    private volatile WebClient web;

    @Override
    public Future<UiDeployment> provision(UiDeployment deployment) {
        Validate.notNull(deployment, "deployment is required");
        String label = deployment.getId();
        log.info("Serving site {} for UI {} of project {}", properties().resolveHostname(label), deployment.getName(), deployment.getProjectId());
        return servingStatus(deployment)
                .map(deployment::setStatus)
                .onSuccess(row -> {
                    log.info("Site {} is {}", properties().resolveHostname(label), row.getStatus().type());
                    if (row.getStatus().type() == DeploymentStatusType.PROVISIONING) {
                        schedulePoll(label, System.currentTimeMillis() + POLL_TIMEOUT_MS);
                    }
                });
    }

    @Override
    public Future<UiDeployment> checkProvisioning(UiDeployment deployment) {
        Validate.notNull(deployment, "deployment is required");
        return servingStatus(deployment).map(deployment::setStatus);
    }

    // Front Door serves a hostname of the wildcard domain from its first request, so only a
    // request through the site tells that it serves: the version file for the commit, and
    // the root for the spa rule, which the version file bypasses
    private Future<DeploymentStatus> servingStatus(UiDeployment deployment) {
        String site = properties().resolveSiteUrl(deployment.getId());
        String versionUrl = site + "/" + VERSION_FILE;
        String rootUrl = site + "/";
        return web().getAbs(versionUrl).timeout(HTTP_TIMEOUT_MS).send()
                    .compose(version -> {
                        Future<DeploymentStatus> ret;
                        String served = version.statusCode() == 200 ? servedCommit(version) : null;
                        if (served != null && served.equals(deployment.getCommitSha())) {
                            ret = web().getAbs(rootUrl).timeout(HTTP_TIMEOUT_MS).send()
                                       .map(root -> servesHtml(root)
                                               ? new DeploymentStatus(DeploymentStatusType.READY)
                                               : new DeploymentStatus(DeploymentStatusType.PROVISIONING,
                                                                      rootUrl + " answered " + root.statusCode() + " " + root.getHeader("Content-Type")));
                        } else if (version.statusCode() == 200) {
                            ret = Future.succeededFuture(new DeploymentStatus(DeploymentStatusType.PROVISIONING,
                                                                              versionUrl + " serves commit " + served + ", not " + deployment.getCommitSha()));
                        } else {
                            ret = Future.succeededFuture(new DeploymentStatus(DeploymentStatusType.PROVISIONING,
                                                                              versionUrl + " answered " + version.statusCode()));
                        }
                        return ret;
                    })
                    .otherwise(error -> new DeploymentStatus(DeploymentStatusType.PROVISIONING, site + " is unreachable: " + error.getMessage()));
    }

    // A 200 that is not the version file, such as the index the spa rule serves, is not a commit
    private static String servedCommit(HttpResponse<Buffer> response) {
        String ret;
        try {
            ret = response.bodyAsJsonObject().getString("commitSha");
        } catch (RuntimeException e) {
            ret = null;
        }
        return ret;
    }

    // The root unrewritten is the UI's directory, which the account answers with an empty 200
    private static boolean servesHtml(HttpResponse<Buffer> response) {
        String type = response.getHeader("Content-Type");
        return response.statusCode() == 200 && type != null && type.startsWith("text/html");
    }

    /**
     * Checks a provisioning site every {@link #POLL_INTERVAL_MS} until it is ready or failed,
     * recording the outcome on its row, or until the deadline passes. A row removed or advanced
     * meanwhile ends the polling.
     */
    private void schedulePoll(String label, long deadline) {
        vertx.timer(POLL_INTERVAL_MS)
             .compose(v -> uiDeploymentRepository.findById(label))
             .compose(row -> {
                 Future<Void> ret;

                 if (row == null || row.getStatus().type() != DeploymentStatusType.PROVISIONING) {
                     ret = Future.succeededFuture();
                 } else {
                     ret = checkProvisioning(row).compose(checked -> {
                         Future<Void> saved;

                         if (checked.getStatus().type() != DeploymentStatusType.PROVISIONING) {
                             log.info("Site {} is {}", properties().resolveHostname(label), checked.getStatus().type());
                             saved = uiDeploymentRepository.save(checked.setUpdated(new Date())).mapEmpty();
                         } else if (System.currentTimeMillis() < deadline) {
                             schedulePoll(label, deadline);
                             saved = Future.succeededFuture();
                         } else {
                             log.warn("Site {} is still provisioning after {} minutes; it is checked again when listed",
                                      properties().resolveHostname(label), POLL_TIMEOUT_MS / 60_000);
                             saved = Future.succeededFuture();
                         }

                         return saved;
                     });
                 }

                 return ret;
             })
             .onFailure(error -> {
                 log.warn("Checking site {} failed", properties().resolveHostname(label), error);

                 if (System.currentTimeMillis() < deadline) {
                     schedulePoll(label, deadline);
                 }
             });
    }

    private WebClient web() {
        WebClient ret = web;
        if (ret == null) {
            ret = WebClient.create(vertx);
            web = ret;
        }
        return ret;
    }

    private UiDeploymentProperties properties() {
        return kinoticProperties.getSystemApi().getUiDeployment();
    }

}
