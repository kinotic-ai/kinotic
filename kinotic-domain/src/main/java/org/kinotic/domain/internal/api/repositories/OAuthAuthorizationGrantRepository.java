package org.kinotic.domain.internal.api.repositories;

import org.kinotic.domain.internal.api.model.OAuthAuthorizationGrant;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class OAuthAuthorizationGrantRepository extends AbstractRepository<OAuthAuthorizationGrant> {

    public OAuthAuthorizationGrantRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_oauth_authorization_grant", OAuthAuthorizationGrant.class, crudServiceTemplate);
    }

    /** Finds the grant whose authorization code hashes to {@code codeHash}, or {@code null} if none matches. */
    public CompletableFuture<OAuthAuthorizationGrant> findByCodeHash(String codeHash) {
        return findFirst(b -> b.query(termFilter("codeHash", codeHash)));
    }
}
