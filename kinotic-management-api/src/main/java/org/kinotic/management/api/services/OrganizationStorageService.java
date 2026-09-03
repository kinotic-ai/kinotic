package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.Organization;

import java.time.Duration;
import java.util.List;

/**
 * The data plane of an organization's storage: what the platform does with the blobs in the
 * {@code ui} container of an organization whose storage is
 * {@link org.kinotic.domain.api.model.OrganizationStorageStatusType#READY}. Prefixes are paths
 * within that container, as {@link UiStoragePaths} builds them.
 */
public interface OrganizationStorageService {

    /**
     * Issues the URL a publish workload uploads an application's UIs through: the
     * organization's blob endpoint, the container and the application's prefix, and a query
     * carrying a container SAS that allows creating and writing blobs until {@code ttl} has
     * passed. The workload appends the UI, commit and file path before the query.
     *
     * @param organization  the organization whose storage to upload to
     * @param applicationId the application whose UIs are uploaded
     * @param ttl           how long the SAS stays valid
     * @return a future emitting the upload URL
     */
    Future<String> issueUploadUrl(Organization organization, String applicationId, Duration ttl);

    /**
     * Whether any blob exists under the prefix.
     */
    Future<Boolean> exists(Organization organization, String prefix);

    /**
     * Lists the commit directories published under a UI's prefix: the sha of every commit
     * whose assets are still stored.
     *
     * @param organization the organization whose storage to read
     * @param uiPrefix     the UI's prefix, from {@link UiStoragePaths#uiPrefix}
     * @return a future emitting the shas, empty when nothing is published
     */
    Future<List<String>> listCommitDirs(Organization organization, String uiPrefix);

    /**
     * Deletes every blob under the prefix. Nothing under it is not a failure.
     */
    Future<Void> deletePrefix(Organization organization, String prefix);

}
