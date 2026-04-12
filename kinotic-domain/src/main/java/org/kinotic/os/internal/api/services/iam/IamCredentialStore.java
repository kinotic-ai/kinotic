package org.kinotic.os.internal.api.services.iam;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.kinotic.os.internal.api.model.iam.IamCredential;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class IamCredentialStore {

    private static final String INDEX_NAME = "kinotic_iam_credential";

    private final ElasticsearchAsyncClient esAsyncClient;
    private final CrudServiceTemplate crudServiceTemplate;

    @PostConstruct
    public void verifyIndexExists() {
        crudServiceTemplate.verifyIndexExists(INDEX_NAME);
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
