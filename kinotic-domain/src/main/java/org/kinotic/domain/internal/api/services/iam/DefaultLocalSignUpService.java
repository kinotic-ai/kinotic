package org.kinotic.domain.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.LocalPendingSignUp;
import org.kinotic.domain.api.services.iam.LocalSignUpService;
import org.kinotic.domain.internal.api.model.IamCredential;
import org.kinotic.domain.internal.api.repositories.IamCredentialRepository;
import org.kinotic.domain.internal.api.repositories.IamUserRepository;
import org.kinotic.domain.internal.api.repositories.LocalPendingSignUpRepository;
import org.kinotic.domain.internal.api.services.EmailService;
import org.kinotic.domain.internal.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultLocalSignUpService implements LocalSignUpService {

    private static final long VERIFICATION_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 hours

    private final LocalPendingSignUpRepository pendingSignUpRepository;
    private final IamUserRepository iamUserRepository;
    private final IamCredentialRepository credentialRepository;
    private final EmailService emailService;
    private final OrgAdminProvisioner orgAdminProvisioner;

    @Override
    public CompletableFuture<Void> initiateSignUp(LocalPendingSignUp request) {
        Validate.notBlank(request.getOrgName(), "Organization name is required");
        Validate.notBlank(request.getEmail(), "Email is required");
        Validate.notBlank(request.getDisplayName(), "Display name is required");

        return pendingSignUpRepository.findByEmail(request.getEmail())
                .thenCompose(existing -> {
                    if (existing != null) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException(
                                "A sign-up is already pending for this email. Check your inbox for the verification link."));
                    }
                    return iamUserRepository.findFirstOrgUserByEmail(request.getEmail());
                })
                .thenCompose(existingUser -> {
                    if (existingUser != null) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException(
                                "An account with this email already exists."));
                    }
                    return populateAndSave(request);
                });
    }

    private CompletableFuture<Void> populateAndSave(LocalPendingSignUp request) {
        String verificationToken = UUID.randomUUID().toString();

        request.setId(UUID.randomUUID().toString());
        request.setVerificationToken(verificationToken);
        request.setExpiresAt(new Date(System.currentTimeMillis() + VERIFICATION_EXPIRY_MS));
        request.setCreated(new Date());

        return pendingSignUpRepository.save(request)
                .thenCompose(saved -> emailService.sendVerificationEmail(
                        request.getEmail(), request.getDisplayName(), verificationToken));
    }

    @Override
    public CompletableFuture<String> completeSignUp(String verificationToken, String password) {
        Validate.notBlank(verificationToken, "Verification token is required");
        Validate.notBlank(password, "Password is required");

        return pendingSignUpRepository.findValidByToken(verificationToken)
                                      .thenCompose(pending -> createOrgAndUser(pending, password));
    }

    private CompletableFuture<String> createOrgAndUser(LocalPendingSignUp pending, String password) {
        Date now = new Date();
        IamUser admin = new IamUser()
                .setId(UUID.randomUUID().toString())
                .setEmail(pending.getEmail())
                .setDisplayName(pending.getDisplayName())
                .setAuthType(AuthType.LOCAL)
                .setEnabled(true)
                .setCreated(now)
                .setUpdated(now);

        return orgAdminProvisioner.provisionNewOrg(pending.getOrgName(), pending.getOrgDescription(), admin)
                .thenCompose(savedAdmin -> {
                    IamCredential credential = new IamCredential()
                            .setId(savedAdmin.getId())
                            .setPasswordHash(DomainUtil.hashPassword(password));
                    return credentialRepository.save(credential)
                            .thenCompose(c -> pendingSignUpRepository.deleteById(pending.getId())
                                    .thenApply(v -> savedAdmin.getOrganizationId()));
                });
    }
}
