package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.domain.api.model.iam.PendingVerification;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence base for {@link PendingVerification} records: token lookup plus single-use,
 * expiry-aware consumption, shared by every tokenized-link flow (sign-up, member invite).
 * Each concrete subclass binds the index it serves.
 *
 * @param <T> the pending-verification record type managed by this repository
 */
public abstract class AbstractTokenVerificationRepository<T extends PendingVerification> extends AbstractRepository<T> {

    protected AbstractTokenVerificationRepository(String indexName,
                                                  Class<T> type,
                                                  ElasticsearchAsyncClient esAsyncClient,
                                                  CrudServiceTemplate crudServiceTemplate) {
        super(indexName, type, esAsyncClient, crudServiceTemplate);
    }

    /** Finds a record by its verification token, or {@code null} if none matches. */
    public CompletableFuture<T> findByVerificationToken(String verificationToken) {
        return findFirst(b -> b.query(termFilter("verificationToken", verificationToken)));
    }

    /**
     * Finds a record by token and confirms it is still valid. Deletes the record and fails if it
     * has expired; fails if no record matches the token. Returns the live record otherwise —
     * callers consume it and delete it on success.
     */
    public CompletableFuture<T> findValidByToken(String verificationToken) {
        return findByVerificationToken(verificationToken).thenCompose(pending -> {
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
