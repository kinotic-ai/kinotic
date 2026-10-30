package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.EmailTemplate;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.PendingInvite;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.exceptions.UserDisabledException;
import org.kinotic.domain.api.exceptions.ExistingUserException;
import org.kinotic.domain.api.exceptions.InviteEmailMismatchException;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.internal.api.repositories.PendingInviteRepository;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import org.kinotic.domain.internal.api.services.EmailService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultInviteService implements InviteService {

    // Invites sit in inboxes, unlike the minutes/hours-lived sign-up tokens.
    private static final long INVITE_TTL_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    private final PendingInviteRepository pendingInviteRepository;
    private final ParticipantIdentityRepository identityRepository;
    private final ParticipantIdentityService identityService;
    private final OrganizationService organizationService;
    private final PasswordConfigurationService passwordConfigurationService;
    private final EmailService emailService;
    private final KinoticDomainProperties domainProperties;

    @Override
    public Future<PendingInvite> createInvite(PendingInvite invite, EmailTemplate emailTemplate) {
        Validate.notBlank(invite.getEmail(), "email is required");
        Validate.notBlank(invite.getOrganizationId(), "organizationId is required");
        Validate.notBlank(invite.getInvitedByName(), "invitedByName is required");
        emailService.validateTemplate(emailTemplate);
        invite.setEmail(DomainUtil.normalizeEmail(invite.getEmail()));

        return organizationService.findById(invite.getOrganizationId())
                .compose(org -> {
                    if (org == null) {
                        return Future.failedFuture(
                                new IllegalArgumentException("Organization not found."));
                    }
                    return checkEmailAvailable(invite).map(org);
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
                            .compose(saved -> emailService.sendInviteEmail(saved, org.getName(), emailTemplate)
                                                          .map(saved));
                });
    }

    /**
     * Rejects an invitation to a scope that already holds a user with the email, or one for which an
     * invitation is already pending. Every login is addressed to its scope, so an email may be a
     * member of any number of organizations and applications.
     */
    private Future<Void> checkEmailAvailable(PendingInvite invite) {
        return isMemberAlready(invite)
                .compose(member -> {
                    if (member) {
                        return Future.<PendingInvite>failedFuture(new IllegalArgumentException(
                                invite.getApplicationId() == null
                                        ? "A member with this email already exists in this organization."
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
    public Future<Participant> acceptLocalInvite(String token, String password, String displayName) {
        Validate.notBlank(token, "token is required");
        Validate.notBlank(password, "password is required");

        return pendingInviteRepository.findValidByToken(token)
                .compose(invite -> requirePasswordSignIn(invite)
                        .compose(v -> findInvitedUser(invite))
                        .compose(user -> user == null
                                ? identityService.createUser(newUser(invite, displayName), password).map(DomainUtil::createParticipant)
                                // the user an administrator added is the invited address's to claim: the password
                                // becomes its first sign-in method
                                : requireUnclaimed(user).compose(v -> claim(user, displayName, password))
                                                        .map(v -> DomainUtil.createParticipant(user)))
                        .compose(member -> pendingInviteRepository.deleteById(invite.getId()).map(member)));
    }

    @Override
    public Future<Participant> acceptOidcInvite(String token,
                                                String oidcSubject,
                                                String oidcConfigId,
                                                String verifiedEmail) {
        Validate.notBlank(token, "token is required");
        Validate.notBlank(oidcSubject, "oidcSubject is required");
        Validate.notBlank(oidcConfigId, "oidcConfigId is required");
        Validate.notBlank(verifiedEmail, "verifiedEmail is required");

        return pendingInviteRepository.findValidByToken(token)
                .compose(invite -> {
                    // the application's providers return to the app server, whose login claims the invited user itself
                    Validate.isTrue(invite.getApplicationId() == null,
                                    "An invitation into an application is accepted by signing in to the application.");
                    if (!invite.getEmail().equals(DomainUtil.normalizeEmail(verifiedEmail))) {
                        return Future.failedFuture(new InviteEmailMismatchException(
                                "This invitation was sent to a different email address."));
                    }
                    String organizationId = invite.getOrganizationId();
                    return identityService.findOrganizationUserByOidcIdentity(oidcConfigId, oidcSubject, organizationId)
                            .compose(bound -> {
                                Future<Participant> joined;
                                if (bound != null) {
                                    // the identity already signs in a member of the organization, which must be the invited one
                                    joined = invite.getEmail().equals(bound.getEmail())
                                            ? requireEnabled(bound).map(v -> DomainUtil.createParticipant(bound))
                                            : Future.failedFuture(new InviteEmailMismatchException(
                                                    "This invitation was sent to a different email address."));
                                } else {
                                    joined = findInvitedUser(invite)
                                            .compose(user -> user == null
                                                    ? identityService.createOidcUser(newOrganizationUser(invite, null), oidcConfigId, oidcSubject)
                                                                     .map(DomainUtil::createParticipant)
                                                    // the member an administrator added is the invited address's to claim: the
                                                    // identity becomes its first sign-in method
                                                    : requireUnclaimed(user)
                                                            .compose(v -> identityService.bindOidcIdentity(user.getId(), oidcConfigId, oidcSubject))
                                                            .map(v -> DomainUtil.createParticipant(user)));
                                }
                                return joined;
                            })
                            .compose(member -> pendingInviteRepository.deleteById(invite.getId()).map(member));
                });
    }

    // a pending user is the invitee's to claim, so only one someone signs in as is a member already
    private Future<Boolean> isMemberAlready(PendingInvite invite) {
        return identityService.findUserByEmail(invite.getEmail(), invite.getOrganizationId(), invite.getApplicationId())
                              .map(user -> user != null && !user.isPending());
    }

    // the unsaved user of the invitation's scope that accepting it creates
    private static UserParticipantIdentity newUser(PendingInvite invite, String displayNameOverride) {
        return invite.getApplicationId() != null ? newApplicationUser(invite, displayNameOverride) : newOrganizationUser(invite, displayNameOverride);
    }

    /**
     * The unsaved user of the invitation's organization that an invitation into an organization creates. Id, dates,
     * the enabled flag and the sign-in method are applied by the identity service. A concurrent double-accept is
     * safe — the unique-email check fails the second attempt.
     */
    private static OrganizationParticipantIdentity newOrganizationUser(PendingInvite invite, String displayNameOverride) {
        OrganizationParticipantIdentity user = new OrganizationParticipantIdentity();
        user.setOrganizationId(invite.getOrganizationId())
            .setEmail(invite.getEmail())
            .setDisplayName(displayName(invite, displayNameOverride));
        return user;
    }

    /** The unsaved user of the invitation's application that an invitation into an application creates. */
    private static ApplicationParticipantIdentity newApplicationUser(PendingInvite invite, String displayNameOverride) {
        ApplicationParticipantIdentity user = new ApplicationParticipantIdentity();
        user.setOrganizationId(invite.getOrganizationId())
            .setApplicationId(invite.getApplicationId())
            .setTenantId(invite.getTenantId())
            .setEmail(invite.getEmail())
            .setDisplayName(displayName(invite, displayNameOverride));
        return user;
    }

    private static String displayName(PendingInvite invite, String displayNameOverride) {
        return StringUtils.isNotBlank(displayNameOverride) ? displayNameOverride : invite.getDisplayName();
    }

    // the user of the invitation's scope under the invited address as stored, or null; a search finds it, which may
    // predate an administrator's latest change, and the acceptance saves it
    private Future<UserParticipantIdentity> findInvitedUser(PendingInvite invite) {
        return identityService.findUserByEmail(invite.getEmail(), invite.getOrganizationId(), invite.getApplicationId())
                              .compose(found -> found != null
                                      ? identityService.findById(found.getId()).map(UserParticipantIdentity.class::cast)
                                      : Future.succeededFuture());
    }

    // a user someone already signs in as has accepted already, and a disabled one is refused as a login would refuse it
    private Future<Void> requireUnclaimed(UserParticipantIdentity user) {
        return requireEnabled(user)
                .compose(v -> user.isPending()
                        ? Future.<Void>succeededFuture()
                        : Future.failedFuture(new ExistingUserException("This email already has a user here. Sign in instead.")));
    }

    // the invited address makes the user its own: the name it gives, and the password it sets as its first sign-in
    // method. The claim is written first, and only by one acceptance: a second acceptance of the same invitation, or
    // an administrator disabling the user meanwhile, leaves the password of a user someone signs in as unset
    private Future<Void> claim(UserParticipantIdentity user, String displayName, String password) {
        String givenName = StringUtils.trimToNull(displayName);
        return identityRepository.clearPending(user.getId(), givenName)
                                 .compose(claimed -> {
                                     Future<Void> ret;
                                     if (claimed) {
                                         // the participant the acceptance signs in carries the name as written
                                         if (givenName != null) {
                                             user.setDisplayName(givenName);
                                         }
                                         ret = identityService.setPassword(user.getId(), password);
                                     } else {
                                         ret = Future.failedFuture(new ExistingUserException("This email already has a user here. Sign in instead."));
                                     }
                                     return ret;
                                 });
    }

    private static Future<Void> requireEnabled(UserParticipantIdentity user) {
        return user.isEnabled()
                ? Future.succeededFuture()
                : Future.failedFuture(new UserDisabledException("This user cannot sign in."));
    }

    // an invitation is accepted with a password only where the login of its scope takes one
    private Future<Void> requirePasswordSignIn(PendingInvite invite) {
        return passwordConfigurationService.findOffered(invite.getOrganizationId(), invite.getApplicationId(), invite.getTenantId())
                                           .compose(offered -> offered == null
                                                   ? Future.failedFuture(new IllegalArgumentException(invite.getApplicationId() == null
                                                           ? "This organization does not accept passwords. Accept the invitation with one of its sign-in providers."
                                                           : "This application does not accept passwords. Accept the invitation by signing in to it"
                                                             + " with one of its sign-in providers."))
                                                   : Future.succeededFuture());
    }

    @Override
    public Future<Page<PendingInvite>> findPendingInvites(String organizationId,
                                                          String applicationId,
                                                          Pageable pageable) {
        Validate.notBlank(organizationId, "organizationId is required");
        return pendingInviteRepository.findByScope(organizationId, applicationId, pageable);
    }

    @Override
    public Future<Void> deletePendingInvite(String email, String organizationId, String applicationId) {
        Validate.notBlank(email, "email is required");
        Validate.notBlank(organizationId, "organizationId is required");
        return pendingInviteRepository.findByEmailAndScope(email, organizationId, applicationId)
                                      // sync so the console's immediate re-query no longer sees it
                                      .compose(invite -> invite != null ? pendingInviteRepository.deleteByIdSync(invite.getId()) : Future.succeededFuture());
    }

    @Override
    public Future<PendingInvite> findPendingInvite(String email, String organizationId, String applicationId) {
        Validate.notBlank(email, "email is required");
        Validate.notBlank(organizationId, "organizationId is required");
        return pendingInviteRepository.findByEmailAndScope(email, organizationId, applicationId);
    }

    @Override
    public Future<String> findAcceptUrl(String email, String organizationId, String applicationId) {
        Validate.notBlank(email, "email is required");
        Validate.notBlank(organizationId, "organizationId is required");
        Future<String> ret;
        if (!domainProperties.getDomain().getEmail().isEnabled()) {
            ret = pendingInviteRepository.findByEmailAndScope(email, organizationId, applicationId)
                                         .map(invite -> invite == null ? null : emailService.inviteAcceptUrl(invite));
        } else {
            ret = Future.succeededFuture();
        }
        return ret;
    }

    @Override
    public Future<Void> resendInvite(String inviteId, String organizationId, EmailTemplate emailTemplate) {
        Validate.notBlank(inviteId, "inviteId is required");
        Validate.notBlank(organizationId, "organizationId is required");
        emailService.validateTemplate(emailTemplate);
        return pendingInviteRepository.findById(inviteId)
                .compose(invite -> {
                    // same message for missing and foreign, as cancelInvite answers
                    if (invite == null || !organizationId.equals(invite.getOrganizationId())) {
                        return Future.failedFuture(new IllegalArgumentException("Invitation not found."));
                    }
                    return organizationService.findById(organizationId)
                                              .compose(org -> emailService.sendInviteEmail(invite, org.getName(), emailTemplate));
                });
    }

    @Override
    public Future<Void> cancelInvite(String inviteId, String organizationId) {
        Validate.notBlank(inviteId, "inviteId is required");
        Validate.notBlank(organizationId, "organizationId is required");

        return pendingInviteRepository.findById(inviteId)
                .compose(invite -> {
                    // The repository deleteById is unscoped; this load-and-assert is the
                    // security boundary keeping one org from cancelling another's invites.
                    // Same message for missing and foreign — no existence oracle.
                    if (invite == null || !organizationId.equals(invite.getOrganizationId())) {
                        return Future.failedFuture(
                                new IllegalArgumentException("Invitation not found."));
                    }
                    // Sync delete so the console's immediate re-query no longer sees the row.
                    return pendingInviteRepository.deleteByIdSync(inviteId);
                });
    }
}
