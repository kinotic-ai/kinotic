package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.model.RefreshToken;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Stores refresh tokens, each routed by the identity it authenticates, so every token of an identity, and of each
 * of its rotation lineages, lives on one shard and a revocation reads them all at one point in time.
 * <p>
 * Composition (rather than inheritance from {@link AbstractRepository}) is intentional, as in
 * {@link AbstractOrganizationScopedRepository}: a read or write by id alone cannot address a routed token, so
 * the forms that take only an id are not offered.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String INDEX_NAME = "kinotic_refresh_token";

    private static final String REVOKE_SCRIPT = "ctx._source.revoked = true";

    // a pass skips a token a rotation changes while it runs, which the next pass reads again
    private static final int MAX_REVOCATION_PASSES = 5;

    private static final String CONSUME_SCRIPT = """
            if (ctx._source.revoked == true) {
                ctx.op = 'noop'
            } else {
                ctx._source.revoked = true;
                ctx._source.replacedById = params.replacedById;
                ctx._source.lastUsedAt = params.lastUsedAt
            }""";

    private final CrudServiceTemplate crudServiceTemplate;

    @PostConstruct
    public void verifyIndexExists() {
        crudServiceTemplate.verifyIndexExists(INDEX_NAME);
    }

    /** Finds the token whose plaintext hashes to {@code tokenHash}, or {@code null} if none matches. */
    public Future<RefreshToken> findByTokenHash(String tokenHash) {
        return crudServiceTemplate.findFirst(INDEX_NAME, RefreshToken.class,
                                             b -> b.query(crudServiceTemplate.termFilter("tokenHash", tokenHash)));
    }

    /**
     * Finds every unrevoked token of the given identity: one per live family, and two for a family while a rotation
     * of it is under way.
     */
    public Future<List<RefreshToken>> findActiveByIdentityId(String identityId) {
        return crudServiceTemplate.search(INDEX_NAME, Pageable.ofSize(1000), RefreshToken.class,
                                          b -> b.routing(identityId).query(liveTokensOf(identityId)))
                                  .map(Page::getContent);
    }

    /** Stores {@code token}, visible to search when the future completes. */
    public Future<RefreshToken> saveSync(RefreshToken token) {
        Validate.notBlank(token.getIdentityId(), "identityId cannot be blank");
        return crudServiceTemplate.saveSync(INDEX_NAME, token.getId(), token, b -> b.routing(token.getIdentityId()))
                                  .map(token);
    }

    /**
     * Deletes token {@code id} of identity {@code identityId}, gone from search when the future completes.
     */
    public Future<Void> deleteSync(String id, String identityId) {
        Validate.notBlank(id, "id cannot be blank");
        Validate.notBlank(identityId, "identityId cannot be blank");
        return crudServiceTemplate.deleteByIdSync(INDEX_NAME, id, b -> b.routing(identityId))
                                  .mapEmpty();
    }

    /**
     * Redeems {@code token}: marks it revoked, used now and replaced by {@code replacedById}, which exactly one
     * caller does, however many present the token at once. False when the token was revoked already, which is a
     * reuse of a rotated token. The change is visible to search when the future completes.
     */
    public Future<Boolean> consume(RefreshToken token, String replacedById) {
        Validate.notBlank(replacedById, "replacedById cannot be blank");
        return crudServiceTemplate.scriptedUpdate(INDEX_NAME, token.getId(), CONSUME_SCRIPT,
                                                  Map.of("replacedById", replacedById, "lastUsedAt", new Date()),
                                                  b -> b.routing(token.getIdentityId()).refresh(Refresh.WaitFor));
    }

    /**
     * Revokes every live token of rotation lineage {@code familyId} of identity {@code identityId}, however many
     * the lineage holds; a family id of another identity's lineage revokes nothing. The change is visible to
     * search when the future completes.
     */
    public Future<Void> revokeFamily(String identityId, String familyId) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        Validate.notBlank(familyId, "familyId cannot be blank");
        return revokeMatching(identityId, crudServiceTemplate.composeFilter(liveTokensOf(identityId),
                                                                            crudServiceTemplate.termFilter("familyId", familyId)), 1);
    }

    /**
     * Revokes every live token of identity {@code identityId}, in every lineage. The change is visible to search
     * when the future completes.
     */
    public Future<Void> revokeAllFor(String identityId) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        return revokeMatching(identityId, liveTokensOf(identityId), 1);
    }

    private Query liveTokensOf(String identityId) {
        return crudServiceTemplate.composeFilter(crudServiceTemplate.termFilter("identityId", identityId),
                                                 crudServiceTemplate.termFilter("revoked", false));
    }

    // A pass reads the identity's one shard at a single point in time. A rotation writes its replacement before it
    // consumes the token it replaces, or deletes the replacement when the consume fails, so the pass finds either
    // the token still live, and a rotation changing it under the pass is a version conflict, or the replacement.
    // After a conflict the index is refreshed before the next pass: a pass that changed nothing refreshes nothing,
    // and the next would read the same stale tokens and skip them again.
    private Future<Void> revokeMatching(String identityId, Query live, int pass) {
        return crudServiceTemplate.updateByQuery(INDEX_NAME, b -> b.routing(identityId)
                                                                    .query(live)
                                                                    .script(s -> s.source(src -> src.scriptString(REVOKE_SCRIPT))))
                                  .compose(response -> {
                                      Future<Void> ret;
                                      if (response.versionConflicts() == null || response.versionConflicts() == 0) {
                                          ret = Future.succeededFuture();
                                      } else if (pass < MAX_REVOCATION_PASSES) {
                                          ret = crudServiceTemplate.syncIndex(INDEX_NAME)
                                                                   .compose(v -> revokeMatching(identityId, live, pass + 1));
                                      } else {
                                          ret = Future.failedFuture(new IllegalStateException(
                                                  "The refresh tokens could not be revoked while they were being rotated; try again"));
                                      }
                                      return ret;
                                  });
    }
}
