package org.kinotic.os.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.Project;
import org.kinotic.domain.api.model.security.UserParticipantIdentity;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.os.api.model.security.PendingInviteSummary;

import java.util.concurrent.CompletableFuture;

/**
 * Cross-organization reads for platform operators. Published in the system zone, which only
 * SYSTEM participants may address, so every method takes the target organization explicitly
 * instead of resolving it from the caller's scope.
 */
@Publish
@Zone(DomainUtil.SYSTEM_ZONE)
public interface SystemOrganizationService {

    /**
     * Returns every organization on the platform.
     *
     * @param pageable the page settings to use
     * @return a page of {@link Organization}s
     */
    CompletableFuture<Page<Organization>> findOrganizations(Pageable pageable);

    /**
     * Searches every organization on the platform.
     *
     * @param searchText the text to match organizations against
     * @param pageable the page settings to use
     * @return a page of matching {@link Organization}s
     */
    CompletableFuture<Page<Organization>> searchOrganizations(String searchText, Pageable pageable);

    /**
     * Returns the organization with the given id, or null if none exists.
     *
     * @param organizationId the id of the organization to return
     * @return the {@link Organization}, or null when not found
     */
    CompletableFuture<Organization> findOrganizationById(String organizationId);

    /**
     * Counts every organization on the platform.
     *
     * @return the number of organizations
     */
    CompletableFuture<Long> countOrganizations();

    /**
     * Returns the applications owned by the given organization.
     *
     * @param organizationId the organization whose applications to return
     * @param pageable the page settings to use
     * @return a page of the organization's {@link Application}s
     */
    CompletableFuture<Page<Application>> findApplications(String organizationId, Pageable pageable);

    /**
     * Returns the projects owned by the given organization, across all of its applications.
     *
     * @param organizationId the organization whose projects to return
     * @param pageable the page settings to use
     * @return a page of the organization's {@link Project}s
     */
    CompletableFuture<Page<Project>> findProjects(String organizationId, Pageable pageable);

    /**
     * Returns the given organization's members, or one application's members when
     * {@code applicationId} is set.
     *
     * @param organizationId the organization whose members to return
     * @param applicationId the application to narrow to, or null for organization members
     * @param pageable the page settings to use
     * @return a page of matching {@link UserParticipantIdentity}s
     */
    CompletableFuture<Page<UserParticipantIdentity>> findMembers(String organizationId,
                                                                 String applicationId,
                                                                 Pageable pageable);

    /**
     * Searches the given organization's members, or one application's members when
     * {@code applicationId} is set.
     *
     * @param searchText the text to match members against
     * @param organizationId the organization whose members to search
     * @param applicationId the application to narrow to, or null for organization members
     * @param pageable the page settings to use
     * @return a page of matching {@link UserParticipantIdentity}s
     */
    CompletableFuture<Page<UserParticipantIdentity>> searchMembers(String searchText,
                                                                   String organizationId,
                                                                   String applicationId,
                                                                   Pageable pageable);

    /**
     * Returns the given organization's pending invitations, or one application's when
     * {@code applicationId} is set.
     *
     * @param organizationId the organization whose invitations to return
     * @param applicationId the application to narrow to, or null for organization invitations
     * @param pageable the page settings to use
     * @return a page of {@link PendingInviteSummary}s
     */
    CompletableFuture<Page<PendingInviteSummary>> findPendingInvites(String organizationId,
                                                                     String applicationId,
                                                                     Pageable pageable);
}
