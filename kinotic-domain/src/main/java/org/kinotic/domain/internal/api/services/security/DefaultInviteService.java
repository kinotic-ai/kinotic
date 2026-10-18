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
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.exceptions.AccountDisabledException;
import org.kinotic.domain.api.exceptions.ExistingAccountException;
import org.kinotic.domain.api.exceptions.InviteEmailMismatchException;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.services.security.MembershipService;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.internal.api.repositories.PendingInviteRepository;
import org.kinotic.domain.internal.api.services.EmailService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultInviteService implements InviteService {

    // Invites sit in inboxes, unlike the minutes/hours-lived sign-up tokens.
    private static final long INVITE_TTL_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    private final PendingInviteRepository pendingInviteRepository;
    private final ParticipantIdentityService identityService;
    private final MembershipService membershipService;
    private final LocalAuthenticationService localAuthenticationService;
    private final OrganizationService organizationService;
    private final EmailService emailService;

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
                            .compose(saved -> emailService.sendInviteEmail(saved, org.getName())
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
                .compose(invite -> {
                    Future<Participant> ret;
                    if (invite.getApplicationId() != null) {
                        ret = identityService.createUser(newMember(invite, displayName), password)
                                             .map(DomainUtil::createParticipant);
                    } else {
                        ret = identityService.findByEmail(invite.getEmail(), null, null).compose(account -> {
                            Future<Participant> joined;
                            if (account == null) {
                                joined = identityService.createUser(newMember(invite, displayName), password)
                                                        .compose(created -> join(created, invite));
                            } else if (!account.isEnabled()) {
                                // the invitation's holder owns the email, so the account's standing is theirs to learn; a
                                // password check would answer as for a wrong password
                                joined = Future.failedFuture(new AccountDisabledException("This account cannot sign in to the organization."));
                            } else {
                                joined = requireAccountPassword(account, password).compose(v -> join(account, invite));
                            }
                            return joined;
                        });
                    }
                    return ret.compose(member -> pendingInviteRepository.deleteById(invite.getId()).map(member));
                });
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
                    if (!invite.getEmail().equals(DomainUtil.normalizeEmail(verifiedEmail))) {
                        return Future.failedFuture(new InviteEmailMismatchException(
                                "This invitation was sent to a different email address."));
                    }
                    Future<Participant> ret;
                    if (invite.getApplicationId() != null) {
                        ret = identityService.createOidcUser(newMember(invite, null), oidcConfigId, oidcSubject)
                                             .map(DomainUtil::createParticipant);
                    } else {
                        ret = identityService.findByOidcIdentity(oidcConfigId, oidcSubject, null, null).compose(bound -> {
                            Future<Participant> joined;
                            if (bound != null) {
                                // the identity proves the acceptor owns the account, which must be the invited one
                                joined = invite.getEmail().equals(bound.getEmail())
                                        ? join(bound, invite)
                                        : Future.failedFuture(new InviteEmailMismatchException(
                                                "This invitation was sent to a different email address."));
                            } else {
                                joined = identityService.findByEmail(invite.getEmail(), null, null).compose(account -> account == null
                                        ? identityService.createOidcUser(newMember(invite, null), oidcConfigId, oidcSubject)
                                                         .compose(created -> join(created, invite))
                                        : Future.failedFuture(new ExistingAccountException(
                                                "An account already exists for this email. Sign in with its password to accept.")));
                            }
                            return joined;
                        });
                    }
                    return ret.compose(member -> pendingInviteRepository.deleteById(invite.getId()).map(member));
                });
    }

    // an application invitee is a user of the application; an organization invitee is an account holding a
    // membership of the organization, whatever the membership's enabled flag
    private Future<Boolean> isMemberAlready(PendingInvite invite) {
        Future<Boolean> ret;
        if (invite.getApplicationId() != null) {
            ret = identityService.findByEmail(invite.getEmail(), invite.getOrganizationId(), invite.getApplicationId())
                                 .map(Objects::nonNull);
        } else {
            ret = identityService.findByEmail(invite.getEmail(), null, null)
                                 .compose(account -> account == null
                                         ? Future.succeededFuture(false)
                                         : membershipService.findMembership(account.getId(), invite.getOrganizationId())
                                                            .map(Objects::nonNull));
        }
        return ret;
    }

    /**
     * The unsaved user an invitation creates: an application's user in the invitation's application, or an
     * account for an invitation into an organization. Id, dates, the enabled flag and the sign-in method are
     * applied by the identity service. A concurrent double-accept is safe — the unique-email check fails the
     * second attempt.
     */
    private static UserParticipantIdentity newMember(PendingInvite invite, String displayNameOverride) {
        UserParticipantIdentity user = new UserParticipantIdentity();
        user.setEmail(invite.getEmail())
            .setDisplayName(StringUtils.isNotBlank(displayNameOverride)
                                    ? displayNameOverride
                                    : invite.getDisplayName());
        if (invite.getApplicationId() != null) {
            user.setOrganizationId(invite.getOrganizationId())
                .setApplicationId(invite.getApplicationId());
        }
        return user;
    }

    // the account becomes a member of the invitation's organization and signs in there; a disabled account
    // joins nothing, and a membership that already exists is reused, so one disabled before the acceptance
    // refuses the sign-in as a login would
    private Future<Participant> join(UserParticipantIdentity account, PendingInvite invite) {
        Future<Participant> ret;
        if (!account.isEnabled()) {
            ret = Future.failedFuture(new AccountDisabledException("This account cannot sign in to the organization."));
        } else {
            ret = membershipService.addMember(account.getId(), invite.getOrganizationId())
                                   .compose(membership -> membershipService.resolveMember(account, invite.getOrganizationId()));
        }
        return ret;
    }

    // an existing account is joined only by someone who knows its password
    private Future<Void> requireAccountPassword(UserParticipantIdentity account, String password) {
        return localAuthenticationService.authenticateLocal(account.getEmail(), password, null, null)
                                         .compose(authenticated -> authenticated == null
                                                 ? Future.failedFuture(new ExistingAccountException(
                                                         "An account already exists for this email. Enter its password to accept."))
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
