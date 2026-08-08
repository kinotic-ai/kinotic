package org.kinotic.domain.internal.api.repositories;

import org.kinotic.domain.api.model.security.PendingVerification;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence base for short-lived, single-use token records. Layers token lookup and
 * expiry-aware, single-use consumption on top of {@link AbstractRepository} for any
 * {@link PendingVerification} type.
 *
 * @param <T> the pending-verification type managed by this repository
 */
public abstract class AbstractTokenVerificationRepository<T extends PendingVerification>
        extends AbstractRepository<T> {

    protected AbstractTokenVerificationRepository(String indexName,
                                                  Class<T> type,
                                                  CrudServiceTemplate crudServiceTemplate) {
        super(indexName, type, crudServiceTemplate);
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
