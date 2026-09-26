package org.kinotic.management.api.services.storage;

import io.vertx.core.Future;

import java.time.Duration;

/**
 * Issues the credentials workloads and browsers act on organizations' files with. The files live
 * in the organization storage account, each organization's under its own directory; the server
 * itself never reads, writes or deletes them.
 */
public interface OrganizationStorageService {

    /**
     * Issues the URL a workload writes a project's SBOM through until {@code ttl} has passed: the
     * project's one SBOM file, with a query carrying a SAS that allows creating and writing that
     * file alone.
     *
     * @param organizationId the organization the project belongs to
     * @param projectId      the project
     * @param ttl            how long the SAS stays valid
     * @return a future emitting the write URL
     */
    Future<String> issueSbomWriteUrl(String organizationId, String projectId, Duration ttl);

    /**
     * Issues the URL a project's SBOM can be read from until {@code ttl} has passed: the project's
     * one SBOM file, with a query carrying a SAS that allows reading that file alone.
     *
     * @param organizationId the organization the project belongs to
     * @param projectId      the project
     * @param ttl            how long the SAS stays valid
     * @return a future emitting the read URL
     */
    Future<String> issueSbomReadUrl(String organizationId, String projectId, Duration ttl);

}
