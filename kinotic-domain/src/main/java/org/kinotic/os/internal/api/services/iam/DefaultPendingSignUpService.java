package org.kinotic.os.internal.api.services.iam;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.api.model.iam.PendingSignUp;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Internal ES CRUD for {@link PendingSignUp} records.
 * These are temporary records that exist only until the user verifies their email.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultPendingSignUpService implements PendingSignUpService {

    private static final String INDEX_NAME = "kinotic_pending_signup";

    private final ElasticsearchAsyncClient esAsyncClient;
    private final CrudServiceTemplate crudServiceTemplate;

    @PostConstruct
    @Override
    public void verifyIndexExists() {
        crudServiceTemplate.verifyIndexExists(INDEX_NAME);
    }

    @Override
    public CompletableFuture<PendingSignUp> save(PendingSignUp pendingSignUp) {
        return esAsyncClient.index(i -> i
                .index(INDEX_NAME)
                .id(pendingSignUp.getId())
                .document(pendingSignUp)
                .refresh(Refresh.WaitFor))
                .thenApply(response -> pendingSignUp);
    }

    @Override
    public CompletableFuture<PendingSignUp> findByToken(String verificationToken) {
        return esAsyncClient.search(s -> s
                .index(INDEX_NAME)
                .query(q -> q.term(TermQuery.of(t -> t.field("verificationToken").value(verificationToken))))
                .size(1), PendingSignUp.class)
                .thenApply(response -> {
                    if (response.hits().hits().isEmpty()) {
                        return null;
                    }
                    return response.hits().hits().getFirst().source();
                });
    }

    @Override
    public CompletableFuture<PendingSignUp> findByEmail(String email) {
        return esAsyncClient.search(s -> s
                .index(INDEX_NAME)
                .query(q -> q.term(TermQuery.of(t -> t.field("email").value(email))))
                .size(1), PendingSignUp.class)
                .thenApply(response -> {
                    if (response.hits().hits().isEmpty()) {
                        return null;
                    }
                    return response.hits().hits().getFirst().source();
                });
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        return esAsyncClient.delete(d -> d.index(INDEX_NAME).id(id).refresh(Refresh.WaitFor))
                            .thenApply(response -> null);
    }

}
