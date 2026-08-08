package org.kinotic.domain.internal.api.repositories;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.model.RefreshToken;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class RefreshTokenRepository extends AbstractRepository<RefreshToken> {

    public RefreshTokenRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_refresh_token", RefreshToken.class, crudServiceTemplate);
    }

    /** Finds the token whose plaintext hashes to {@code tokenHash}, or {@code null} if none matches. */
    public CompletableFuture<RefreshToken> findByTokenHash(String tokenHash) {
        return findFirst(b -> b.query(termFilter("tokenHash", tokenHash)));
    }

    /** Finds every unrevoked token of the given identity — one per live family. */
    public CompletableFuture<List<RefreshToken>> findActiveByIdentityId(String identityId) {
        return findAll(Pageable.ofSize(1000), b -> b.query(composeFilter(
                termFilter("identityId", identityId),
                termFilter("revoked", false))))
                .thenApply(Page::getContent);
    }

    /** Finds every token in the given rotation lineage. Used to revoke a family on reuse detection. */
    public CompletableFuture<List<RefreshToken>> findByFamilyId(String familyId) {
        return findAll(Pageable.ofSize(1000), b -> b.query(termFilter("familyId", familyId)))
                .thenApply(Page::getContent);
    }
}
