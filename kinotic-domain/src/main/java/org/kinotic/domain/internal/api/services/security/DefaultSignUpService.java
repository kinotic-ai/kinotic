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
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
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
    private final OrganizationService organizationService;
    private final EmailService emailService;

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
                    return identityService.findFirstOrgUserByEmail(email);
                })
                .compose(existingUser -> {
                    if (existingUser != null) {
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
    public Future<UserParticipantIdentity> completeLocalSignUp(String token, String orgName, String orgDescription, String password) {
        Validate.notBlank(token, "Verification token is required");
        Validate.notBlank(orgName, "Organization name is required");
        Validate.notBlank(password, "Password is required");

        return pendingSignUpRepository.findValidByToken(token)
                .compose(pending -> createOrgWithAdmin(orgName, orgDescription, newUser(pending), password)
                        .compose(savedAdmin -> pendingSignUpRepository.deleteById(pending.getId())
                                .map(savedAdmin)));
    }

    @Override
    public Future<UserParticipantIdentity> completeOidcWithNewOrg(String token, String orgName, String orgDescription) {
        Validate.notBlank(orgName, "Organization name is required");
        return pendingSignUpRepository.findValidByToken(token)
                .compose(pending -> createOrgWithAdmin(orgName, orgDescription, newUser(pending), null)
                        .compose(savedAdmin -> pendingSignUpRepository.deleteById(pending.getId())
                                .map(savedAdmin)));
    }

    /**
     * Creates the organization (failing if the name is taken), makes {@code admin} its first
     * member and creator, then starts provisioning it. The admin (and its credential, when
     * {@code password} is non-null) is created through {@link ParticipantIdentityService#createUser}
     * so member creation has a single code path.
     */
    private Future<UserParticipantIdentity> createOrgWithAdmin(String orgName, String orgDescription, UserParticipantIdentity admin, String password) {
        Organization org = new Organization().setName(orgName).setDescription(orgDescription);
        return organizationService.create(org)
                .compose(savedOrg -> {
                    admin.setOrganizationId(savedOrg.getId());
                    return identityService.createUser(admin, password)
                            .compose(savedAdmin -> {
                                savedOrg.setCreatedBy(savedAdmin.getId());
                                // provisioned once the record is complete, so nothing it
                                // records races the creator being set
                                return organizationService.save(savedOrg)
                                                          .compose(saved -> organizationService.provision(saved.getId()))
                                                          .map(savedAdmin);
                            });
                });
    }

    /**
     * Builds an unsaved {@link UserParticipantIdentity} carrying the pending record's identity. Id, dates,
     * the enabled flag and the credential are applied by {@link ParticipantIdentityService#createUser}.
     */
    private UserParticipantIdentity newUser(PendingSignUp pending) {
        UserParticipantIdentity user = new UserParticipantIdentity();
        user.setEmail(pending.getEmail())
            .setDisplayName(pending.getDisplayName())
            .setAuthType(pending.getAuthType());
        if (pending.getAuthType() == AuthType.OIDC) {
            user.setOidcSubject(pending.getOidcSubject())
                .setOidcConfigId(pending.getOidcConfigId());
        }
        return user;
    }
}
