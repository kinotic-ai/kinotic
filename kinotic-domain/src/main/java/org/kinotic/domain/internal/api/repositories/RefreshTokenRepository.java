package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.model.RefreshToken;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class RefreshTokenRepository extends AbstractRepository<RefreshToken> {

    // A token is revoked in the shard operation that reads whether it already is, so of two rotations that
    // both read it live, the script lets exactly one through
    private static final String REVOKE = """
            if (ctx._source.revoked == true) {
                ctx.op = 'noop';
            } else {
                ctx._source.revoked = true;
            }
            """;
    private static final String CONSUME = """
            if (ctx._source.revoked == true) {
                ctx.op = 'noop';
            } else {
                ctx._source.revoked = true;
                ctx._source.lastUsedAt = params.lastUsedAt;
                ctx._source.replacedById = params.replacedById;
            }
            """;

    public RefreshTokenRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_refresh_token", RefreshToken.class, crudServiceTemplate);
    }

    /**
     * Revokes the token in favor of its replacement, recording when it was presented, unless it is revoked
     * already. Visible to search on completion.
     *
     * @param id           the token to consume
     * @param replacedById the token minted in its place
     * @param usedAt       when it was presented
     * @return true when this call revoked it, false when it was revoked already
     */
    public Future<Boolean> consume(String id, String replacedById, Instant usedAt) {
        return crudServiceTemplate.scriptedUpdateSync(indexName, id, CONSUME,
                                                      Map.of("replacedById", replacedById, "lastUsedAt", usedAt));
    }

    /**
     * Revokes the token unless it is revoked already. Visible to search on completion.
     *
     * @param id the token to revoke
     * @return true when this call revoked it, false when it was revoked already
     */
    public Future<Boolean> revoke(String id) {
        return crudServiceTemplate.scriptedUpdateSync(indexName, id, REVOKE, Map.of());
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
