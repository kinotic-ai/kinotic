package org.kinotic.os.internal.api.services.iam;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.os.api.model.Organization;
import org.kinotic.os.api.model.iam.AuthType;
import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.model.iam.SignUpRequest;
import org.kinotic.os.api.services.OrganizationService;
import org.kinotic.os.api.services.iam.IamUserService;
import org.kinotic.os.api.services.iam.SignUpService;
import org.kinotic.os.api.utils.DomainUtil;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.kinotic.os.internal.api.services.EmailService;
import org.kinotic.os.internal.api.model.iam.IamCredential;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSignUpService implements SignUpService {

    private static final long VERIFICATION_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 hours
    private static final String SIGNUP_REQUEST_INDEX = "kinotic_signup_request";

    private final ElasticsearchAsyncClient esAsyncClient;
    private final CrudServiceTemplate crudServiceTemplate;
    private final IamUserService userService;
    private final IamCredentialService credentialStore;
    private final OrganizationService organizationService;
    private final EmailService emailService;

    @PostConstruct
    public void verifyIndexExists() {
        crudServiceTemplate.verifyIndexExists(SIGNUP_REQUEST_INDEX);
    }

    @Override
    public CompletableFuture<Void> initiateSignUp(SignUpRequest request) {
        Validate.notBlank(request.getOrgName(), "Organization name is required");
        Validate.notBlank(request.getEmail(), "Email is required");
        Validate.notBlank(request.getDisplayName(), "Display name is required");

        // Check if a sign-up is already pending for this email
        return findByEmail(request.getEmail())
                .thenCompose(existing -> {
                    if (existing != null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("A sign-up is already pending for this email. Check your inbox for the verification link."));
                    }
                    // Check if a user with this email already exists in any ORGANIZATION scope
                    return userService.findFirstByEmailInScopeType(request.getEmail(), "ORGANIZATION");
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

        return save(request)
                .thenCompose(saved -> emailService.sendVerificationEmail(
                        request.getEmail(),
                        request.getDisplayName(),
                        verificationToken));
    }

    @Override
    public CompletableFuture<String> completeSignUp(String verificationToken, String password) {
        Validate.notBlank(verificationToken, "Verification token is required");
        Validate.notBlank(password, "Password is required");

        return findByToken(verificationToken)
                .thenCompose(request -> {
                    if (request == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Invalid or already used verification token."));
                    }
                    if (request.getExpiresAt().before(new Date())) {
                        // Clean up expired record
                        return deleteById(request.getId())
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
                                // Create the credential with the user-supplied password
                                IamCredential credential = new IamCredential()
                                        .setId(savedUser.getId())
                                        .setPasswordHash(DomainUtil.hashPassword(password));
                                return credentialStore.save(credential)
                                        .thenApply(c -> savedOrg.getId());
                            });
                })
                .thenCompose(orgId ->
                    // Delete the pending record
                    deleteById(request.getId())
                            .thenApply(v -> orgId));
    }

    // --- SignUpRequest persistence (internal) ---

    private CompletableFuture<SignUpRequest> save(SignUpRequest request) {
        return esAsyncClient.index(i -> i
                .index(SIGNUP_REQUEST_INDEX)
                .id(request.getId())
                .document(request)
                .refresh(Refresh.WaitFor))
                .thenApply(response -> request);
    }

    private CompletableFuture<SignUpRequest> findByToken(String verificationToken) {
        return esAsyncClient.search(s -> s
                .index(SIGNUP_REQUEST_INDEX)
                .query(q -> q.term(TermQuery.of(t -> t.field("verificationToken").value(verificationToken))))
                .size(1), SignUpRequest.class)
                .thenApply(response -> {
                    if (response.hits().hits().isEmpty()) {
                        return null;
                    }
                    return response.hits().hits().getFirst().source();
                });
    }

    private CompletableFuture<SignUpRequest> findByEmail(String email) {
        return esAsyncClient.search(s -> s
                .index(SIGNUP_REQUEST_INDEX)
                .query(q -> q.term(TermQuery.of(t -> t.field("email").value(email))))
                .size(1), SignUpRequest.class)
                .thenApply(response -> {
                    if (response.hits().hits().isEmpty()) {
                        return null;
                    }
                    return response.hits().hits().getFirst().source();
                });
    }

    private CompletableFuture<Void> deleteById(String id) {
        return esAsyncClient.delete(d -> d.index(SIGNUP_REQUEST_INDEX).id(id).refresh(Refresh.WaitFor))
                            .thenApply(response -> null);
    }

}
