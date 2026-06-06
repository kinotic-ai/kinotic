package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.domain.api.model.iam.PendingSignUp;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence for {@link PendingSignUp} records: token and email lookup plus single-use,
 * expiry-aware consumption, over one index serving every sign-up flow.
 */
@Component
public class PendingSignUpRepository extends AbstractRepository<PendingSignUp> {

    public PendingSignUpRepository(ElasticsearchAsyncClient esAsyncClient,
                                   CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_pending_signup", PendingSignUp.class, esAsyncClient, crudServiceTemplate);
    }

    /** Finds a pending sign-up by its verification token, or {@code null} if none matches. */
    public CompletableFuture<PendingSignUp> findByToken(String verificationToken) {
        return findFirst(b -> b.query(termFilter("verificationToken", verificationToken)));
    }

    /** Finds a pending sign-up by email, or {@code null} — used to block duplicate submissions. */
    public CompletableFuture<PendingSignUp> findByEmail(String email) {
        return findFirst(b -> b.query(termFilter("email", email)));
    }

    /**
     * Finds a pending sign-up by token and confirms it is still valid. Deletes the record and
     * fails if it has expired; fails if no record matches the token. Returns the live record
     * otherwise — callers consume it and delete it on success.
     */
    public CompletableFuture<PendingSignUp> findValidByToken(String verificationToken) {
        return findByToken(verificationToken).thenCompose(pending -> {
            if (pending == null) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        "Invalid or already consumed token."));
            }
            if (pending.getExpiresAt().before(new Date())) {
                return deleteById(pending.getId()).thenCompose(v -> CompletableFuture.failedFuture(
                        new IllegalArgumentException("This link has expired. Please start over.")));
            }
            return CompletableFuture.completedFuture(pending);
        });
    }
}
