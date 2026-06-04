package org.kinotic.domain.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.PendingSignUp;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.iam.SignUpService;
import org.kinotic.domain.internal.api.model.IamCredential;
import org.kinotic.domain.internal.api.repositories.IamCredentialRepository;
import org.kinotic.domain.internal.api.repositories.IamUserRepository;
import org.kinotic.domain.internal.api.repositories.PendingSignUpRepository;
import org.kinotic.domain.internal.api.services.EmailService;
import org.kinotic.domain.internal.utils.DomainUtil;
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
    private final IamUserRepository iamUserRepository;
    private final IamCredentialRepository credentialRepository;
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
                    return iamUserRepository.findFirstOrgUserByEmail(email);
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
    public CompletableFuture<String> completeLocalSignUp(String token, String orgName, String orgDescription, String password) {
        Validate.notBlank(token, "Verification token is required");
        Validate.notBlank(orgName, "Organization name is required");
        Validate.notBlank(password, "Password is required");

        return pendingSignUpRepository.findValidByToken(token)
                .thenCompose(pending -> {
                    IamUser admin = newUser(pending);
                    return createOrgWithAdmin(orgName, orgDescription, admin)
                            .thenCompose(savedAdmin -> {
                                IamCredential credential = new IamCredential()
                                        .setId(savedAdmin.getId())
                                        .setPasswordHash(DomainUtil.hashPassword(password));
                                return credentialRepository.save(credential)
                                        .thenCompose(c -> pendingSignUpRepository.deleteById(pending.getId())
                                                .thenApply(v -> savedAdmin.getOrganizationId()));
                            });
                });
    }

    @Override
    public CompletableFuture<IamUser> completeOidcWithNewOrg(String token, String orgName, String orgDescription) {
        Validate.notBlank(orgName, "Organization name is required");
        return pendingSignUpRepository.findValidByToken(token)
                .thenCompose(pending -> {
                    IamUser admin = newUser(pending);
                    return createOrgWithAdmin(orgName, orgDescription, admin)
                            .thenCompose(savedAdmin -> pendingSignUpRepository.deleteById(pending.getId())
                                    .thenApply(v -> savedAdmin));
                });
    }

    /** Creates the organization (failing if the name is taken), then makes {@code admin} its first member and creator. */
    private CompletableFuture<IamUser> createOrgWithAdmin(String orgName, String orgDescription, IamUser admin) {
        Organization org = new Organization().setName(orgName).setDescription(orgDescription);
        return organizationService.create(org)
                .thenCompose(savedOrg -> {
                    admin.setOrganizationId(savedOrg.getId());
                    return iamUserRepository.save(admin)
                            .thenCompose(savedAdmin -> {
                                savedOrg.setCreatedBy(savedAdmin.getId());
                                return organizationService.save(savedOrg).thenApply(o -> savedAdmin);
                            });
                });
    }

    /** Builds an unsaved {@link IamUser} from the pending record's identity, auth-method aware. */
    private IamUser newUser(PendingSignUp pending) {
        Date now = new Date();
        IamUser user = new IamUser()
                .setId(UUID.randomUUID().toString())
                .setEmail(pending.getEmail())
                .setDisplayName(pending.getDisplayName())
                .setAuthType(pending.getAuthType())
                .setEnabled(true)
                .setCreated(now)
                .setUpdated(now);
        if (pending.getAuthType() == AuthType.OIDC) {
            user.setOidcSubject(pending.getOidcSubject())
                .setOidcConfigId(pending.getOidcConfigId());
        }
        return user;
    }
}
