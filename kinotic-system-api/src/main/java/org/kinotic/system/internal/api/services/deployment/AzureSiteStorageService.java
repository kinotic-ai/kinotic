package org.kinotic.system.internal.api.services.deployment;

import com.azure.storage.file.datalake.sas.PathSasPermission;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.services.deployment.SiteStorageService;
import org.kinotic.system.api.services.deployment.UiStoragePaths;
import org.kinotic.system.internal.api.services.storage.DataLakeSasIssuer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Data Lake SDK backed {@link SiteStorageService}. Reaches the sites account at its configured
 * blob endpoint as the server's Azure identity, which signs each URL with a user delegation
 * key for the site's directory alone.
 */
@Component
public class AzureSiteStorageService implements SiteStorageService {

    private final DataLakeSasIssuer sites;

    public AzureSiteStorageService(Vertx vertx, KinoticSystemApiProperties kinoticProperties) {
        this.sites = new DataLakeSasIssuer(vertx, kinoticProperties.getSystemApi().getUiDeployment().getSitesStorageEndpoint());
    }

    @Override
    public Future<String> issueUploadUrl(String hostname, Duration ttl) {
        // list and delete within the directory let the workload clear the files of other commits
        return sites.issueDirectoryUrl(UiStoragePaths.SITES_CONTAINER, UiStoragePaths.sitePrefix(hostname), ttl,
                                       new PathSasPermission().setCreatePermission(true)
                                                              .setWritePermission(true)
                                                              .setListPermission(true)
                                                              .setDeletePermission(true));
    }

    @Override
    public Future<String> issueRemovalUrl(String hostname, Duration ttl) {
        return sites.issueDirectoryUrl(UiStoragePaths.SITES_CONTAINER, UiStoragePaths.sitePrefix(hostname), ttl,
                                       new PathSasPermission().setListPermission(true).setDeletePermission(true));
    }

}
