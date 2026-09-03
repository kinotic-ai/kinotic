package org.kinotic.management.internal.api.services;

import com.azure.storage.blob.BlobServiceAsyncClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.OrganizationStorageStatus;
import org.kinotic.domain.api.model.OrganizationStorageStatusType;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.kinotic.management.api.config.OrganizationStorageProperties;
import org.kinotic.management.api.services.OrganizationStorageProvisioner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Date;

/**
 * Fallback {@link OrganizationStorageProvisioner} used when storage provisioning is disabled
 * ({@code kinotic.managementApi.organizationStorage.disableProvisioner=true}). Points every
 * organization at the one configured Azurite, creating the {@code ui} container there, so
 * deployments publish in development and tests without an Azure subscription.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "kinotic.managementApi.organizationStorage.disableProvisioner", havingValue = "true")
public class MockOrganizationStorageProvisioner implements OrganizationStorageProvisioner {

    private final OrganizationService organizationService;
    private final Vertx vertx;
    private final OrganizationStorageProperties properties;
    private BlobServiceAsyncClient blobService;

    public MockOrganizationStorageProvisioner(OrganizationService organizationService,
                                              Vertx vertx,
                                              KinoticManagementApiProperties properties) {
        this.organizationService = organizationService;
        this.vertx = vertx;
        this.properties = properties.getManagementApi().getOrganizationStorage();
    }

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
                    if (organization.getStorageStatus() != null
                            && organization.getStorageStatus().type() == OrganizationStorageStatusType.READY) {
                        ret = Future.succeededFuture(organization);
                    } else {
                        ret = Future.fromCompletionStage(blobService.createBlobContainerIfNotExists(UI_CONTAINER).toFuture(),
                                                         vertx.getOrCreateContext())
                                    .compose(container -> {
                                        URI endpoint = URI.create(blobService.getAccountUrl());
                                        organization.setStorageAccountName(blobService.getAccountName())
                                                    .setStorageBlobEndpoint(blobService.getAccountUrl())
                                                    .setStoragePrivateEndpointIp(endpoint.getHost())
                                                    .setStorageStatus(new OrganizationStorageStatus(OrganizationStorageStatusType.READY))
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
            Validate.notBlank(properties.getAzuriteConnectionString(),
                              "kinotic.managementApi.organizationStorage.azuriteConnectionString is required when the provisioner is disabled");
            blobService = new BlobServiceClientBuilder()
                    .connectionString(properties.getAzuriteConnectionString())
                    .buildAsyncClient();
        }
        return blobService;
    }

}
