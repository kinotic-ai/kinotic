package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.internal.api.model.RefreshToken;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class RefreshTokenRepository extends AbstractRepository<RefreshToken> {

    public RefreshTokenRepository(ElasticsearchAsyncClient esAsyncClient,
                                  CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_refresh_token", RefreshToken.class, esAsyncClient, crudServiceTemplate);
    }

    /** Finds the token whose plaintext hashes to {@code tokenHash}, or {@code null} if none matches. */
    public CompletableFuture<RefreshToken> findByTokenHash(String tokenHash) {
        return findAll(Pageable.create(0, 1, Sort.unsorted()), b -> b
                .query(q -> q.term(t -> t.field("tokenHash").value(tokenHash))))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    /** Finds every token in the given rotation lineage. Used to revoke a family on reuse detection. */
    public CompletableFuture<List<RefreshToken>> findByFamilyId(String familyId) {
        return findAll(Pageable.create(0, 1000, Sort.unsorted()), b -> b
                .query(q -> q.term(t -> t.field("familyId").value(familyId))))
                .thenApply(Page::getContent);
    }
}
