package org.kinotic.os.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.iam.BaseOidcConfiguration;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.PendingInvite;
import org.kinotic.domain.api.security.OrganizationParticipant;
import org.kinotic.domain.api.services.iam.IamUserService;
import org.kinotic.domain.api.services.iam.InviteService;
import org.kinotic.domain.api.services.iam.OrgSignupOidcConfigurationService;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.os.api.model.iam.InviteOptions;
import org.kinotic.os.api.model.iam.OidcProviderOption;
import org.kinotic.os.api.model.iam.PendingInviteSummary;
import org.kinotic.os.api.services.iam.MemberService;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DefaultMemberService implements MemberService {

    private final SecurityContext securityContext;
    private final IamUserService iamUserService;
    private final InviteService inviteService;
    private final OidcConfigurationService oidcConfigurationService;
    private final OrgSignupOidcConfigurationService orgSignupOidcConfigurationService;
    private final ApplicationRepository applicationRepository;

    @Override
    public CompletableFuture<Page<IamUser>> findMembers(String applicationId, Pageable pageable) {
        OrganizationParticipant participant = requireOrgParticipant();
        return requireOwnedApplication(applicationId, participant.getOrganizationId())
                .thenCompose(app -> iamUserService.findByScope(participant.getOrganizationId(),
                                                               applicationId,
                                                               pageable));
    }

    @Override
    public CompletableFuture<Page<IamUser>> searchMembers(String searchText, String applicationId, Pageable pageable) {
        OrganizationParticipant participant = requireOrgParticipant();
        return requireOwnedApplication(applicationId, participant.getOrganizationId())
                .thenCompose(app -> iamUserService.searchByScope(searchText,
                                                                 participant.getOrganizationId(),
                                                                 applicationId,
                                                                 pageable));
    }

    @Override
    public CompletableFuture<InviteOptions> inviteOptions(String applicationId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return requireOwnedApplication(applicationId, participant.getOrganizationId())
                .thenCompose(app -> resolveScopeProviders(participant.getOrganizationId(), app, applicationId))
                .thenApply(providers -> new InviteOptions(true, providers));
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
    public CompletableFuture<Void> setMemberEnabled(String userId, boolean enabled) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwnedMember(userId, participant)
                .thenCompose(user -> iamUserService.save(user.setEnabled(enabled)))
                .thenApply(u -> null);
    }

    @Override
    public CompletableFuture<Void> removeMember(String userId) {
        OrganizationParticipant participant = requireOrgParticipant();
        return loadOwnedMember(userId, participant)
                // deleteById cascades the IamCredential
                .thenCompose(user -> iamUserService.deleteById(user.getId()));
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
    private CompletableFuture<IamUser> loadOwnedMember(String userId, OrganizationParticipant participant) {
        Validate.notBlank(userId, "userId is required");
        if (userId.equals(participant.getId())) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("You cannot perform this action on your own account."));
        }
        return iamUserService.findById(userId)
                .thenApply(user -> {
                    if (user == null || !participant.getOrganizationId().equals(user.getOrganizationId())) {
                        throw new IllegalArgumentException("Member not found.");
                    }
                    return user;
                });
    }

    /**
     * The providers an invitee into the scope can accept with: for an org invite the platform
     * social providers plus the org's SSO config when one is set; for an app invite the
     * application's enabled OIDC configurations.
     */
    private CompletableFuture<List<OidcProviderOption>> resolveScopeProviders(String organizationId,
                                                                              Application app,
                                                                              String applicationId) {
        if (applicationId == null) {
            return orgSignupOidcConfigurationService.findAllEnabled()
                    .thenCombine(oidcConfigurationService.findOrgLoginConfig(organizationId),
                                 (social, sso) -> {
                                     List<OidcProviderOption> providers = new ArrayList<>(social.stream()
                                                                                                .map(DefaultMemberService::toOption)
                                                                                                .toList());
                                     if (sso != null && sso.isEnabled()) {
                                         providers.add(toOption(sso));
                                     }
                                     return providers;
                                 });
        }
        List<String> configIds = app.getOidcConfigurationIds();
        if (configIds == null || configIds.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return oidcConfigurationService.findEnabledByIds(configIds, organizationId)
                .thenApply(configs -> configs.stream()
                                             .map(DefaultMemberService::toOption)
                                             .toList());
    }

    private static OidcProviderOption toOption(BaseOidcConfiguration config) {
        return new OidcProviderOption(config.getId(), config.getName(), config.getProvider());
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
