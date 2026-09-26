package org.kinotic.management.internal.api.services.storage;

import com.azure.storage.file.datalake.sas.PathSasPermission;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.kinotic.management.api.services.storage.AzureStorageUrlIssuer;
import org.kinotic.management.api.services.storage.OrganizationStoragePaths;
import org.kinotic.management.api.services.storage.OrganizationStorageService;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Data Lake SDK backed {@link OrganizationStorageService}. Reaches the organization storage account
 * at its configured blob endpoint as the server's Azure identity, which signs each URL with a user
 * delegation key for the one file it names.
 */
@Component
public class AzureOrganizationStorageService implements OrganizationStorageService {

    private final AzureStorageUrlIssuer organizations;

    public AzureOrganizationStorageService(Vertx vertx, KinoticManagementApiProperties kinoticProperties) {
        this.organizations = new AzureStorageUrlIssuer(vertx, kinoticProperties.getManagementApi().getOrganizationStorage().getBlobEndpoint());
    }

    @Override
    public Future<String> issueWriteUrl(String file, Duration ttl) {
        return organizations.issueFileUrl(OrganizationStoragePaths.CONTAINER, file, ttl,
                                          new PathSasPermission().setCreatePermission(true).setWritePermission(true));
    }

    @Override
    public Future<String> issueReadUrl(String file, Duration ttl) {
        return organizations.issueFileUrl(OrganizationStoragePaths.CONTAINER, file, ttl, new PathSasPermission().setReadPermission(true));
    }

}
