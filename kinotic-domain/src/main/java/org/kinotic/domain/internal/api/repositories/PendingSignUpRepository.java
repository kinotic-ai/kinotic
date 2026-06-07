package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.domain.api.model.iam.PendingSignUp;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Persistence for {@link PendingSignUp} records over one index serving every sign-up flow.
 * Token lookup and single-use, expiry-aware consumption are inherited from
 * {@link AbstractTokenVerificationRepository}; this class adds the email lookup used to block
 * duplicate submissions.
 */
@Component
public class PendingSignUpRepository extends AbstractTokenVerificationRepository<PendingSignUp> {

    public PendingSignUpRepository(ElasticsearchAsyncClient esAsyncClient,
                                   CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_pending_signup", PendingSignUp.class, esAsyncClient, crudServiceTemplate);
    }

    /** Finds a pending sign-up by email, or {@code null} — used to block duplicate submissions. */
    public CompletableFuture<PendingSignUp> findByEmail(String email) {
        return findFirst(b -> b.query(termFilter("email", email)));
    }
}
