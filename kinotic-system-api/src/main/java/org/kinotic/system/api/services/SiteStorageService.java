package org.kinotic.system.api.services;

import io.vertx.core.Future;

import java.time.Duration;

/**
 * Issues the credentials workloads act on one site's files with. A site's files live in the
 * platform's sites storage account under the site's hostname, as {@link UiStoragePaths} lays
 * them out; the server itself never reads, writes or deletes them.
 */
public interface SiteStorageService {

    /**
     * Issues the URL a publish workload publishes one site through: the site's directory in
     * the sites account, with a query carrying a SAS that allows creating, writing, listing
     * and deleting blobs within that directory alone until {@code ttl} has passed. The
     * workload appends each file's path before the query.
     *
     * @param hostname the site's hostname, which names its directory
     * @param ttl      how long the SAS stays valid
     * @return a future emitting the upload URL
     */
    Future<String> issueUploadUrl(String hostname, Duration ttl);

    /**
     * Issues the URL a removal workload deletes one site through: the site's directory in
     * the sites account, with a query carrying a SAS that allows listing and deleting within
     * that directory alone until {@code ttl} has passed.
     *
     * @param hostname the site's hostname, which names its directory
     * @param ttl      how long the SAS stays valid
     * @return a future emitting the removal URL
     */
    Future<String> issueRemovalUrl(String hostname, Duration ttl);

}
