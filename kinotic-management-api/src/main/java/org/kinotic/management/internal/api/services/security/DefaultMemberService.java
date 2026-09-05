package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.PendingInvite;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.management.api.model.security.PendingInviteSummary;
import org.kinotic.management.api.services.security.MemberService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultMemberService implements MemberService {

    private final SecurityContext securityContext;
    private final ParticipantIdentityService identityService;
    private final InviteService inviteService;
    private final ApplicationRepository applicationRepository;

    @Override
    public Future<Page<UserParticipantIdentity>> findMembers(String applicationId, Pageable pageable) {
        OrganizationParticipant participant = requireOrgParticipant();
        // scope-composed query: a foreign or unknown application matches nothing
        return identityService.findUsersByScope(participant.getOrganizationId(), applicationId, pageable);
    }

    @Override
    public Future<Page<UserParticipantIdentity>> searchMembers(String searchText, String applicationId, Pageable pageable) {
        OrganizationParticipant participant = requireOrgParticipant();
        return identityService.searchUsersByScope(searchText,
                                                  participant.getOrganizationId(),
                                                  applicationId,
                                                  pageable);
    }

    @Override
    public Future<PendingInviteSummary> inviteMember(String email, String displayName, String applicationId) {
        Validate.notBlank(email, "email is required");
        OrganizationParticipant participant = requireOrgParticipant();
        return requireOwnedApplication(applicationId, participant.getOrganizationId())
                .compose(app -> {
                    PendingInvite invite = new PendingInvite()
                            .setEmail(email)
                            .setDisplayName(displayName)
                            .setOrganizationId(participant.getOrganizationId())
                            .setApplicationId(applicationId)
                            .setInvitedById(participant.getId())
                            .setInvitedByName(participantDisplayName(participant));
                    return inviteService.createInvite(invite);
                })
                .map(PendingInviteSummary::from);
    }

    @Override
    public Future<Void> setMemberEnabled(String identityId, boolean enabled) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwnedMember(identityId, participant)
                // saveSync so the console's immediate re-query sees the change
                .compose(user -> identityService.saveSync(user.setEnabled(enabled)))
                .mapEmpty();
    }

    @Override
    public Future<Void> removeMember(String identityId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwnedMember(identityId, participant)
                // Cascades the IdentityCredential; sync so the console's immediate re-query
                // no longer shows the member.
                .compose(user -> identityService.deleteByIdSync(user.getId()));
    }

    @Override
    public Future<Page<PendingInviteSummary>> findPendingInvites(String applicationId, Pageable pageable) {
        OrganizationParticipant participant = requireOrgParticipant();
        return inviteService.findPendingInvites(participant.getOrganizationId(),
                                                applicationId,
                                                pageable)
                .map(page -> new Page<>(page.getContent().stream()
                                            .map(PendingInviteSummary::from)
                                            .toList(),
                                        page.getTotalElements()));
    }

    @Override
    public Future<Void> cancelInvite(String inviteId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return inviteService.cancelInvite(inviteId, participant.getOrganizationId());
    }

    private OrganizationParticipant requireOrgParticipant() {
        // ApplicationParticipant is a sibling type, so app end-users are rejected here.
        return securityContext.requireParticipant(OrganizationParticipant.class);
    }

    /**
     * The write-path guard: an invitation persists a reference to an application, so the
     * application must exist in the participant's org. Completes with null for an org-member
     * invite (no applicationId).
     */
    private Future<Application> requireOwnedApplication(String applicationId, String organizationId) {
        Future<Application> ret;
        if (applicationId == null) {
            ret = Future.succeededFuture();
        } else {
            ret = applicationRepository.requireById(applicationId, organizationId);
        }
        return ret;
    }

    /**
     * Loads a member of the participant's organization for mutation. Rejects acting on the
     * caller's own account, so an admin can't disable or remove themselves out of the org.
     */
    private Future<UserParticipantIdentity> loadOwnedMember(String identityId, OrganizationParticipant participant) {
        Validate.notBlank(identityId, "identityId is required");
        if (identityId.equals(participant.getId())) {
            return Future.failedFuture(
                    new IllegalArgumentException("You cannot perform this action on your own account."));
        }
        return identityService.findById(identityId)
                // a delegate id gets the same not-found as a foreign one — member management
                // operates on people; delegates are managed by their owner's revocation flow
                .map(identity -> DomainUtil.requireOwned(identity, UserParticipantIdentity.class,
                        user -> participant.getOrganizationId().equals(user.getOrganizationId()),
                        "Member not found."));
    }

    private static String participantDisplayName(OrganizationParticipant participant) {
        if (participant.getMetadata() != null) {
            String name = participant.getMetadata().get("displayName");
            if (name != null && !name.isBlank()) {
                return name;
            }
            String email = participant.getMetadata().get("email");
            if (email != null && !email.isBlank()) {
                return email;
            }
        }
        return participant.getId();
    }
}
