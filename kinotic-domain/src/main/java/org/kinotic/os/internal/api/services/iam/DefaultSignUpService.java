package org.kinotic.os.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.os.api.model.Organization;
import org.kinotic.os.api.model.iam.AuthType;
import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.model.iam.PendingSignUp;
import org.kinotic.os.api.model.iam.SignUpRequest;
import org.kinotic.os.api.services.OrganizationService;
import org.kinotic.os.api.services.iam.SignUpService;
import org.kinotic.os.internal.api.services.EmailService;
import org.kinotic.os.internal.model.iam.IamCredential;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSignUpService implements SignUpService {

    private static final long VERIFICATION_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 hours

    private final PendingSignUpService pendingSignUpService;
    private final DefaultIamUserService userService;
    private final IamCredentialStore credentialStore;
    private final OrganizationService organizationService;
    private final PasswordService passwordService;
    private final EmailService emailService;

    @Override
    public CompletableFuture<Void> initiateSignUp(SignUpRequest request) {
        Validate.notBlank(request.getOrgName(), "Organization name is required");
        Validate.notBlank(request.getEmail(), "Email is required");
        Validate.notBlank(request.getDisplayName(), "Display name is required");
        Validate.notBlank(request.getPassword(), "Password is required");

        // Check if a pending sign-up already exists for this email
        return pendingSignUpService.findByEmail(request.getEmail())
                                   .thenCompose(existing -> {
                    if (existing != null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("A sign-up is already pending for this email. Check your inbox for the verification link."));
                    }
                    // Check if a user with this email already exists in any ORGANIZATION scope
                    return userService.findByEmailAndScope(request.getEmail(), "ORGANIZATION", null);
                })
                                   .thenCompose(existingUser -> {
                    if (existingUser != null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("An account with this email already exists."));
                    }
                    return createPendingSignUp(request);
                });
    }

    private CompletableFuture<Void> createPendingSignUp(SignUpRequest request) {
        String verificationToken = UUID.randomUUID().toString();

        PendingSignUp pending = new PendingSignUp()
                .setId(UUID.randomUUID().toString())
                .setOrgName(request.getOrgName())
                .setOrgDescription(request.getOrgDescription())
                .setEmail(request.getEmail())
                .setDisplayName(request.getDisplayName())
                .setPasswordHash(passwordService.hash(request.getPassword()))
                .setVerificationToken(verificationToken)
                .setExpiresAt(new Date(System.currentTimeMillis() + VERIFICATION_EXPIRY_MS))
                .setCreated(new Date());

        return pendingSignUpService.save(pending)
                                   .thenAccept(saved -> emailService.sendVerificationEmail(
                        request.getEmail(),
                        request.getDisplayName(),
                        verificationToken));
    }

    @Override
    public CompletableFuture<String> verifySignUp(String verificationToken) {
        Validate.notBlank(verificationToken, "Verification token is required");

        return pendingSignUpService.findByToken(verificationToken)
                                   .thenCompose(pending -> {
                    if (pending == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Invalid or already used verification token."));
                    }
                    if (pending.getExpiresAt().before(new Date())) {
                        // Clean up expired record
                        return pendingSignUpService.deleteById(pending.getId())
                                                   .thenCompose(v -> CompletableFuture.failedFuture(
                                        new IllegalArgumentException("Verification link has expired. Please sign up again.")));
                    }
                    return createOrgAndUser(pending);
                });
    }

    private CompletableFuture<String> createOrgAndUser(PendingSignUp pending) {
        // Create the organization
        Organization org = new Organization()
                .setName(pending.getOrgName())
                .setDescription(pending.getOrgDescription());

        return organizationService.save(org)
                .thenCompose(savedOrg -> {
                    // Create the user scoped to the new organization
                    IamUser user = new IamUser()
                            .setId(UUID.randomUUID().toString())
                            .setEmail(pending.getEmail())
                            .setDisplayName(pending.getDisplayName())
                            .setAuthType(AuthType.LOCAL)
                            .setAuthScopeType("ORGANIZATION")
                            .setAuthScopeId(savedOrg.getId())
                            .setEnabled(true)
                            .setCreated(new Date())
                            .setUpdated(new Date());

                    return userService.save(user)
                            .thenCompose(savedUser -> {
                                // Update org with createdBy
                                savedOrg.setCreatedBy(savedUser.getId());
                                return organizationService.save(savedOrg)
                                        .thenApply(updatedOrg -> savedUser);
                            })
                            .thenCompose(savedUser -> {
                                // Create the credential
                                IamCredential credential = new IamCredential()
                                        .setId(savedUser.getId())
                                        .setPasswordHash(pending.getPasswordHash());
                                return credentialStore.save(credential)
                                        .thenApply(c -> savedOrg.getId());
                            });
                })
                .thenCompose(orgId ->
                    // Delete the pending record
                    pendingSignUpService.deleteById(pending.getId())
                                        .thenApply(v -> orgId));
    }

}
