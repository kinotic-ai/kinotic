package org.kinotic.system.internal.api.services.deployment;

import com.azure.storage.file.datalake.sas.PathSasPermission;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.services.deployment.SiteStorageService;
import org.kinotic.management.api.services.storage.AzureStorageUrlIssuer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Data Lake SDK backed {@link SiteStorageService}. Reaches the sites account at its configured
 * blob endpoint as the server's Azure identity, which signs each URL with a user delegation
 * key for the site's directory alone.
 */
@Component
public class AzureSiteStorageService implements SiteStorageService {

    /** The one container of the sites account. */
    private static final String CONTAINER = "sites";

    private final AzureStorageUrlIssuer sites;

    public AzureSiteStorageService(Vertx vertx, KinoticSystemApiProperties kinoticProperties) {
        this.sites = new AzureStorageUrlIssuer(vertx, kinoticProperties.getSystemApi().getUiDeployment().getSitesStorageEndpoint());
    }

    @Override
    public Future<String> issueUploadUrl(String hostname, Duration ttl) {
        // list and delete within the directory let the workload clear the files of other commits
        return sites.issueDirectoryUrl(CONTAINER, hostname, ttl,
                                       new PathSasPermission().setCreatePermission(true)
                                                              .setWritePermission(true)
                                                              .setListPermission(true)
                                                              .setDeletePermission(true));
    }

    @Override
    public Future<String> issueRemovalUrl(String hostname, Duration ttl) {
        return sites.issueDirectoryUrl(CONTAINER, hostname, ttl,
                                       new PathSasPermission().setListPermission(true).setDeletePermission(true));
    }

}
