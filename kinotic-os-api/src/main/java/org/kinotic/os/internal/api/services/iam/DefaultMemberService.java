package org.kinotic.os.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.OidcConfiguration;
import org.kinotic.domain.api.model.iam.PendingInvite;
import org.kinotic.domain.api.security.OrganizationParticipant;
import org.kinotic.domain.api.services.iam.IamUserService;
import org.kinotic.domain.api.services.iam.InviteService;
import org.kinotic.os.api.services.ApplicationService;
import org.kinotic.os.api.services.iam.InviteOptions;
import org.kinotic.os.api.services.iam.MemberService;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultMemberService implements MemberService {

    private final SecurityContext securityContext;
    private final IamUserService iamUserService;
    private final InviteService inviteService;
    private final OidcConfigurationService oidcConfigurationService;
    private final ApplicationService applicationService;

    @Override
    public CompletableFuture<Page<IamUser>> findMembers(String applicationId, Pageable pageable) {
        return iamUserService.findByScope(requireOrganizationId(), applicationId, pageable);
    }

    @Override
    public CompletableFuture<Page<IamUser>> searchMembers(String applicationId, String searchText, Pageable pageable) {
        return iamUserService.searchByScope(searchText, requireOrganizationId(), applicationId, pageable);
    }

    @Override
    public CompletableFuture<InviteOptions> inviteOptions(String applicationId) {
        return resolveScopeOidcConfig(applicationId)
                .thenApply(config -> new InviteOptions(true, config != null, config != null ? config.getName() : null));
    }

    @Override
    public CompletableFuture<PendingInvite> inviteMember(String applicationId, String email, String displayName, AuthType authType) {
        OrganizationParticipant participant = requireOrgParticipant();
        return resolveOidcConfigId(applicationId, authType)
                .thenCompose(oidcConfigId -> {
                    PendingInvite invite = new PendingInvite()
                            .setEmail(email)
                            .setDisplayName(displayName)
                            .setAuthType(authType)
                            .setOrganizationId(participant.getOrganizationId())
                            .setApplicationId(applicationId)
                            .setOidcConfigId(oidcConfigId)
                            .setInvitedBy(participant.getId());
                    return inviteService.createInvite(invite);
                });
    }

    @Override
    public CompletableFuture<IamUser> setMemberEnabled(String id, boolean enabled) {
        return loadOwnedMember(id).thenCompose(user -> iamUserService.save(user.setEnabled(enabled)));
    }

    @Override
    public CompletableFuture<Void> removeMember(String id) {
        return loadOwnedMember(id).thenCompose(user -> iamUserService.deleteById(user.getId()));
    }

    @Override
    public CompletableFuture<Page<PendingInvite>> findPendingInvites(String applicationId, Pageable pageable) {
        return inviteService.findPendingInvites(requireOrganizationId(), applicationId, pageable);
    }

    @Override
    public CompletableFuture<Void> cancelInvite(String inviteId) {
        return inviteService.cancelInvite(inviteId, requireOrganizationId());
    }

    private OrganizationParticipant requireOrgParticipant() {
        return securityContext.requireParticipant(OrganizationParticipant.class);
    }

    private String requireOrganizationId() {
        return requireOrgParticipant().getOrganizationId();
    }

    /**
     * Loads the member, asserting it belongs to the caller's organization and is not the caller's
     * own account — the latter prevents an administrator from disabling or removing themselves.
     */
    private CompletableFuture<IamUser> loadOwnedMember(String id) {
        OrganizationParticipant participant = requireOrgParticipant();
        if (id.equals(participant.getId())) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("You cannot modify your own membership."));
        }
        return iamUserService.findById(id).thenCompose(user -> {
            if (user == null || !participant.getOrganizationId().equals(user.getOrganizationId())) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Member not found."));
            }
            return CompletableFuture.completedFuture(user);
        });
    }

    /** Resolves the OIDC config id for an OIDC invite, or completes with null for a LOCAL invite. */
    private CompletableFuture<String> resolveOidcConfigId(String applicationId, AuthType authType) {
        if (authType != AuthType.OIDC) {
            return CompletableFuture.completedFuture(null);
        }
        return resolveScopeOidcConfig(applicationId).thenApply(config -> {
            if (config == null) {
                throw new IllegalArgumentException("This scope has no OIDC provider configured.");
            }
            return config.getId();
        });
    }

    /**
     * The scope's enabled OIDC configuration, or null when none: the organization's SSO config for
     * an organization-scope invite, or the application's first enabled config for an application-scope
     * invite.
     */
    private CompletableFuture<OidcConfiguration> resolveScopeOidcConfig(String applicationId) {
        String orgId = requireOrganizationId();
        if (applicationId == null) {
            return oidcConfigurationService.findOrgLoginConfig(orgId);
        }
        return applicationService.getOidcConfigurations(applicationId)
                .thenApply(configs -> configs.isEmpty() ? null : configs.get(0));
    }
}
