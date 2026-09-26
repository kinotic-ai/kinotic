package org.kinotic.management.api.services.storage;

import io.vertx.core.Future;

import java.time.Duration;

/**
 * Issues the credentials workloads and browsers act on organizations' files with. The files live
 * in the organization storage account as {@link OrganizationStoragePaths} lays them out; the server
 * itself never reads, writes or deletes them.
 */
public interface OrganizationStorageService {

    /**
     * Issues the URL a workload writes one file through until {@code ttl} has passed: the file,
     * with a query carrying a SAS that allows creating and writing it alone.
     *
     * @param file the file, a path within the container
     * @param ttl  how long the SAS stays valid
     * @return a future emitting the write URL
     */
    Future<String> issueWriteUrl(String file, Duration ttl);

    /**
     * Issues the URL one file can be read from until {@code ttl} has passed: the file, with a
     * query carrying a SAS that allows reading it alone.
     *
     * @param file the file, a path within the container
     * @param ttl  how long the SAS stays valid
     * @return a future emitting the read URL
     */
    Future<String> issueReadUrl(String file, Duration ttl);

}
