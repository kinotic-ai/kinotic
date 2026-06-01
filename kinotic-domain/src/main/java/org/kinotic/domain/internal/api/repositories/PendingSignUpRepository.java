package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.domain.api.model.iam.PendingSignUp;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

/**
 * Shared persistence for {@link PendingSignUp} records: token lookup and single-use,
 * expiry-aware consumption layered on {@link AbstractRepository}'s CRUD. Concrete subclasses
 * bind the index name and the concrete subtype.
 *
 * @param <T> the concrete {@link PendingSignUp} subtype this repository stores
 */
public abstract class PendingSignUpRepository<T extends PendingSignUp> extends AbstractRepository<T> {

    protected PendingSignUpRepository(String indexName,
                                      Class<T> type,
                                      ElasticsearchAsyncClient esAsyncClient,
                                      CrudServiceTemplate crudServiceTemplate) {
        super(indexName, type, esAsyncClient, crudServiceTemplate);
    }

    /** Finds a pending sign-up by its verification token, or {@code null} if none matches. */
    public CompletableFuture<T> findByToken(String verificationToken) {
        return findFirst(b -> b.query(termFilter("verificationToken", verificationToken)));
    }

    /**
     * Finds a pending sign-up by token and confirms it is still valid. Deletes the record and
     * fails if it has expired; fails if no record matches the token. Returns the live record
     * otherwise — callers consume it and are responsible for deleting it on success.
     */
    public CompletableFuture<T> findValidByToken(String verificationToken) {
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
