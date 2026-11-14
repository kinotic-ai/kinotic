package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.model.RefreshToken;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class RefreshTokenRepository extends AbstractRepository<RefreshToken> {

    private static final String REVOKE_SCRIPT = "ctx._source.revoked = true";

    // a pass skips a token a rotation consumes while it runs, whose replacement the next pass finds
    private static final int MAX_REVOCATION_PASSES = 5;

    private static final String CONSUME_SCRIPT = """
            if (ctx._source.revoked == true) {
                ctx.op = 'noop'
            } else {
                ctx._source.revoked = true;
                ctx._source.replacedById = params.replacedById;
                ctx._source.lastUsedAt = params.lastUsedAt
            }""";

    public RefreshTokenRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_refresh_token", RefreshToken.class, crudServiceTemplate);
    }

    /** Finds the token whose plaintext hashes to {@code tokenHash}, or {@code null} if none matches. */
    public Future<RefreshToken> findByTokenHash(String tokenHash) {
        return findFirst(b -> b.query(termFilter("tokenHash", tokenHash)));
    }

    /**
     * Finds every unrevoked token of the given identity: one per live family, and two for a family while a rotation
     * of it is under way.
     */
    public Future<List<RefreshToken>> findActiveByIdentityId(String identityId) {
        return findAll(Pageable.ofSize(1000), b -> b.query(composeFilter(
                termFilter("identityId", identityId),
                termFilter("revoked", false))))
                .map(Page::getContent);
    }

    /**
     * Redeems token {@code id}: marks it revoked, used now and replaced by {@code replacedById}, which exactly one
     * caller does, however many present the token at once. False when the token was revoked already, which is a
     * reuse of a rotated token. The change is visible to search when the future completes.
     */
    public Future<Boolean> consume(String id, String replacedById) {
        Validate.notBlank(id, "id cannot be blank");
        Validate.notBlank(replacedById, "replacedById cannot be blank");
        return crudServiceTemplate.scriptedUpdateSync(indexName, id, CONSUME_SCRIPT,
                                                      Map.of("replacedById", replacedById, "lastUsedAt", new Date()));
    }

    /**
     * Revokes every live token of rotation lineage {@code familyId} of identity {@code identityId}, however many
     * the lineage holds; a family id of another identity's lineage revokes nothing. The change is visible to
     * search when the future completes.
     */
    public Future<Void> revokeFamily(String identityId, String familyId) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        Validate.notBlank(familyId, "familyId cannot be blank");
        return revokeMatching(composeFilter(termFilter("identityId", identityId),
                                            termFilter("familyId", familyId),
                                            termFilter("revoked", false)));
    }

    /**
     * Revokes every live token of identity {@code identityId}, in every lineage. The change is visible to search
     * when the future completes.
     */
    public Future<Void> revokeAllFor(String identityId) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        return revokeMatching(composeFilter(termFilter("identityId", identityId),
                                            termFilter("revoked", false)));
    }

    // A rotation writes its replacement before it consumes the token it replaces, so a token that changed under a
    // pass was consumed by a rotation whose replacement is already searchable and is revoked by the pass that follows
    private Future<Void> revokeMatching(Query live) {
        return revokeMatching(live, 1);
    }

    private Future<Void> revokeMatching(Query live, int pass) {
        return crudServiceTemplate.updateByQuery(indexName, b -> b.query(live)
                                                                   .script(s -> s.source(src -> src.scriptString(REVOKE_SCRIPT))))
                                  .compose(response -> {
                                      Future<Void> ret;
                                      if (response.versionConflicts() == null || response.versionConflicts() == 0) {
                                          ret = Future.succeededFuture();
                                      } else if (pass < MAX_REVOCATION_PASSES) {
                                          ret = revokeMatching(live, pass + 1);
                                      } else {
                                          ret = Future.failedFuture(new IllegalStateException(
                                                  "The refresh tokens could not be revoked while they were being rotated; try again"));
                                      }
                                      return ret;
                                  });
    }
}
