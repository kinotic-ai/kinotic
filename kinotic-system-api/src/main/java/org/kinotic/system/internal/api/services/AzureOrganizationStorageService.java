package org.kinotic.system.internal.api.services;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobServiceAsyncClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.sas.BlobContainerSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.OrganizationStorage;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.config.OrganizationStorageProperties;
import org.kinotic.system.api.services.OrganizationStorageProvisioner;
import org.kinotic.system.api.services.OrganizationStorageService;
import org.kinotic.system.api.services.UiStoragePaths;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blob-SDK backed {@link OrganizationStorageService}. Reaches each organization's account at
 * its recorded blob endpoint as the server's Azure identity, which signs upload URLs with a
 * user delegation key; while the provisioner is disabled every organization is reached through
 * the configured Azurite instead, with its shared key.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AzureOrganizationStorageService implements OrganizationStorageService {

    /** How far in the past a delegation key starts, so clock skew between server and storage never rejects a fresh SAS. */
    private static final Duration KEY_START_SKEW = Duration.ofMinutes(5);
    private static final int DELETE_CONCURRENCY = 8;

    private final Vertx vertx;
    private final KinoticSystemApiProperties kinoticProperties;
    private final TokenCredential credential = new DefaultAzureCredentialBuilder().build();
    private final Map<String, BlobServiceAsyncClient> clientsByEndpoint = new ConcurrentHashMap<>();

    @Override
    public Future<String> issueUploadUrl(Organization organization, String applicationId, Duration ttl) {
        Validate.notNull(ttl, "ttl is required");
        BlobServiceAsyncClient service = service(organization);
        BlobContainerAsyncClient container = service.getBlobContainerAsyncClient(OrganizationStorageProvisioner.UI_CONTAINER);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiry = now.plus(ttl);
        BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(
                expiry, new BlobContainerSasPermission().setCreatePermission(true).setWritePermission(true));
        Future<String> sas;
        if (azurite()) {
            // the connection string carries the shared key, which signs directly
            sas = Future.succeededFuture(container.generateSas(values));
        } else {
            sas = AzureUtil.toFuture(service.getUserDelegationKey(now.minus(KEY_START_SKEW), expiry)
                                            .map(key -> container.generateUserDelegationSas(values, key)), vertx);
        }
        return sas.map(token -> container.getBlobContainerUrl() + "/" + UiStoragePaths.applicationPrefix(applicationId)
                + "?" + token);
    }

    @Override
    public Future<List<String>> listCommitDirs(Organization organization, String uiPrefix) {
        Validate.notBlank(uiPrefix, "uiPrefix is required");
        String root = uiPrefix + "/";
        // a hierarchy listing returns the direct children: the commit directories as prefixes,
        // and index.html and version.json as blobs
        return AzureUtil.toFuture(container(organization).listBlobsByHierarchy("/", new ListBlobsOptions().setPrefix(root))
                                                         .filter(item -> Boolean.TRUE.equals(item.isPrefix()))
                                                         .map(item -> commitDir(root, item))
                                                         .collectList(), vertx);
    }

    private static String commitDir(String root, BlobItem item) {
        String name = item.getName();
        String relative = name.startsWith(root) ? name.substring(root.length()) : name;
        return relative.endsWith("/") ? relative.substring(0, relative.length() - 1) : relative;
    }

    @Override
    public Future<Void> deletePrefix(Organization organization, String prefix) {
        Validate.notBlank(prefix, "prefix is required");
        BlobContainerAsyncClient container = container(organization);
        return AzureUtil.toFuture(container.listBlobs(new ListBlobsOptions().setPrefix(prefix))
                                           .flatMap(item -> container.getBlobAsyncClient(item.getName()).deleteIfExists(),
                                                    DELETE_CONCURRENCY)
                                           .then(), vertx);
    }

    private BlobContainerAsyncClient container(Organization organization) {
        return service(organization).getBlobContainerAsyncClient(OrganizationStorageProvisioner.UI_CONTAINER);
    }

    private BlobServiceAsyncClient service(Organization organization) {
        OrganizationStorage storage = requireStorage(organization);
        return clientsByEndpoint.computeIfAbsent(storage.getAzureBlobEndpoint(), endpoint -> {
            BlobServiceClientBuilder builder = new BlobServiceClientBuilder();
            if (azurite()) {
                builder.connectionString(properties().getAzuriteConnectionString());
            } else {
                builder.endpoint(endpoint).credential(credential);
            }
            return builder.buildAsyncClient();
        });
    }

    private static OrganizationStorage requireStorage(Organization organization) {
        Validate.notNull(organization, "organization is required");
        Validate.isTrue(organization.getStorage() != null && organization.getStorage().getAzureBlobEndpoint() != null,
                        "Organization %s has no storage endpoint recorded", organization.getId());
        return organization.getStorage();
    }

    // The switch that selects MockOrganizationStorageProvisioner, which is what points organizations
    // at the Azurite; a profile layered on development keeps the connection string regardless
    private boolean azurite() {
        return properties().isDisableProvisioner();
    }

    private OrganizationStorageProperties properties() {
        return kinoticProperties.getSystemApi().getOrganizationStorage();
    }

}
