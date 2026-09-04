package org.kinotic.system.internal.api.services;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobServiceAsyncClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobListDetails;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /** The metadata the publish workload stamps every blob with: the commit it belongs to. */
    private static final String COMMIT_METADATA = "commit";

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
    public Future<Void> deleteFilesOfOtherCommits(Organization organization, String prefix, Set<String> commits) {
        Validate.notBlank(prefix, "prefix is required");
        Validate.notEmpty(commits, "commits is required");
        BlobContainerAsyncClient container = container(organization);
        ListBlobsOptions options = new ListBlobsOptions().setPrefix(prefix + "/")
                                                         .setDetails(new BlobListDetails().setRetrieveMetadata(true));
        return deleteAll(container, container.listBlobs(options).filter(item -> isDirectory(item) || !isOf(item, commits)));
    }

    // A blob without the stamp predates stamping, so no publish keeps it
    private static boolean isOf(BlobItem item, Set<String> commits) {
        String commit = item.getMetadata() == null ? null : item.getMetadata().get(COMMIT_METADATA);
        return commit != null && commits.contains(commit);
    }

    @Override
    public Future<Void> deletePrefix(Organization organization, String prefix) {
        Validate.notBlank(prefix, "prefix is required");
        BlobContainerAsyncClient container = container(organization);
        ListBlobsOptions options = new ListBlobsOptions().setPrefix(prefix)
                                                         .setDetails(new BlobListDetails().setRetrieveMetadata(true));
        return deleteAll(container, container.listBlobs(options));
    }

    // The account has a hierarchical namespace, so a flat listing includes directories, which
    // are deletable only once empty: the files go first, then the directories deepest first,
    // and a directory still holding a kept file is left where it is
    private Future<Void> deleteAll(BlobContainerAsyncClient container, Flux<BlobItem> items) {
        return AzureUtil.toFuture(items.collectList().flatMap(all -> {
            List<BlobItem> files = all.stream().filter(item -> !isDirectory(item)).toList();
            List<BlobItem> directories = all.stream()
                                            .filter(AzureOrganizationStorageService::isDirectory)
                                            .sorted(Comparator.comparingInt((BlobItem item) -> item.getName().length()).reversed())
                                            .toList();
            return Flux.fromIterable(files)
                       .flatMap(item -> container.getBlobAsyncClient(item.getName()).deleteIfExists(), DELETE_CONCURRENCY)
                       .thenMany(Flux.fromIterable(directories))
                       .concatMap(item -> container.getBlobAsyncClient(item.getName()).deleteIfExists()
                                                   .onErrorResume(error -> isNotEmpty(error), error -> Mono.empty()))
                       .then();
        }), vertx);
    }

    private static boolean isDirectory(BlobItem item) {
        return item.getMetadata() != null && "true".equals(item.getMetadata().get("hdi_isfolder"));
    }

    private static boolean isNotEmpty(Throwable error) {
        // a hierarchical namespace error the blob SDK's error codes do not name
        return error instanceof BlobStorageException storage && "DirectoryIsNotEmpty".equals(String.valueOf(storage.getErrorCode()));
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
