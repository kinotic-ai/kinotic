package org.kinotic.domain.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.PendingInvite;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.iam.IamUserService;
import org.kinotic.domain.api.services.iam.InviteService;
import org.kinotic.domain.internal.api.repositories.PendingInviteRepository;
import org.kinotic.domain.internal.api.services.EmailService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultInviteService implements InviteService {

    private static final long INVITE_TTL_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    private final PendingInviteRepository pendingInviteRepository;
    private final IamUserService iamUserService;
    private final OrganizationService organizationService;
    private final EmailService emailService;

    @Override
    public CompletableFuture<PendingInvite> createInvite(PendingInvite invite) {
        Validate.notBlank(invite.getEmail(), "email is required");
        Validate.notBlank(invite.getOrganizationId(), "organizationId is required");
        Validate.notNull(invite.getAuthType(), "authType is required");
        if (invite.getAuthType() == AuthType.OIDC) {
            Validate.notBlank(invite.getOidcConfigId(), "oidcConfigId is required for an OIDC invite");
        }

        return iamUserService.findByEmail(invite.getEmail(), invite.getOrganizationId(), invite.getApplicationId())
                .thenCompose(existingUser -> {
                    if (existingUser != null) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException(
                                "A member with this email already exists in this scope."));
                    }
                    return pendingInviteRepository.findByEmailAndScope(
                            invite.getEmail(), invite.getOrganizationId(), invite.getApplicationId());
                })
                .thenCompose(existingInvite -> {
                    if (existingInvite != null) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException(
                                "An invitation is already pending for this email."));
                    }
                    Date now = new Date();
                    invite.setId(UUID.randomUUID().toString())
                          .setVerificationToken(UUID.randomUUID().toString())
                          .setCreated(now)
                          .setExpiresAt(new Date(now.getTime() + INVITE_TTL_MS));
                    return organizationService.findById(invite.getOrganizationId())
                            .thenCompose(org -> {
                                String orgName = org != null ? org.getName() : invite.getOrganizationId();
                                return pendingInviteRepository.save(invite)
                                        .thenCompose(saved -> emailService.sendInviteEmail(
                                                saved.getEmail(), saved.getDisplayName(),
                                                saved.getVerificationToken(), orgName)
                                                .thenApply(v -> saved));
                            });
                });
    }

    @Override
    public CompletableFuture<PendingInvite> getValidInvite(String token) {
        Validate.notBlank(token, "token is required");
        return pendingInviteRepository.findValidByToken(token);
    }

    @Override
    public CompletableFuture<IamUser> acceptLocalInvite(String token, String password) {
        Validate.notBlank(token, "token is required");
        Validate.notBlank(password, "Password is required");
        return acceptInvite(token, password, this::baseUser);
    }

    @Override
    public CompletableFuture<IamUser> acceptOidcInvite(String token, String oidcSubject, String oidcConfigId, String verifiedEmail) {
        Validate.notBlank(token, "token is required");
        Validate.notBlank(oidcSubject, "oidcSubject is required");
        Validate.notBlank(oidcConfigId, "oidcConfigId is required");
        Validate.notBlank(verifiedEmail, "verifiedEmail is required");
        return acceptInvite(token, null, invite -> {
            if (invite.getAuthType() != AuthType.OIDC || !oidcConfigId.equals(invite.getOidcConfigId())) {
                throw new IllegalArgumentException("This invitation cannot be accepted with this provider.");
            }
            if (!verifiedEmail.equalsIgnoreCase(invite.getEmail())) {
                throw new IllegalArgumentException("The signed-in email does not match the invitation.");
            }
            return baseUser(invite).setOidcSubject(oidcSubject).setOidcConfigId(oidcConfigId);
        });
    }

    @Override
    public CompletableFuture<Page<PendingInvite>> findPendingInvites(String organizationId, String applicationId, Pageable pageable) {
        Validate.notBlank(organizationId, "organizationId is required");
        return pendingInviteRepository.findByScope(organizationId, applicationId, pageable);
    }

    @Override
    public CompletableFuture<Void> cancelInvite(String inviteId, String organizationId) {
        Validate.notBlank(inviteId, "inviteId is required");
        Validate.notBlank(organizationId, "organizationId is required");
        return pendingInviteRepository.findById(inviteId).thenCompose(invite -> {
            if (invite == null || !organizationId.equals(invite.getOrganizationId())) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Invitation not found."));
            }
            return pendingInviteRepository.deleteById(inviteId);
        });
    }

    /**
     * Loads and validates the invitation, lets {@code userBuilder} produce the unsaved member from it
     * (also the point where OIDC identity/email assertions run and may throw), creates the member via
     * the shared {@link IamUserService#createUser} path, then deletes the consumed invitation.
     */
    private CompletableFuture<IamUser> acceptInvite(String token, String password, Function<PendingInvite, IamUser> userBuilder) {
        return pendingInviteRepository.findValidByToken(token).thenCompose(invite -> {
            IamUser user;
            try {
                user = userBuilder.apply(invite);
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
            return iamUserService.createUser(user, password)
                    .thenCompose(saved -> pendingInviteRepository.deleteById(invite.getId()).thenApply(v -> saved));
        });
    }

    /** Builds an unsaved {@link IamUser} carrying the invitation's email, name, auth method, and scope. */
    private IamUser baseUser(PendingInvite invite) {
        return new IamUser()
                .setEmail(invite.getEmail())
                .setDisplayName(invite.getDisplayName())
                .setAuthType(invite.getAuthType())
                .setOrganizationId(invite.getOrganizationId())
                .setApplicationId(invite.getApplicationId());
    }
}
