package org.kinotic.os.internal.services.iam;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.api.model.iam.PendingSignUp;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Internal ES CRUD for {@link PendingSignUp} records.
 * These are temporary records that exist only until the user verifies their email.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingSignUpStore {

    private static final String INDEX_NAME = "kinotic_pending_signup";

    private final ElasticsearchAsyncClient esAsyncClient;

    @PostConstruct
    public void verifyIndexExists() {
        try {
            boolean exists = esAsyncClient.indices()
                                          .exists(b -> b.index(INDEX_NAME))
                                          .get()
                                          .value();
            if (!exists) {
                throw new IllegalStateException(
                        "Elasticsearch index '" + INDEX_NAME + "' does not exist. "
                        + "Did you forget to add a migration in kinotic-migration/src/main/resources/migrations/?");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to verify existence of index '" + INDEX_NAME + "'", e);
        }
    }

    public CompletableFuture<PendingSignUp> save(PendingSignUp pendingSignUp) {
        return esAsyncClient.index(i -> i
                .index(INDEX_NAME)
                .id(pendingSignUp.getId())
                .document(pendingSignUp)
                .refresh(Refresh.WaitFor))
                .thenApply(response -> pendingSignUp);
    }

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

    public CompletableFuture<Void> deleteById(String id) {
        return esAsyncClient.delete(d -> d.index(INDEX_NAME).id(id).refresh(Refresh.WaitFor))
                            .thenApply(response -> null);
    }

}
