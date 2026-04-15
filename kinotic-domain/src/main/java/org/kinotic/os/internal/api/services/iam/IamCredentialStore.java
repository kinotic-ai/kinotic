package org.kinotic.os.internal.api.services.iam;

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

    private final CrudServiceTemplate crudServiceTemplate;

    @PostConstruct
    public void verifyIndexExists() {
        crudServiceTemplate.verifyIndexExists(INDEX_NAME);
    }

    public CompletableFuture<IamCredential> findById(String userId) {
        return crudServiceTemplate.findById(INDEX_NAME, userId, IamCredential.class, null);
    }

    public CompletableFuture<IamCredential> save(IamCredential credential) {
        return crudServiceTemplate.save(INDEX_NAME,
                                        credential.getId(),
                                        credential,
                                        b -> b.refresh(Refresh.WaitFor))
                                  .thenApply(response -> credential);
    }

    public CompletableFuture<Void> deleteById(String userId) {
        return crudServiceTemplate.deleteById(INDEX_NAME, userId, null)
                                  .thenApply(response -> null);
    }

}
