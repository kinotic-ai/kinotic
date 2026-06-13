package org.kinotic.domain.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.PendingInvite;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.iam.IamUserService;
import org.kinotic.domain.api.services.iam.InviteEmailMismatchException;
import org.kinotic.domain.api.services.iam.InviteService;
import org.kinotic.domain.internal.api.repositories.PendingInviteRepository;
import org.kinotic.domain.internal.api.services.EmailService;
import org.kinotic.domain.internal.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultInviteService implements InviteService {

    // Invites sit in inboxes, unlike the minutes/hours-lived sign-up tokens.
    private static final long INVITE_TTL_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    private final PendingInviteRepository pendingInviteRepository;
    private final IamUserService iamUserService;
    private final OrganizationService organizationService;
    private final EmailService emailService;

    @Override
    public CompletableFuture<PendingInvite> createInvite(PendingInvite invite) {
        Validate.notBlank(invite.getEmail(), "email is required");
        Validate.notBlank(invite.getOrganizationId(), "organizationId is required");
        Validate.notBlank(invite.getInvitedByName(), "invitedByName is required");
        invite.setEmail(DomainUtil.normalizeEmail(invite.getEmail()));

        return organizationService.findById(invite.getOrganizationId())
                .thenCompose(org -> {
                    if (org == null) {
                        return CompletableFuture.<Organization>failedFuture(
                                new IllegalArgumentException("Organization not found."));
                    }
                    return checkEmailAvailable(invite).thenApply(v -> org);
                })
                .thenCompose(org -> {
                    Date now = new Date();
                    invite.setId(UUID.randomUUID().toString())
                          .setVerificationToken(UUID.randomUUID().toString())
                          .setCreated(now)
                          .setExpiresAt(new Date(now.getTime() + INVITE_TTL_MS));
                    // Save before sending, mirroring sign-up: a failed send leaves a pending
                    // record that simply expires, never an emailed link with no record behind it.
                    // saveSync because the console re-queries pending invites as soon as this
                    // returns — a plain save races the ES index refresh and the new row is missed.
                    return pendingInviteRepository.saveSync(invite)
                            .thenCompose(saved -> emailService.sendInviteEmail(saved, org.getName())
                                                              .thenApply(v -> saved));
                });
    }

    /**
     * Scope-branched availability check. Org invites reject an email with an org-scope account
     * in ANY organization — org login resolves users by unscoped email lookups, so a second
     * org-scope row for the same email would make login nondeterministic. App login is fully
     * scoped, so app invites only check within the target application. Both also reject when
     * an invitation is already pending in the scope.
     */
    private CompletableFuture<Void> checkEmailAvailable(PendingInvite invite) {
        CompletableFuture<IamUser> existingUser = invite.getApplicationId() == null
                ? iamUserService.findFirstOrgUserByEmail(invite.getEmail())
                : iamUserService.findByEmail(invite.getEmail(),
                                             invite.getOrganizationId(),
                                             invite.getApplicationId());
        return existingUser
                .thenCompose(user -> {
                    if (user != null) {
                        return CompletableFuture.<PendingInvite>failedFuture(new IllegalArgumentException(
                                invite.getApplicationId() == null
                                        ? "This email already belongs to an organization."
                                        : "A user with this email already exists in this application."));
                    }
                    return pendingInviteRepository.findByEmailAndScope(invite.getEmail(),
                                                                       invite.getOrganizationId(),
                                                                       invite.getApplicationId());
                })
                .thenCompose(pending -> {
                    if (pending != null) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException(
                                "An invitation is already pending for this email."));
                    }
                    return CompletableFuture.completedFuture(null);
                });
    }

    @Override
    public CompletableFuture<PendingInvite> getValidInvite(String token) {
        Validate.notBlank(token, "token is required");
        return pendingInviteRepository.findValidByToken(token);
    }

    @Override
    public CompletableFuture<IamUser> acceptLocalInvite(String token, String password, String displayName) {
        Validate.notBlank(token, "token is required");
        Validate.notBlank(password, "password is required");

        return pendingInviteRepository.findValidByToken(token)
                .thenCompose(invite -> acceptInvite(invite, password, displayName, null, null));
    }

    @Override
    public CompletableFuture<IamUser> acceptOidcInvite(String token,
                                                       String oidcSubject,
                                                       String oidcConfigId,
                                                       String verifiedEmail) {
        Validate.notBlank(token, "token is required");
        Validate.notBlank(oidcSubject, "oidcSubject is required");
        Validate.notBlank(oidcConfigId, "oidcConfigId is required");
        Validate.notBlank(verifiedEmail, "verifiedEmail is required");

        return pendingInviteRepository.findValidByToken(token)
                .thenCompose(invite -> {
                    if (!invite.getEmail().equals(DomainUtil.normalizeEmail(verifiedEmail))) {
                        return CompletableFuture.failedFuture(new InviteEmailMismatchException(
                                "This invitation was sent to a different email address."));
                    }
                    return acceptInvite(invite, null, null, oidcSubject, oidcConfigId);
                });
    }

    /**
     * Single accept path for both methods: the member's scope comes entirely from the stored
     * invite, the auth type from whether a password is present (createUser auto-detects), and
     * the invite is consumed on success. A concurrent double-accept is safe — createUser's
     * unique-email-in-scope check fails the second attempt.
     */
    private CompletableFuture<IamUser> acceptInvite(PendingInvite invite,
                                                    String password,
                                                    String displayNameOverride,
                                                    String oidcSubject,
                                                    String oidcConfigId) {
        IamUser user = new IamUser()
                .setEmail(invite.getEmail())
                .setDisplayName(StringUtils.isNotBlank(displayNameOverride)
                                        ? displayNameOverride
                                        : invite.getDisplayName())
                .setOrganizationId(invite.getOrganizationId())
                .setApplicationId(invite.getApplicationId());
        if (oidcSubject != null) {
            user.setOidcSubject(oidcSubject)
                .setOidcConfigId(oidcConfigId);
        }
        return iamUserService.createUser(user, password)
                .thenCompose(saved -> pendingInviteRepository.deleteById(invite.getId())
                                                             .thenApply(v -> saved));
    }

    @Override
    public CompletableFuture<Page<PendingInvite>> findPendingInvites(String organizationId,
                                                                     String applicationId,
                                                                     Pageable pageable) {
        Validate.notBlank(organizationId, "organizationId is required");
        return pendingInviteRepository.findByScope(organizationId, applicationId, pageable);
    }

    @Override
    public CompletableFuture<Void> cancelInvite(String inviteId, String organizationId) {
        Validate.notBlank(inviteId, "inviteId is required");
        Validate.notBlank(organizationId, "organizationId is required");

        return pendingInviteRepository.findById(inviteId)
                .thenCompose(invite -> {
                    // The repository deleteById is unscoped; this load-and-assert is the
                    // security boundary keeping one org from cancelling another's invites.
                    // Same message for missing and foreign — no existence oracle.
                    if (invite == null || !organizationId.equals(invite.getOrganizationId())) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Invitation not found."));
                    }
                    // Sync delete so the console's immediate re-query no longer sees the row.
                    return pendingInviteRepository.deleteByIdSync(inviteId);
                });
    }
}
