package org.kinotic.system.internal.api.services.deployment;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.management.api.model.deployment.DeploymentStatus;
import org.kinotic.management.api.model.deployment.DeploymentStatusType;
import org.kinotic.management.api.model.deployment.UiDeployment;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.config.UiDeploymentProperties;
import org.kinotic.system.api.services.deployment.UiDeploymentProvisioner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;


/**
 * Reports whether a published UI serves through the platform's Front Door profile at
 * {@code <label>.<sitesDomain>}. The profile serves every site of the sites domain from the
 * sites storage account, keyed by hostname, through what terraform provisions once, so a site
 * needs nothing of its own: only a request through it tells whether it serves.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kinotic.systemApi.disableAzureStorage",
                       havingValue = "false", matchIfMissing = true)
public class FrontDoorUiDeploymentProvisioner implements UiDeploymentProvisioner {

    private static final String VERSION_FILE = "version.json";
    private static final long HTTP_TIMEOUT_MS = 10_000;

    private final Vertx vertx;
    private final KinoticSystemApiProperties kinoticProperties;
    // Built on first use rather than at startup, so a server that never publishes a UI opens no client
    private volatile WebClient web;

    // Front Door serves a hostname of the wildcard domain from its first request, so only a
    // request through the site tells that it serves: the version file for the commit, and
    // the root for the spa rule, which the version file bypasses
    @Override
    public Future<DeploymentStatus> check(UiDeployment deployment, String commitSha) {
        Validate.notNull(deployment, "deployment is required");
        Validate.notBlank(commitSha, "commitSha is required");
        String site = properties().resolveSiteUrl(deployment.getId());
        String versionUrl = site + "/" + VERSION_FILE;
        String rootUrl = site + "/";
        return web().getAbs(versionUrl).timeout(HTTP_TIMEOUT_MS).send()
                    .compose(version -> {
                        Future<DeploymentStatus> ret;
                        String served = version.statusCode() == 200 ? servedCommit(version) : null;
                        if (served != null && served.equals(commitSha)) {
                            ret = web().getAbs(rootUrl).timeout(HTTP_TIMEOUT_MS).send()
                                       .map(root -> servesHtml(root)
                                               ? new DeploymentStatus(DeploymentStatusType.READY)
                                               : new DeploymentStatus(DeploymentStatusType.PROVISIONING,
                                                                      rootUrl + " answered " + root.statusCode() + " " + root.getHeader("Content-Type")));
                        } else if (version.statusCode() == 200) {
                            ret = Future.succeededFuture(new DeploymentStatus(DeploymentStatusType.PROVISIONING,
                                                                              versionUrl + " serves commit " + served + ", not " + commitSha));
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
