package org.kinotic.management.api.services.storage;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.file.datalake.DataLakeFileSystemAsyncClient;
import com.azure.storage.file.datalake.DataLakePathAsyncClient;
import com.azure.storage.file.datalake.DataLakeServiceAsyncClient;
import com.azure.storage.file.datalake.DataLakeServiceClientBuilder;
import com.azure.storage.file.datalake.sas.DataLakeServiceSasSignatureValues;
import com.azure.storage.file.datalake.sas.PathSasPermission;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.Validate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.function.Function;

/**
 * Issues the URLs of paths in one Azure storage account with a hierarchical namespace, each
 * carrying a SAS the server's Azure identity signs with a user delegation key for that path alone.
 * The URLs are on the account's blob endpoint, which honors a directory's SAS for every blob under
 * it.
 */
public class AzureStorageUrlIssuer {

    /** How far in the past a delegation key starts, so clock skew between server and storage never rejects a fresh SAS. */
    private static final Duration KEY_START_SKEW = Duration.ofMinutes(5);

    private final Vertx vertx;
    private final String blobEndpoint;
    private final TokenCredential credential = new DefaultAzureCredentialBuilder().build();
    // Built on first use, so a server that never issues a URL for the account opens no storage client
    private volatile DataLakeServiceAsyncClient client;

    /**
     * @param vertx        the Vert.x instance the issued futures complete on
     * @param blobEndpoint the account's blob endpoint, e.g. {@code https://stkinoticsites.blob.core.windows.net/}
     */
    public AzureStorageUrlIssuer(Vertx vertx, String blobEndpoint) {
        this.vertx = vertx;
        this.blobEndpoint = blobEndpoint.endsWith("/") ? blobEndpoint.substring(0, blobEndpoint.length() - 1) : blobEndpoint;
    }

    /**
     * Issues the URL of a directory, with a SAS granting the permission within it alone until
     * {@code ttl} has passed.
     */
    public Future<String> issueDirectoryUrl(String container, String directory, Duration ttl, PathSasPermission permission) {
        return issue(container, directory, ttl, permission, fileSystem -> fileSystem.getDirectoryAsyncClient(directory));
    }

    /**
     * Issues the URL of a file, with a SAS granting the permission on it alone until {@code ttl}
     * has passed.
     */
    public Future<String> issueFileUrl(String container, String file, Duration ttl, PathSasPermission permission) {
        return issue(container, file, ttl, permission, fileSystem -> fileSystem.getFileAsyncClient(file));
    }

    private Future<String> issue(String container,
                                 String path,
                                 Duration ttl,
                                 PathSasPermission permission,
                                 Function<DataLakeFileSystemAsyncClient, DataLakePathAsyncClient> pathOf) {
        Validate.notBlank(path, "path cannot be blank");
        Validate.notNull(ttl, "ttl is required");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiry = now.plus(ttl);
        DataLakePathAsyncClient target = pathOf.apply(client().getFileSystemAsyncClient(container));
        DataLakeServiceSasSignatureValues values = new DataLakeServiceSasSignatureValues(expiry, permission);
        return Future.fromCompletionStage(client().getUserDelegationKey(now.minus(KEY_START_SKEW), expiry)
                                                  .map(key -> target.generateUserDelegationSas(values, key))
                                                  .toFuture(), vertx.getOrCreateContext())
                     .map(token -> blobEndpoint + "/" + container + "/" + path + "?" + token);
    }

    // The Data Lake client signs the path SAS; it takes the blob endpoint and derives the
    // account's dfs endpoint from it
    private DataLakeServiceAsyncClient client() {
        DataLakeServiceAsyncClient ret = client;
        if (ret == null) {
            ret = new DataLakeServiceClientBuilder().endpoint(blobEndpoint)
                                                    .credential(credential)
                                                    .buildAsyncClient();
            client = ret;
        }
        return ret;
    }

}
