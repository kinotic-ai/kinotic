package org.kinotic.os.internal.api.services.iam;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.internal.model.iam.IamCredential;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class IamCredentialStore {

    private static final String INDEX_NAME = "kinotic_iam_credential";

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

    public CompletableFuture<IamCredential> findById(String userId) {
        return esAsyncClient.get(g -> g.index(INDEX_NAME).id(userId), IamCredential.class)
                            .thenApply(response -> {
                                if (response.found()) {
                                    return response.source();
                                }
                                return null;
                            });
    }

    public CompletableFuture<IamCredential> save(IamCredential credential) {
        return esAsyncClient.index(i -> i
                .index(INDEX_NAME)
                .id(credential.getId())
                .document(credential)
                .refresh(Refresh.WaitFor))
                .thenApply(response -> credential);
    }

    public CompletableFuture<Void> deleteById(String userId) {
        return esAsyncClient.delete(d -> d.index(INDEX_NAME).id(userId))
                            .thenApply(response -> null);
    }

}
