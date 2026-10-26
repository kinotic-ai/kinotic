package org.kinotic.management.api.services.security;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.EmailTemplate;
import org.kinotic.management.api.model.AddApplicationUserRequest;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;

/**
 * Adds people to the calling organization user's organization and its applications, naming that user as each
 * invitation's sender. Every method requires an {@link org.kinotic.domain.api.model.security.participant.OrganizationParticipant}
 * on the call. Each invitation is emailed with the {@link EmailTemplate} passed, or Kinotic's own when it is
 * {@code null}.
 */
public interface MemberInvitationService {

    /**
     * {@link MemberService#addOrganizationUser}, emailing any invitation as {@code emailTemplate}.
     *
     * @return the member as listed, pending until they first sign in
     * @throws IllegalArgumentException when an invitation is emailed and {@code emailTemplate} is not valid, as
     *                                  {@link EmailTemplate} describes
     */
    Future<OrganizationParticipantIdentity> addOrganizationUser(String email, String displayName, boolean sendInvitation,
                                                                EmailTemplate emailTemplate);

    /**
     * {@link MemberService#addApplicationUser}, emailing any invitation as {@code emailTemplate}.
     *
     * @return the user as listed, pending until they first sign in
     * @throws IllegalArgumentException when an invitation is emailed and {@code emailTemplate} is not valid, as
     *                                  {@link EmailTemplate} describes
     */
    Future<ApplicationParticipantIdentity> addApplicationUser(AddApplicationUserRequest request, EmailTemplate emailTemplate);

    /**
     * {@link MemberService#sendInvitation}, emailing the invitation as {@code emailTemplate}.
     *
     * @throws IllegalArgumentException when {@code emailTemplate} is not valid, as {@link EmailTemplate} describes
     */
    Future<Void> sendInvitation(String email, String applicationId, EmailTemplate emailTemplate);
}
