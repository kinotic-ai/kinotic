package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.domain.api.model.iam.SignUpRequest;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class SignUpRepository extends AbstractRepository<SignUpRequest> {

    public SignUpRepository(ElasticsearchAsyncClient esAsyncClient,
                            CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_signup_request", SignUpRequest.class, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<SignUpRequest> findByToken(String verificationToken) {
        return findFirst(b -> b.query(termFilter("verificationToken", verificationToken)));
    }

    public CompletableFuture<SignUpRequest> findByEmail(String email) {
        return findFirst(b -> b.query(termFilter("email", email)));
    }
}
