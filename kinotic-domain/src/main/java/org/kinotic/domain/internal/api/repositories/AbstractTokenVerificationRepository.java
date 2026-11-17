package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.security.PendingVerification;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.Date;
import java.util.Map;

/**
 * Persistence base for short-lived, single-use token records. Layers token lookup and
 * expiry-aware, single-use consumption on top of {@link AbstractRepository} for any
 * {@link PendingVerification} type.
 *
 * @param <T> the pending-verification type managed by this repository
 */
public abstract class AbstractTokenVerificationRepository<T extends PendingVerification>
        extends AbstractRepository<T> {

    /** How long a claim holds a record whose completion neither finished nor released it. */
    private static final long CLAIM_TTL_MS = 5 * 60 * 1000L;

    private static final String CLAIM_SCRIPT = """
            if (ctx._source.claimedUntil != null && ctx._source.claimedUntil > params.now) {
                ctx.op = 'noop'
            } else {
                ctx._source.claimedUntil = params.claimedUntil
            }""";

    private static final String RELEASE_SCRIPT = "ctx._source.remove('claimedUntil')";

    protected AbstractTokenVerificationRepository(String indexName,
                                                  Class<T> type,
                                                  CrudServiceTemplate crudServiceTemplate) {
        super(indexName, type, crudServiceTemplate);
    }

    /** Finds a record by its verification token, or {@code null} if none matches. */
    public Future<T> findByVerificationToken(String verificationToken) {
        return findFirst(b -> b.query(termFilter("verificationToken", verificationToken)));
    }

    /**
     * Finds a record by token and confirms it is still valid. Deletes the record and fails if it
     * has expired; fails if no record matches the token. Returns the live record otherwise —
     * callers consume it and delete it on success.
     */
    public Future<T> findValidByToken(String verificationToken) {
        return findByVerificationToken(verificationToken).compose(pending -> {
            if (pending == null) {
                return Future.failedFuture(new IllegalArgumentException(
                        "Invalid or already consumed token."));
            }
            if (pending.getExpiresAt().before(new Date())) {
                return deleteById(pending.getId()).compose(v -> Future.failedFuture(
                        new IllegalArgumentException("This link has expired. Please start over.")));
            }
            return Future.succeededFuture(pending);
        });
    }

    /**
     * Claims a live record for the one completion of its flow that calls this, for {@link #CLAIM_TTL_MS}: of several
     * callers presenting the same record at once exactly one gets it, and the others fail. The caller deletes the
     * record once its flow completes, and gives it up with {@link #release} when the flow fails; a claim neither
     * deleted nor released lapses, so the token can be presented again.
     *
     * @param pending the record {@link #findValidByToken} returned
     * @return a future emitting the record, failed with an {@link IllegalArgumentException} while another
     *         completion holds it
     */
    public Future<T> claim(T pending) {
        long now = System.currentTimeMillis();
        return crudServiceTemplate.scriptedUpdate(indexName, pending.getId(), CLAIM_SCRIPT,
                                                  Map.of("now", now, "claimedUntil", now + CLAIM_TTL_MS), null)
                                  .compose(claimed -> claimed
                                          ? Future.succeededFuture(pending)
                                          : Future.failedFuture(new IllegalArgumentException(
                                                  "This link is already being completed. Try again in a moment.")));
    }

    /** Gives up a claim taken with {@link #claim}, so the token can be presented again at once. */
    public Future<Void> release(T pending) {
        return crudServiceTemplate.scriptedUpdate(indexName, pending.getId(), RELEASE_SCRIPT, Map.of(), null)
                                  .mapEmpty();
    }
}
