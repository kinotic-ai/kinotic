package org.kinotic.os.internal.api.services.security;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.security.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.PendingInvite;
import org.kinotic.domain.api.model.security.OrganizationParticipant;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.os.api.model.security.PendingInviteSummary;
import org.kinotic.os.api.services.security.MemberService;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DefaultMemberService implements MemberService {

    private final SecurityContext securityContext;
    private final ParticipantIdentityService identityService;
    private final InviteService inviteService;
    private final ApplicationRepository applicationRepository;

    @Override
    public CompletableFuture<Page<UserParticipantIdentity>> findMembers(String applicationId, Pageable pageable) {
        OrganizationParticipant participant = requireOrgParticipant();
        return requireOwnedApplication(applicationId, participant.getOrganizationId())
                .thenCompose(app -> identityService.findUsersByScope(participant.getOrganizationId(),
                                                               applicationId,
                                                               pageable));
    }

    @Override
    public CompletableFuture<Page<UserParticipantIdentity>> searchMembers(String searchText, String applicationId, Pageable pageable) {
        OrganizationParticipant participant = requireOrgParticipant();
        return requireOwnedApplication(applicationId, participant.getOrganizationId())
                .thenCompose(app -> identityService.searchUsersByScope(searchText,
                                                                 participant.getOrganizationId(),
                                                                 applicationId,
                                                                 pageable));
    }

    @Override
    public CompletableFuture<PendingInviteSummary> inviteMember(String email, String displayName, String applicationId) {
        Validate.notBlank(email, "email is required");
        OrganizationParticipant participant = requireOrgParticipant();
        return requireOwnedApplication(applicationId, participant.getOrganizationId())
                .thenCompose(app -> {
                    PendingInvite invite = new PendingInvite()
                            .setEmail(email)
                            .setDisplayName(displayName)
                            .setOrganizationId(participant.getOrganizationId())
                            .setApplicationId(applicationId)
                            .setInvitedById(participant.getId())
                            .setInvitedByName(participantDisplayName(participant));
                    return inviteService.createInvite(invite);
                })
                .thenApply(DefaultMemberService::toSummary);
    }

    @Override
    public CompletableFuture<Void> setMemberEnabled(String identityId, boolean enabled) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwnedMember(identityId, participant)
                // saveSync so the console's immediate re-query sees the change
                .thenCompose(user -> identityService.saveSync(user.setEnabled(enabled)))
                .thenApply(u -> null);
    }

    @Override
    public CompletableFuture<Void> removeMember(String identityId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwnedMember(identityId, participant)
                // Cascades the IdentityCredential; sync so the console's immediate re-query
                // no longer shows the member.
                .thenCompose(user -> identityService.deleteByIdSync(user.getId()));
    }

    @Override
    public CompletableFuture<Page<PendingInviteSummary>> findPendingInvites(String applicationId, Pageable pageable) {
        OrganizationParticipant participant = requireOrgParticipant();
        return requireOwnedApplication(applicationId, participant.getOrganizationId())
                .thenCompose(app -> inviteService.findPendingInvites(participant.getOrganizationId(),
                                                                     applicationId,
                                                                     pageable))
                .thenApply(page -> new Page<>(page.getContent().stream()
                                                  .map(DefaultMemberService::toSummary)
                                                  .toList(),
                                              page.getTotalElements()));
    }

    @Override
    public CompletableFuture<Void> cancelInvite(String inviteId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return inviteService.cancelInvite(inviteId, participant.getOrganizationId());
    }

    private OrganizationParticipant requireOrgParticipant() {
        // ApplicationParticipant is a sibling type, so app end-users are rejected here.
        return securityContext.requireParticipant(OrganizationParticipant.class);
    }

    /**
     * When an applicationId is supplied, proves it belongs to the participant's org before any
     * scoped operation — the org-scoped lookup simply misses for a foreign app, blocking
     * cross-org application access. Completes with null for org scope.
     */
    private CompletableFuture<Application> requireOwnedApplication(String applicationId, String organizationId) {
        if (applicationId == null) {
            return CompletableFuture.completedFuture(null);
        }
        return applicationRepository.findById(applicationId, organizationId)
                .thenApply(app -> {
                    if (app == null) {
                        throw new IllegalArgumentException("Application not found: " + applicationId);
                    }
                    return app;
                });
    }

    /**
     * Loads a member of the participant's organization for mutation. Fails for unknown or
     * foreign users (same message — no existence oracle) and rejects acting on the caller's
     * own account, so an admin can't disable or remove themselves out of the org.
     */
    private CompletableFuture<UserParticipantIdentity> loadOwnedMember(String identityId, OrganizationParticipant participant) {
        Validate.notBlank(identityId, "identityId is required");
        if (identityId.equals(participant.getId())) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("You cannot perform this action on your own account."));
        }
        return identityService.findById(identityId)
                .thenApply(identity -> {
                    // same message for a delegate id as for a foreign one — member management
                    // operates on people; delegates are managed by their owner's revocation flow
                    if (!(identity instanceof UserParticipantIdentity user)
                            || !participant.getOrganizationId().equals(user.getOrganizationId())) {
                        throw new IllegalArgumentException("Member not found.");
                    }
                    return user;
                });
    }

    private static PendingInviteSummary toSummary(PendingInvite invite) {
        return new PendingInviteSummary()
                .setId(invite.getId())
                .setEmail(invite.getEmail())
                .setDisplayName(invite.getDisplayName())
                .setApplicationId(invite.getApplicationId())
                .setInvitedByName(invite.getInvitedByName())
                .setCreated(invite.getCreated())
                .setExpiresAt(invite.getExpiresAt());
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
