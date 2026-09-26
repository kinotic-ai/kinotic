package org.kinotic.system.internal.api.services.storage;

import com.azure.storage.file.datalake.sas.PathSasPermission;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.services.storage.OrganizationStoragePaths;
import org.kinotic.system.api.services.storage.OrganizationStorageService;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Data Lake SDK backed {@link OrganizationStorageService}. Reaches the organization storage account
 * at its configured blob endpoint as the server's Azure identity, which signs each URL with a user
 * delegation key for the one directory or file it names.
 */
@Component
public class AzureOrganizationStorageService implements OrganizationStorageService {

    private final DataLakeSasIssuer organizations;

    public AzureOrganizationStorageService(Vertx vertx, KinoticSystemApiProperties kinoticProperties) {
        this.organizations = new DataLakeSasIssuer(vertx, kinoticProperties.getSystemApi().getOrganizationStorage().getBlobEndpoint());
    }

    @Override
    public Future<String> issueWriteUrl(String directory, Duration ttl) {
        // list and delete within the directory let the workload clear the files it replaces
        return organizations.issueDirectoryUrl(OrganizationStoragePaths.CONTAINER, directory, ttl,
                                               new PathSasPermission().setCreatePermission(true)
                                                                      .setWritePermission(true)
                                                                      .setListPermission(true)
                                                                      .setDeletePermission(true));
    }

    @Override
    public Future<String> issueReadUrl(String file, Duration ttl) {
        return organizations.issueFileUrl(OrganizationStoragePaths.CONTAINER, file, ttl, new PathSasPermission().setReadPermission(true));
    }

}
