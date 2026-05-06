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
import org.kinotic.os.api.model.iam.PendingRegistration;
import org.kinotic.os.api.services.OrganizationService;
import org.kinotic.os.api.services.iam.PendingRegistrationService;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultPendingRegistrationService implements PendingRegistrationService {

    static final String INDEX = "kinotic_pending_registration";
    private static final long DEFAULT_TTL_MS = 10 * 60 * 1000L; // 10 minutes

    private final ElasticsearchAsyncClient esAsyncClient;
    private final CrudServiceTemplate crudServiceTemplate;
    private final DefaultIamUserService userService;
    private final OrganizationService organizationService;

    @PostConstruct
    public void verifyIndexExists() {
        crudServiceTemplate.verifyIndexExists(INDEX);
    }

    @Override
    public CompletableFuture<PendingRegistration> create(PendingRegistration registration) {
        Validate.notBlank(registration.getOidcSubject(), "oidcSubject is required");
        Validate.notBlank(registration.getOidcConfigId(), "oidcConfigId is required");
        Validate.notBlank(registration.getEmail(), "email is required");
        Validate.notBlank(registration.getAuthScopeType(), "authScopeType is required");

        Date now = new Date();
        registration.setId(UUID.randomUUID().toString())
                    .setVerificationToken(UUID.randomUUID().toString())
                    .setCreated(now)
                    .setExpiresAt(new Date(now.getTime() + DEFAULT_TTL_MS));

        return esAsyncClient.index(i -> i
                .index(INDEX)
                .id(registration.getId())
                .document(registration)
                .refresh(Refresh.WaitFor))
                .thenApply(response -> registration);
    }

    @Override
    public CompletableFuture<PendingRegistration> findByToken(String verificationToken) {
        Validate.notBlank(verificationToken, "verificationToken is required");
        return esAsyncClient.search(s -> s
                .index(INDEX)
                .query(q -> q.term(TermQuery.of(t -> t.field("verificationToken").value(verificationToken))))
                .size(1), PendingRegistration.class)
                .thenApply(response -> response.hits().hits().isEmpty()
                        ? null : response.hits().hits().getFirst().source());
    }

    @Override
    public CompletableFuture<IamUser> complete(String verificationToken, Consumer<IamUser> finalizer) {
        return findValid(verificationToken).thenCompose(pending -> createUserFromPending(pending, finalizer));
    }

    @Override
    public CompletableFuture<IamUser> completeWithNewOrg(String verificationToken, String orgName, String orgDescription) {
        Validate.notBlank(orgName, "orgName is required");
        return findValid(verificationToken).thenCompose(pending -> {
            Organization org = new Organization()
                    .setName(orgName)
                    .setDescription(orgDescription);
            return organizationService.save(org)
                    .thenCompose(savedOrg -> {
                        // Promote the pending reg's scope to the new org so createUserFromPending wires it correctly
                        pending.setAuthScopeType("ORGANIZATION").setAuthScopeId(savedOrg.getId());
                        return createUserFromPending(pending, null)
                                .thenCompose(savedUser -> {
                                    savedOrg.setCreatedBy(savedUser.getId());
                                    return organizationService.save(savedOrg).thenApply(updatedOrg -> savedUser);
                                });
                    });
        });
    }

    private CompletableFuture<PendingRegistration> findValid(String verificationToken) {
        return findByToken(verificationToken).thenCompose(pending -> {
            if (pending == null) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        "Invalid or already consumed registration token."));
            }
            if (pending.getExpiresAt().before(new Date())) {
                return deleteById(pending.getId())
                        .thenCompose(v -> CompletableFuture.failedFuture(new IllegalArgumentException(
                                "Registration link has expired. Please start over.")));
            }
            return CompletableFuture.completedFuture(pending);
        });
    }

    private CompletableFuture<IamUser> createUserFromPending(PendingRegistration pending,
                                                             Consumer<IamUser> finalizer) {
        Date now = new Date();
        IamUser user = new IamUser()
                .setId(UUID.randomUUID().toString())
                .setEmail(pending.getEmail())
                .setDisplayName(pending.getDisplayName())
                .setAuthType(AuthType.OIDC)
                .setOidcSubject(pending.getOidcSubject())
                .setOidcConfigId(pending.getOidcConfigId())
                .setAuthScopeType(pending.getAuthScopeType())
                .setAuthScopeId(pending.getAuthScopeId())
                .setEnabled(true)
                .setCreated(now)
                .setUpdated(now);

        if (finalizer != null) {
            finalizer.accept(user);
        }

        return userService.save(user)
                .thenCompose(saved -> deleteById(pending.getId()).thenApply(v -> saved));
    }

    private CompletableFuture<Void> deleteById(String id) {
        return esAsyncClient.delete(d -> d.index(INDEX).id(id).refresh(Refresh.WaitFor))
                            .thenApply(response -> null);
    }
}
