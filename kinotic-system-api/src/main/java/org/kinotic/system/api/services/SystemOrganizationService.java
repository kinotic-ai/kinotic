package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.PendingInviteSummary;

/**
 * Cross-organization reads for system users. Published in the system zone, which only
 * SYSTEM participants may address, so every method takes the target organization explicitly
 * instead of resolving it from the caller's scope. A read of one organization is checked on that
 * organization, and a read across organizations on the system; a grant made on the system
 * reaches every organization on it.
 */
@Publish
@AuthzResource(value = AuthzConstants.ORGANIZATION_TYPE, parent = AuthzConstants.SYSTEM_TYPE)
public interface SystemOrganizationService {

    /**
     * Returns every organization on the platform.
     *
     * @param pageable the page settings to use
     * @return a page of {@link Organization}s
     */
    Future<Page<Organization>> findOrganizations(Pageable pageable);

    /**
     * Searches every organization on the platform.
     *
     * @param searchText the text to match organizations against
     * @param pageable the page settings to use
     * @return a page of matching {@link Organization}s
     */
    Future<Page<Organization>> searchOrganizations(String searchText, Pageable pageable);

    /**
     * Returns the organization with the given id, or null if none exists.
     *
     * @param organizationId the id of the organization to return
     * @return the {@link Organization}, or null when not found
     */
    Future<Organization> findOrganizationById(String organizationId);

    /**
     * Counts every organization on the platform.
     *
     * @return the number of organizations
     */
    Future<Long> countOrganizations();

    /**
     * Returns the applications owned by the given organization.
     *
     * @param organizationId the organization whose applications to return
     * @param pageable the page settings to use
     * @return a page of the organization's {@link Application}s
     */
    Future<Page<Application>> findApplications(String organizationId, Pageable pageable);

    /**
     * Returns the projects owned by the given organization, across all of its applications.
     *
     * @param organizationId the organization whose projects to return
     * @param pageable the page settings to use
     * @return a page of the organization's {@link Project}s
     */
    Future<Page<Project>> findProjects(String organizationId, Pageable pageable);

    /**
     * Returns the given organization's members.
     *
     * @param organizationId the organization whose members to return
     * @param pageable the page settings to use
     * @return a page of {@link OrganizationParticipantIdentity}s
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_MEMBERS)
    Future<Page<OrganizationParticipantIdentity>> findMembers(String organizationId, Pageable pageable);

    /**
     * Searches the given organization's members by email and display name.
     *
     * @param searchText the text to match members against
     * @param organizationId the organization whose members to search
     * @param pageable the page settings to use
     * @return a page of matching {@link OrganizationParticipantIdentity}s
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_MEMBERS)
    Future<Page<OrganizationParticipantIdentity>> searchMembers(String searchText, String organizationId, Pageable pageable);

    /**
     * Returns the users of one of the given organization's applications.
     *
     * @param organizationId the organization that owns the application
     * @param applicationId the application whose users to return
     * @param pageable the page settings to use
     * @return a page of the application's users
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_MEMBERS)
    Future<Page<ApplicationParticipantIdentity>> findApplicationUsers(String organizationId,
                                                       String applicationId,
                                                       Pageable pageable);

    /**
     * Searches the users of one of the given organization's applications by email and display name.
     *
     * @param searchText the text to match users against
     * @param organizationId the organization that owns the application
     * @param applicationId the application whose users to search
     * @param pageable the page settings to use
     * @return a page of the application's matching users
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_MEMBERS)
    Future<Page<ApplicationParticipantIdentity>> searchApplicationUsers(String searchText,
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
    @AuthzCheck(permission = AuthzConstants.CAN_READ_MEMBERS)
    Future<Page<PendingInviteSummary>> findPendingInvites(String organizationId,
                                                                     String applicationId,
                                                                     Pageable pageable);
}
