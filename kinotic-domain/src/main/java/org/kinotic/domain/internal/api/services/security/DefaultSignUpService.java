package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.security.AuthType;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.PendingSignUp;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.SiteDomainProvisioner;
import org.kinotic.domain.api.services.security.MembershipService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.services.security.SignUpService;
import org.kinotic.domain.internal.api.repositories.PendingSignUpRepository;
import org.kinotic.domain.internal.api.services.EmailService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSignUpService implements SignUpService {

    private static final long LOCAL_TTL_MS = 24 * 60 * 60 * 1000L; // 24 hours
    private static final long OIDC_TTL_MS = 10 * 60 * 1000L;       // 10 minutes

    private final PendingSignUpRepository pendingSignUpRepository;
    private final ParticipantIdentityService identityService;
    private final MembershipService membershipService;
    private final OrganizationService organizationService;
    private final EmailService emailService;
    private final SiteDomainProvisioner siteDomainProvisioner;

    @Override
    public Future<Void> initiateLocalSignUp(String email, String displayName) {
        Validate.notBlank(email, "Email is required");
        Validate.notBlank(displayName, "Display name is required");

        return pendingSignUpRepository.findByEmail(email)
                .compose(existing -> {
                    if (existing != null) {
                        return Future.failedFuture(new IllegalArgumentException(
                                "A sign-up is already pending for this email. Check your inbox for the verification link."));
                    }
                    return identityService.findByEmail(email, null, null);
                })
                .compose(account -> {
                    if (account != null) {
                        return Future.failedFuture(new IllegalArgumentException(
                                "An account with this email already exists."));
                    }
                    String token = UUID.randomUUID().toString();
                    Date now = new Date();
                    PendingSignUp pending = new PendingSignUp()
                            .setId(UUID.randomUUID().toString())
                            .setEmail(email)
                            .setDisplayName(displayName)
                            .setAuthType(AuthType.LOCAL)
                            .setVerificationToken(token)
                            .setCreated(now)
                            .setExpiresAt(new Date(now.getTime() + LOCAL_TTL_MS));
                    return pendingSignUpRepository.save(pending)
                            .compose(saved -> emailService.sendVerificationEmail(email, displayName, token));
                });
    }

    @Override
    public Future<PendingSignUp> createOidcPending(PendingSignUp pending) {
        Validate.notBlank(pending.getOidcSubject(), "oidcSubject is required");
        Validate.notBlank(pending.getOidcConfigId(), "oidcConfigId is required");
        Validate.notBlank(pending.getEmail(), "email is required");

        Date now = new Date();
        pending.setId(UUID.randomUUID().toString())
               .setAuthType(AuthType.OIDC)
               .setVerificationToken(UUID.randomUUID().toString())
               .setCreated(now)
               .setExpiresAt(new Date(now.getTime() + OIDC_TTL_MS));
        return pendingSignUpRepository.saveSync(pending);
    }

    @Override
    public Future<Participant> completeLocalSignUp(String token, String orgName, String orgDescription, String password) {
        Validate.notBlank(token, "Verification token is required");
        Validate.notBlank(orgName, "Organization name is required");
        Validate.notBlank(password, "Password is required");

        return pendingSignUpRepository.findValidByToken(token)
                .compose(pending -> requireNoAccount(pending)
                        .compose(v -> createOrgWithFounder(orgName, orgDescription, pending, password))
                        .compose(founder -> pendingSignUpRepository.deleteById(pending.getId())
                                .map(founder)));
    }

    @Override
    public Future<Participant> completeOidcWithNewOrg(String token, String orgName, String orgDescription) {
        Validate.notBlank(orgName, "Organization name is required");
        return pendingSignUpRepository.findValidByToken(token)
                .compose(pending -> requireNoAccount(pending)
                        .compose(v -> createOrgWithFounder(orgName, orgDescription, pending, null))
                        .compose(founder -> pendingSignUpRepository.deleteById(pending.getId())
                                .map(founder)));
    }

    /**
     * Fails when the identity of {@code pending}, or its verified email, already has an account: a person
     * founds one organization at most, by signing up once, and joins others by invitation. Checked here, at
     * completion, as well as when the sign-up starts, so two sign-ups of one identity in flight at once cannot
     * both found an organization, and so the organization is never created for an account that cannot be.
     */
    private Future<Void> requireNoAccount(PendingSignUp pending) {
        Future<UserParticipantIdentity> bound = pending.getAuthType() == AuthType.OIDC
                ? identityService.findByOidcIdentity(pending.getOidcConfigId(), pending.getOidcSubject(), null, null)
                : Future.succeededFuture();
        return bound.compose(found -> found != null ? Future.succeededFuture(found) : identityService.findByEmail(pending.getEmail(), null, null))
                    .compose(found -> found == null
                            ? Future.succeededFuture()
                            : Future.failedFuture(new IllegalArgumentException("An account for this identity already exists. Sign in instead.")));
    }

    /**
     * Creates the organization (failing if the name is taken), the founder's account from the pending
     * sign-up — with {@code password} as its credential, or bound to the pending record's verified identity
     * — its membership of the organization, which makes it the organization's creator, and provisions the
     * organization's sites subdomain at the edge.
     *
     * @return the founder as the new organization's participant
     */
    private Future<Participant> createOrgWithFounder(String orgName, String orgDescription, PendingSignUp pending, String password) {
        Organization org = new Organization().setName(orgName).setDescription(orgDescription);
        UserParticipantIdentity account = new UserParticipantIdentity();
        account.setEmail(pending.getEmail())
               .setDisplayName(pending.getDisplayName());
        return organizationService.create(org)
                .compose(savedOrg -> {
                    Future<UserParticipantIdentity> created = pending.getAuthType() == AuthType.OIDC
                            ? identityService.createOidcUser(account, pending.getOidcConfigId(), pending.getOidcSubject())
                            : identityService.createUser(account, password);
                    return created
                            .compose(founder -> membershipService.addMember(founder.getId(), savedOrg.getId())
                                                                 .compose(membership -> {
                                                                     savedOrg.setCreatedBy(founder.getId());
                                                                     return organizationService.save(savedOrg)
                                                                                               .map(DomainUtil.createParticipant(founder, membership));
                                                                 })
                                                                 // an account whose membership or organization could not be
                                                                 // completed goes too: left behind, it could neither sign in
                                                                 // anywhere nor sign up again
                                                                 .recover(err -> identityService.deleteById(founder.getId())
                                                                                                .transform(v -> Future.failedFuture(err))))
                            // and so does the organization, as when a sign-up of the same identity completed first, so
                            // its name is not held by an organization no one is in
                            .recover(err -> organizationService.deleteById(savedOrg.getId())
                                                               .transform(v -> Future.failedFuture(err)))
                            // the edge is provisioned again before the organization's first site is published, so a
                            // refusal here costs the sign-up nothing
                            .compose(founder -> siteDomainProvisioner.ensure(savedOrg.getId())
                                                                     .recover(err -> {
                                                                         log.warn("Could not provision the sites subdomain of organization {}: {}",
                                                                                  savedOrg.getId(), err.getMessage());
                                                                         return Future.succeededFuture();
                                                                     })
                                                                     .map(founder));
                });
    }
}
