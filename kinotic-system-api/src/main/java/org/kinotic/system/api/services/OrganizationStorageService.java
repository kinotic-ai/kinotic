package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.DeploymentStatusType;
import org.kinotic.domain.api.model.Organization;

import java.time.Duration;
import java.util.Set;

/**
 * The data plane of an organization's storage: what the platform does with the blobs in the
 * {@code ui} container of an organization whose storage is
 * {@link org.kinotic.domain.api.model.DeploymentStatusType#READY}. Prefixes are paths
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
     * Deletes every blob under the prefix that a publish of another commit wrote: the publish
     * workload stamps each blob with its commit, and the blobs of the given commits stay.
     *
     * @param organization the organization whose storage to clean
     * @param prefix       the UI's prefix, from {@link UiStoragePaths#uiPrefix}
     * @param commits      the commits whose files stay
     * @return a future completing once the other commits' files are gone
     */
    Future<Void> deleteFilesOfOtherCommits(Organization organization, String prefix, Set<String> commits);

    /**
     * Deletes every blob under the prefix. Nothing under it is not a failure.
     */
    Future<Void> deletePrefix(Organization organization, String prefix);

}
