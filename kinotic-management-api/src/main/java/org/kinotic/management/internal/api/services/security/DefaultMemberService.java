package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.OrganizationMember;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.model.PendingInviteSummary;
import org.kinotic.management.api.services.security.MemberInvitationService;
import org.kinotic.management.api.services.security.MemberService;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class DefaultMemberService implements MemberService {

    private final SecurityContext securityContext;
    private final ParticipantIdentityService identityService;
    private final InviteService inviteService;
    private final MemberInvitationService memberInvitationService;

    @Override
    public Future<Page<OrganizationMember>> findMembers(Pageable pageable) {
        return identityService.findOrganizationMembers(requireOrgParticipant().getOrganizationId(), pageable);
    }

    @Override
    public Future<Page<OrganizationMember>> searchMembers(String searchText, Pageable pageable) {
        return identityService.searchOrganizationMembers(searchText, requireOrgParticipant().getOrganizationId(), pageable);
    }

    // scope-composed queries, so a foreign or unknown application matches nothing
    @Override
    public Future<Page<ApplicationParticipantIdentity>> findApplicationUsers(String applicationId, Pageable pageable) {
        return identityService.findApplicationUsers(requireOrgParticipant().getOrganizationId(), applicationId, pageable);
    }

    @Override
    public Future<Page<ApplicationParticipantIdentity>> searchApplicationUsers(String searchText, String applicationId, Pageable pageable) {
        return identityService.searchApplicationUsers(searchText, requireOrgParticipant().getOrganizationId(), applicationId, pageable);
    }

    @Override
    public Future<OrganizationMember> addMember(String email, String displayName, boolean sendInvitation) {
        return memberInvitationService.addMember(email, displayName, sendInvitation, null);
    }

    @Override
    public Future<Void> sendInvitation(String email) {
        return memberInvitationService.sendInvitation(email, null);
    }

    @Override
    public Future<PendingInviteSummary> inviteMember(String email, String displayName, String applicationId) {
        return memberInvitationService.inviteMember(email, displayName, applicationId, null)
                                      .map(PendingInviteSummary::from);
    }

    @Override
    public Future<Void> setMemberEnabled(String identityId, boolean enabled) {
        return loadOwnedMember(identityId, requireOrgParticipant())
                // saveSync so the console's immediate re-query sees the change
                .compose(member -> identityService.saveSync(member.setEnabled(enabled)).mapEmpty());
    }

    @Override
    public Future<Void> removeMember(String identityId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwnedMember(identityId, participant)
                // cascades the credential, the bindings and the delegates; sync so the console's immediate re-query
                // no longer shows the member
                .compose(member -> identityService.deleteByIdSync(member.getId()).map(member))
                // an organization's member goes with the invitation they never accepted
                .compose(member -> member instanceof OrganizationParticipantIdentity user
                        ? inviteService.findPendingInvite(user.getEmail(), participant.getOrganizationId())
                                       .compose(invite -> invite == null
                                               ? Future.<Void>succeededFuture()
                                               : inviteService.cancelInvite(invite.getId(), participant.getOrganizationId()))
                        : Future.<Void>succeededFuture());
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
     * Loads a member of the participant's organization for mutation: a user of it, or a user of one of its
     * applications. Rejects acting on the caller themselves, so an admin can't
     * disable or remove themselves out of the org.
     */
    private Future<UserParticipantIdentity> loadOwnedMember(String identityId, OrganizationParticipant participant) {
        Validate.notBlank(identityId, "identityId is required");
        if (identityId.equals(participant.getId())) {
            return Future.failedFuture(
                    new IllegalArgumentException("You cannot perform this action on yourself."));
        }
        // a delegate id gets the same not-found as a foreign one — member management
        // operates on people; delegates are managed by their owner's revocation flow
        return identityService.findById(identityId)
                .map(identity -> DomainUtil.requireOwned(identity, UserParticipantIdentity.class,
                        found -> (found instanceof OrganizationParticipantIdentity member
                                  && participant.getOrganizationId().equals(member.getOrganizationId()))
                                 || (found instanceof ApplicationParticipantIdentity application
                                     && participant.getOrganizationId().equals(application.getOrganizationId())),
                        "Member not found."));
    }
}
