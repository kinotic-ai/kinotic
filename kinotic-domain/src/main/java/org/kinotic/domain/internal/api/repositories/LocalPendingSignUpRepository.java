package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.domain.api.model.iam.LocalPendingSignUp;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class LocalPendingSignUpRepository extends PendingSignUpRepository<LocalPendingSignUp> {

    public LocalPendingSignUpRepository(ElasticsearchAsyncClient esAsyncClient,
                                        CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_local_pending_signup", LocalPendingSignUp.class, esAsyncClient, crudServiceTemplate);
    }

    /** Finds a pending local sign-up by email, or {@code null} — used to block duplicate submissions. */
    public CompletableFuture<LocalPendingSignUp> findByEmail(String email) {
        return findFirst(b -> b.query(termFilter("email", email)));
    }
}
