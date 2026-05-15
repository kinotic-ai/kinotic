package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
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
        return findAll(Pageable.create(0, 1, Sort.unsorted()), b -> b
                .query(q -> q.term(TermQuery.of(t -> t.field("verificationToken").value(verificationToken)))))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    public CompletableFuture<SignUpRequest> findByEmail(String email) {
        return findAll(Pageable.create(0, 1, Sort.unsorted()), b -> b
                .query(q -> q.term(TermQuery.of(t -> t.field("email").value(email)))))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }
}
