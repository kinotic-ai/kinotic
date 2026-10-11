package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.PendingInvite;
import org.kinotic.domain.api.repositories.TenantRepository;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.exceptions.InviteEmailMismatchException;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.internal.api.repositories.PendingInviteRepository;
import org.kinotic.domain.internal.api.services.EmailService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.function.Predicate;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultInviteService implements InviteService {

    // Invites sit in inboxes, unlike the minutes/hours-lived sign-up tokens.
    private static final long INVITE_TTL_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    private final PendingInviteRepository pendingInviteRepository;
    private final ParticipantIdentityService identityService;
    private final OrganizationService organizationService;
    private final EmailService emailService;
    private final TenantRepository tenants;

    @Override
    public Future<PendingInvite> createInvite(PendingInvite invite) {
        Validate.notBlank(invite.getEmail(), "email is required");
        Validate.notBlank(invite.getOrganizationId(), "organizationId is required");
        Validate.notBlank(invite.getInvitedByName(), "invitedByName is required");
        invite.setEmail(DomainUtil.normalizeEmail(invite.getEmail()));

        return organizationService.findById(invite.getOrganizationId())
                .compose(org -> {
                    if (org == null) {
                        return Future.failedFuture(
                                new IllegalArgumentException("Organization not found."));
                    }
                    return requireTenant(invite).compose(v -> checkEmailAvailable(invite)).map(org);
                })
                .compose(org -> {
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
                            .compose(saved -> emailService.sendInviteEmail(saved, org.getName())
                                                          .map(saved));
                });
    }

    // An invite into a tenant names one of the application's tenants
    private Future<Void> requireTenant(PendingInvite invite) {
        Future<Void> ret;
        if (invite.getTenantId() == null) {
            ret = Future.succeededFuture();
        } else {
            Validate.notBlank(invite.getApplicationId(), "An invite into a tenant names the tenant's application");
            ret = tenants.findByTenantId(invite.getOrganizationId(), invite.getApplicationId(), invite.getTenantId()).map(tenant -> {
                if (tenant == null) {
                    throw new IllegalArgumentException("No tenant of the application has id " + invite.getTenantId());
                }
                return null;
            });
        }
        return ret;
    }

    /**
     * Scope-branched availability check. Org invites reject an email with an org-scope account
     * in ANY organization — org login resolves users by unscoped email lookups, so a second
     * org-scope row for the same email would make login nondeterministic. App login is fully
     * scoped, so app invites only check within the target application. Both also reject when
     * an invitation is already pending in the scope.
     */
    private Future<Void> checkEmailAvailable(PendingInvite invite) {
        Future<UserParticipantIdentity> existingUser = invite.getApplicationId() == null
                ? identityService.findFirstOrgUserByEmail(invite.getEmail())
                : identityService.findByEmail(invite.getEmail(),
                                             invite.getOrganizationId(),
                                             invite.getApplicationId());
        return existingUser
                .compose(user -> {
                    if (user != null) {
                        return Future.<PendingInvite>failedFuture(new IllegalArgumentException(
                                invite.getApplicationId() == null
                                        ? "This email already belongs to an organization."
                                        : "A user with this email already exists in this application."));
                    }
                    return pendingInviteRepository.findByEmailAndScope(invite.getEmail(),
                                                                       invite.getOrganizationId(),
                                                                       invite.getApplicationId());
                })
                .compose(pending -> {
                    if (pending != null) {
                        return Future.failedFuture(new IllegalArgumentException(
                                "An invitation is already pending for this email."));
                    }
                    return Future.succeededFuture();
                });
    }

    @Override
    public Future<PendingInvite> getValidInvite(String token) {
        Validate.notBlank(token, "token is required");
        return pendingInviteRepository.findValidByToken(token);
    }

    @Override
    public Future<UserParticipantIdentity> acceptLocalInvite(String token, String password, String displayName) {
        Validate.notBlank(token, "token is required");
        Validate.notBlank(password, "password is required");

        return pendingInviteRepository.findValidByToken(token)
                .compose(invite -> acceptInvite(invite, password, displayName, null, null));
    }

    @Override
    public Future<UserParticipantIdentity> acceptOidcInvite(String token,
                                                            String oidcSubject,
                                                            String oidcConfigId,
                                                            String verifiedEmail) {
        Validate.notBlank(token, "token is required");
        Validate.notBlank(oidcSubject, "oidcSubject is required");
        Validate.notBlank(oidcConfigId, "oidcConfigId is required");
        Validate.notBlank(verifiedEmail, "verifiedEmail is required");

        return pendingInviteRepository.findValidByToken(token)
                .compose(invite -> {
                    if (!invite.getEmail().equals(DomainUtil.normalizeEmail(verifiedEmail))) {
                        return Future.failedFuture(new InviteEmailMismatchException(
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
    private Future<UserParticipantIdentity> acceptInvite(PendingInvite invite,
                                                         String password,
                                                         String displayNameOverride,
                                                         String oidcSubject,
                                                         String oidcConfigId) {
        UserParticipantIdentity user = new UserParticipantIdentity();
        user.setEmail(invite.getEmail())
            .setDisplayName(StringUtils.isNotBlank(displayNameOverride)
                                    ? displayNameOverride
                                    : invite.getDisplayName())
            .setOrganizationId(invite.getOrganizationId())
            .setApplicationId(invite.getApplicationId())
            .setTenantId(invite.getTenantId());
        if (oidcSubject != null) {
            user.setOidcSubject(oidcSubject)
                .setOidcConfigId(oidcConfigId);
        }
        return identityService.createUser(user, password)
                .compose(saved -> pendingInviteRepository.deleteById(invite.getId())
                                                         .map(saved));
    }

    @Override
    public Future<Page<PendingInvite>> findPendingInvites(String organizationId,
                                                          String applicationId,
                                                          Pageable pageable) {
        Validate.notBlank(organizationId, "organizationId is required");
        return pendingInviteRepository.findByScope(organizationId, applicationId, pageable);
    }

    @Override
    public Future<Page<PendingInvite>> findPendingInvitesByTenant(String organizationId,
                                                                  String applicationId,
                                                                  String tenantId,
                                                                  Pageable pageable) {
        Validate.notBlank(organizationId, "organizationId is required");
        Validate.notBlank(applicationId, "applicationId is required");
        Validate.notBlank(tenantId, "tenantId is required");
        return pendingInviteRepository.findByTenant(organizationId, applicationId, tenantId, pageable);
    }

    @Override
    public Future<Void> cancelInvite(String inviteId, String organizationId) {
        Validate.notBlank(inviteId, "inviteId is required");
        Validate.notBlank(organizationId, "organizationId is required");
        return cancel(inviteId, invite -> organizationId.equals(invite.getOrganizationId()));
    }

    @Override
    public Future<Void> cancelTenantInvite(String inviteId, String organizationId, String applicationId, String tenantId) {
        Validate.notBlank(inviteId, "inviteId is required");
        Validate.notBlank(organizationId, "organizationId is required");
        Validate.notBlank(applicationId, "applicationId is required");
        Validate.notBlank(tenantId, "tenantId is required");
        return cancel(inviteId, invite -> organizationId.equals(invite.getOrganizationId())
                && applicationId.equals(invite.getApplicationId())
                && tenantId.equals(invite.getTenantId()));
    }

    // The repository's deleteById is unscoped, so this load-and-assert is the security boundary keeping one
    // scope from cancelling another's invites; a missing and a foreign invite fail the same way, so there is
    // no existence oracle
    private Future<Void> cancel(String inviteId, Predicate<PendingInvite> ownedByCaller) {
        return pendingInviteRepository.findById(inviteId)
                .compose(invite -> {
                    if (invite == null || !ownedByCaller.test(invite)) {
                        return Future.failedFuture(new IllegalArgumentException("Invitation not found."));
                    }
                    // Sync delete so the console's immediate re-query no longer sees the row.
                    return pendingInviteRepository.deleteByIdSync(inviteId);
                });
    }
}
