package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.model.AddApplicationUserRequest;
import org.kinotic.management.api.model.PendingInviteSummary;
import org.kinotic.management.api.services.security.MemberInvitationService;
import org.kinotic.management.api.services.security.MemberService;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@RequiredArgsConstructor
public class DefaultMemberService implements MemberService {

    private final SecurityContext securityContext;
    private final ParticipantIdentityService identityService;
    private final InviteService inviteService;
    private final MemberInvitationService memberInvitationService;
    private final RelationshipService relationships;

    @Override
    public Future<Page<OrganizationParticipantIdentity>> findMembers(Pageable pageable) {
        return identityService.findOrganizationUsers(requireOrgParticipant().getOrganizationId(), pageable);
    }

    @Override
    public Future<Page<OrganizationParticipantIdentity>> searchMembers(String searchText, Pageable pageable) {
        return identityService.searchOrganizationUsers(searchText, requireOrgParticipant().getOrganizationId(), pageable);
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
    public Future<OrganizationParticipantIdentity> addOrganizationUser(String email, String displayName, boolean sendInvitation) {
        return memberInvitationService.addOrganizationUser(email, displayName, sendInvitation, null);
    }

    @Override
    public Future<ApplicationParticipantIdentity> addApplicationUser(AddApplicationUserRequest request) {
        return memberInvitationService.addApplicationUser(request, null);
    }

    @Override
    public Future<Void> sendInvitation(String email, String applicationId) {
        return memberInvitationService.sendInvitation(email, applicationId, null);
    }

    @Override
    public Future<Void> setMemberEnabled(String identityId, boolean enabled) {
        return loadOwnedMember(identityId, requireOrgParticipant())
                .compose(member -> enabled ? Future.succeededFuture(member) : requireAnotherAdministrator(member))
                // saveSync so the console's immediate re-query sees the change
                .compose(member -> identityService.saveSync(member.setEnabled(enabled))
                                                  .compose(saved -> memberOfOrganization(member)));
    }

    // a disabled organization user is out of its organization's members, so nothing granted to the organization's
    // members reaches it
    private Future<Void> memberOfOrganization(UserParticipantIdentity member) {
        Future<Void> ret;
        if (member instanceof OrganizationParticipantIdentity organizationUser) {
            List<RelationshipTuple> tuples = List.of(AuthzGraphUtil.organizationMember(organizationUser.getId(), organizationUser.getOrganizationId()));
            ret = organizationUser.isEnabled()
                    ? relationships.ensure(AuthzStoreService.SYSTEM, tuples)
                    : relationships.remove(AuthzStoreService.SYSTEM, tuples);
        } else {
            ret = Future.succeededFuture();
        }
        return ret;
    }

    @Override
    public Future<Void> removeMember(String identityId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwnedMember(identityId, participant)
                .compose(this::requireAnotherAdministrator)
                // cascades the credential, the bindings and the delegates; sync so the console's immediate re-query
                // no longer shows the member
                .compose(member -> identityService.deleteByIdSync(member.getId()).map(member))
                // a member goes with the invitation they never accepted
                .compose(member -> inviteService.deletePendingInvite(member.getEmail(), participant.getOrganizationId(),
                                                                     member instanceof ApplicationParticipantIdentity user ? user.getApplicationId() : null));
    }

    // an organization user going out of the organization leaves it another administrator who can sign in
    private Future<UserParticipantIdentity> requireAnotherAdministrator(UserParticipantIdentity member) {
        Future<UserParticipantIdentity> ret;
        if (member instanceof OrganizationParticipantIdentity organizationUser) {
            ret = identityService.requireAnotherAdministrator(AuthzUtil.object(AuthzConstants.ORGANIZATION_TYPE, organizationUser.getOrganizationId()),
                                                              grant -> grant.subject().id().equals(organizationUser.getId()),
                                                              OrganizationParticipantIdentity.class)
                                 .map(member);
        } else {
            ret = Future.succeededFuture(member);
        }
        return ret;
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

    // a scope-composed query, so an invitation into a foreign or unknown application matches nothing
    @Override
    public Future<String> findInvitationLink(String email, String applicationId) {
        Validate.notBlank(email, "email is required");
        return inviteService.findAcceptUrl(email, requireOrgParticipant().getOrganizationId(), applicationId);
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
