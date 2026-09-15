package org.kinotic.system.internal.api.services;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.file.datalake.DataLakeDirectoryAsyncClient;
import com.azure.storage.file.datalake.DataLakeServiceAsyncClient;
import com.azure.storage.file.datalake.DataLakeServiceClientBuilder;
import com.azure.storage.file.datalake.sas.DataLakeServiceSasSignatureValues;
import com.azure.storage.file.datalake.sas.PathSasPermission;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.config.UiDeploymentProperties;
import org.kinotic.system.api.services.SiteStorageService;
import org.kinotic.system.api.services.UiStoragePaths;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Data Lake SDK backed {@link SiteStorageService}. Reaches the sites account at its configured
 * blob endpoint as the server's Azure identity, which signs each URL with a user delegation
 * key for the site's directory alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AzureSiteStorageService implements SiteStorageService {

    /** How far in the past a delegation key starts, so clock skew between server and storage never rejects a fresh SAS. */
    private static final Duration KEY_START_SKEW = Duration.ofMinutes(5);

    private final Vertx vertx;
    private final KinoticSystemApiProperties kinoticProperties;
    private final TokenCredential credential = new DefaultAzureCredentialBuilder().build();
    // Built on first use, so a server that never publishes a UI opens no storage client
    private volatile DataLakeServiceAsyncClient directories;

    @Override
    public Future<String> issueUploadUrl(String hostname, Duration ttl) {
        // list and delete within the directory let the workload clear the files of other commits
        return issue(hostname, ttl, new PathSasPermission().setCreatePermission(true)
                                                           .setWritePermission(true)
                                                           .setListPermission(true)
                                                           .setDeletePermission(true));
    }

    @Override
    public Future<String> issueRemovalUrl(String hostname, Duration ttl) {
        return issue(hostname, ttl, new PathSasPermission().setListPermission(true).setDeletePermission(true));
    }

    private Future<String> issue(String hostname, Duration ttl, PathSasPermission permission) {
        Validate.notNull(ttl, "ttl is required");
        String directory = UiStoragePaths.sitePrefix(hostname);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiry = now.plus(ttl);
        DataLakeDirectoryAsyncClient site = directories().getFileSystemAsyncClient(UiStoragePaths.SITES_CONTAINER)
                                                         .getDirectoryAsyncClient(directory);
        DataLakeServiceSasSignatureValues values = new DataLakeServiceSasSignatureValues(expiry, permission);
        return Future.fromCompletionStage(directories().getUserDelegationKey(now.minus(KEY_START_SKEW), expiry)
                                                       .map(key -> site.generateUserDelegationSas(values, key))
                                                       .toFuture(), vertx.getOrCreateContext())
                     // the workloads act through the blob endpoint, which honors the directory SAS
                     .map(token -> blobEndpoint() + "/" + UiStoragePaths.SITES_CONTAINER + "/" + directory + "?" + token);
    }

    private String blobEndpoint() {
        String endpoint = properties().getSitesStorageEndpoint();
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    // The Data Lake client signs the directory SAS; it takes the blob endpoint and derives the
    // account's dfs endpoint from it
    private DataLakeServiceAsyncClient directories() {
        DataLakeServiceAsyncClient ret = directories;
        if (ret == null) {
            ret = new DataLakeServiceClientBuilder().endpoint(properties().getSitesStorageEndpoint())
                                                    .credential(credential)
                                                    .buildAsyncClient();
            directories = ret;
        }
        return ret;
    }

    private UiDeploymentProperties properties() {
        return kinoticProperties.getSystemApi().getUiDeployment();
    }

}
