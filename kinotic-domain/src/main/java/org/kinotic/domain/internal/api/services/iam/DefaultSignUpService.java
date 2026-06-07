package org.kinotic.domain.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.PendingSignUp;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.iam.IamUserService;
import org.kinotic.domain.api.services.iam.SignUpService;
import org.kinotic.domain.internal.api.repositories.PendingSignUpRepository;
import org.kinotic.domain.internal.api.services.EmailService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSignUpService implements SignUpService {

    private static final long LOCAL_TTL_MS = 24 * 60 * 60 * 1000L; // 24 hours
    private static final long OIDC_TTL_MS = 10 * 60 * 1000L;       // 10 minutes

    private final PendingSignUpRepository pendingSignUpRepository;
    private final IamUserService iamUserService;
    private final OrganizationService organizationService;
    private final EmailService emailService;

    @Override
    public CompletableFuture<Void> initiateLocalSignUp(String email, String displayName) {
        Validate.notBlank(email, "Email is required");
        Validate.notBlank(displayName, "Display name is required");

        return pendingSignUpRepository.findByEmail(email)
                .thenCompose(existing -> {
                    if (existing != null) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException(
                                "A sign-up is already pending for this email. Check your inbox for the verification link."));
                    }
                    return iamUserService.findFirstOrgUserByEmail(email);
                })
                .thenCompose(existingUser -> {
                    if (existingUser != null) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException(
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
                            .thenCompose(saved -> emailService.sendVerificationEmail(email, displayName, token));
                });
    }

    @Override
    public CompletableFuture<PendingSignUp> createOidcPending(PendingSignUp pending) {
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
    public CompletableFuture<IamUser> completeLocalSignUp(String token, String orgName, String orgDescription, String password) {
        Validate.notBlank(token, "Verification token is required");
        Validate.notBlank(orgName, "Organization name is required");
        Validate.notBlank(password, "Password is required");
        return completeWithNewOrg(token, orgName, orgDescription, password);
    }

    @Override
    public CompletableFuture<IamUser> completeOidcWithNewOrg(String token, String orgName, String orgDescription) {
        Validate.notBlank(orgName, "Organization name is required");
        return completeWithNewOrg(token, orgName, orgDescription, null);
    }

    /**
     * Consumes the pending sign-up, creates the org and its admin (with a password for LOCAL, none
     * for OIDC), then deletes the pending record. {@code password == null} drives an OIDC admin.
     */
    private CompletableFuture<IamUser> completeWithNewOrg(String token, String orgName, String orgDescription, String password) {
        return pendingSignUpRepository.findValidByToken(token)
                .thenCompose(pending -> {
                    IamUser admin = newUser(pending);
                    return createOrgWithAdmin(orgName, orgDescription, admin, password)
                            .thenCompose(savedAdmin -> pendingSignUpRepository.deleteById(pending.getId())
                                    .thenApply(v -> savedAdmin));
                });
    }

    /** Creates the organization (failing if the name is taken), then makes {@code admin} its first member and creator. */
    private CompletableFuture<IamUser> createOrgWithAdmin(String orgName, String orgDescription, IamUser admin, String password) {
        Organization org = new Organization().setName(orgName).setDescription(orgDescription);
        return organizationService.create(org)
                .thenCompose(savedOrg -> {
                    admin.setOrganizationId(savedOrg.getId());
                    return iamUserService.createUser(admin, password)
                            .thenCompose(savedAdmin -> {
                                savedOrg.setCreatedBy(savedAdmin.getId());
                                return organizationService.save(savedOrg).thenApply(o -> savedAdmin);
                            });
                });
    }

    /** Builds an unsaved {@link IamUser} from the pending record's identity, auth-method aware. */
    private IamUser newUser(PendingSignUp pending) {
        IamUser user = new IamUser()
                .setEmail(pending.getEmail())
                .setDisplayName(pending.getDisplayName())
                .setAuthType(pending.getAuthType());
        if (pending.getAuthType() == AuthType.OIDC) {
            user.setOidcSubject(pending.getOidcSubject())
                .setOidcConfigId(pending.getOidcConfigId());
        }
        return user;
    }
}
