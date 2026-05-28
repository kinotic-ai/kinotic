package org.kinotic.domain.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.SignUpRequest;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.internal.api.repositories.IamUserRepository;
import org.kinotic.domain.api.services.iam.SignUpService;
import org.kinotic.domain.internal.api.repositories.IamCredentialRepository;
import org.kinotic.domain.internal.api.repositories.SignUpRepository;
import org.kinotic.domain.internal.utils.DomainUtil;
import org.kinotic.domain.internal.api.services.EmailService;
import org.kinotic.domain.internal.api.model.IamCredential;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSignUpService implements SignUpService {

    private static final long VERIFICATION_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 hours

    private final SignUpRepository signUpRepository;
    private final IamUserRepository iamUserRepository;
    private final IamCredentialRepository credentialRepository;
    private final OrganizationService organizationService;
    private final EmailService emailService;

    @Override
    public CompletableFuture<Void> initiateSignUp(SignUpRequest request) {
        Validate.notBlank(request.getOrgName(), "Organization name is required");
        Validate.notBlank(request.getEmail(), "Email is required");
        Validate.notBlank(request.getDisplayName(), "Display name is required");

        // Check if a sign-up is already pending for this email
        return signUpRepository.findByEmail(request.getEmail())
                .thenCompose(existing -> {
                    if (existing != null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("A sign-up is already pending for this email. Check your inbox for the verification link."));
                    }
                    // Check if a user with this email already exists in any ORGANIZATION scope
                    return iamUserRepository.findFirstByEmailInScopeType(request.getEmail(), "ORGANIZATION");
                })
                .thenCompose(existingUser -> {
                    if (existingUser != null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("An account with this email already exists."));
                    }
                    return populateAndSave(request);
                });
    }

    private CompletableFuture<Void> populateAndSave(SignUpRequest request) {
        String verificationToken = UUID.randomUUID().toString();

        request.setId(UUID.randomUUID().toString())
               .setVerificationToken(verificationToken)
               .setExpiresAt(new Date(System.currentTimeMillis() + VERIFICATION_EXPIRY_MS))
               .setCreated(new Date());

        return signUpRepository.save(request)
                .thenCompose(saved -> emailService.sendVerificationEmail(
                        request.getEmail(),
                        request.getDisplayName(),
                        verificationToken));
    }

    @Override
    public CompletableFuture<String> completeSignUp(String verificationToken, String password) {
        Validate.notBlank(verificationToken, "Verification token is required");
        Validate.notBlank(password, "Password is required");

        return signUpRepository.findByToken(verificationToken)
                .thenCompose(request -> {
                    if (request == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Invalid or already used verification token."));
                    }
                    if (request.getExpiresAt().before(new Date())) {
                        // Clean up expired record
                        return signUpRepository.deleteById(request.getId())
                                .thenCompose(v -> CompletableFuture.failedFuture(
                                        new IllegalArgumentException("Verification link has expired. Please sign up again.")));
                    }
                    return createOrgAndUser(request, password);
                });
    }

    private CompletableFuture<String> createOrgAndUser(SignUpRequest request, String password) {
        // Create the organization
        Organization org = new Organization()
                .setName(request.getOrgName())
                .setDescription(request.getOrgDescription());

        return organizationService.save(org)
                .thenCompose(savedOrg -> {
                    // Create the user scoped to the new organization
                    IamUser user = new IamUser()
                            .setId(UUID.randomUUID().toString())
                            .setEmail(request.getEmail())
                            .setDisplayName(request.getDisplayName())
                            .setAuthType(AuthType.LOCAL)
                            .setOrganizationId(savedOrg.getId())
                            .setEnabled(true)
                            .setCreated(new Date())
                            .setUpdated(new Date());

                    return iamUserRepository.save(user)
                                            .thenCompose(savedUser -> {
                                // Update org with createdBy
                                savedOrg.setCreatedBy(savedUser.getId());
                                return organizationService.save(savedOrg)
                                        .thenApply(updatedOrg -> savedUser);
                            })
                                            .thenCompose(savedUser -> {
                                // Create the credential with the user-supplied password
                                IamCredential credential = new IamCredential()
                                        .setId(savedUser.getId())
                                        .setPasswordHash(DomainUtil.hashPassword(password));
                                return credentialRepository.save(credential)
                                        .thenApply(c -> savedOrg.getId());
                            });
                })
                .thenCompose(orgId ->
                    // Delete the pending record
                    signUpRepository.deleteById(request.getId())
                            .thenApply(v -> orgId));
    }

}
