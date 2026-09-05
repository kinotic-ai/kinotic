package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.model.RefreshToken;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RefreshTokenRepository extends AbstractRepository<RefreshToken> {

    public RefreshTokenRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_refresh_token", RefreshToken.class, crudServiceTemplate);
    }

    /** Finds the token whose plaintext hashes to {@code tokenHash}, or {@code null} if none matches. */
    public Future<RefreshToken> findByTokenHash(String tokenHash) {
        return findFirst(b -> b.query(termFilter("tokenHash", tokenHash)));
    }

    /** Finds every unrevoked token of the given identity — one per live family. */
    public Future<List<RefreshToken>> findActiveByIdentityId(String identityId) {
        return findAll(Pageable.ofSize(1000), b -> b.query(composeFilter(
                termFilter("identityId", identityId),
                termFilter("revoked", false))))
                .map(Page::getContent);
    }

    /** Finds every token in the given rotation lineage. Used to revoke a family on reuse detection. */
    public Future<List<RefreshToken>> findByFamilyId(String familyId) {
        return findAll(Pageable.ofSize(1000), b -> b.query(termFilter("familyId", familyId)))
                .map(Page::getContent);
    }
}
