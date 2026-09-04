package org.kinotic.system.internal.api.services;

import com.azure.storage.blob.BlobServiceAsyncClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.DeploymentStatus;
import org.kinotic.domain.api.model.DeploymentStatusType;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.OrganizationStorage;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.services.OrganizationStorageProvisioner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Date;

/**
 * Fallback {@link OrganizationStorageProvisioner} used when storage provisioning is disabled
 * ({@code kinotic.systemApi.organizationStorage.disableProvisioner=true}). Points every
 * organization at the one configured Azurite, creating the {@code ui} container there, so
 * deployments publish in development and tests without an Azure subscription.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kinotic.systemApi.organizationStorage.disableProvisioner", havingValue = "true")
public class MockOrganizationStorageProvisioner implements OrganizationStorageProvisioner {

    private final OrganizationService organizationService;
    private final Vertx vertx;
    private final KinoticSystemApiProperties kinoticProperties;
    private BlobServiceAsyncClient blobService;

    @Override
    public Future<Organization> ensureStorage(String organizationId) {
        Validate.notBlank(organizationId, "organizationId is required");
        BlobServiceAsyncClient blobService = blobService();
        return organizationService.findById(organizationId)
                .compose(organization -> {
                    if (organization == null) {
                        throw new IllegalArgumentException("Organization not found: " + organizationId);
                    }
                    Future<Organization> ret;
                    if (organization.getStorage() != null
                            && organization.getStorage().getStatus() != null
                            && organization.getStorage().getStatus().type() == DeploymentStatusType.READY) {
                        ret = Future.succeededFuture(organization);
                    } else {
                        ret = AzureUtil.toFuture(blobService.createBlobContainerIfNotExists(UI_CONTAINER), vertx)
                                       .compose(container -> {
                                           URI endpoint = URI.create(blobService.getAccountUrl());
                                           organization.setStorage(new OrganizationStorage()
                                                               .setAccountName(blobService.getAccountName())
                                                               .setBlobEndpoint(blobService.getAccountUrl())
                                                               .setPublishHost(endpoint.getHost())
                                                               .setStatus(new DeploymentStatus(DeploymentStatusType.READY)))
                                                       .setUpdated(new Date());
                                           log.debug("MockOrganizationStorageProvisioner pointed organization {} at {}",
                                                     organizationId, blobService.getAccountUrl());
                                           return organizationService.saveSync(organization);
                                       });
                    }
                    return ret;
                });
    }

    // Built on first use rather than at startup, so a server that never publishes a UI runs
    // without an Azurite configured
    private synchronized BlobServiceAsyncClient blobService() {
        if (blobService == null) {
            String connectionString = kinoticProperties.getSystemApi().getOrganizationStorage().getAzuriteConnectionString();
            Validate.notBlank(connectionString,
                              "kinotic.systemApi.organizationStorage.azuriteConnectionString is required when the provisioner is disabled");
            blobService = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildAsyncClient();
        }
        return blobService;
    }

}
