package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.iam.PendingInvite;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Persistence for {@link PendingInvite} records. Token lookup and single-use, expiry-aware
 * consumption are inherited from {@link AbstractTokenVerificationRepository}; this class adds the
 * scope-aware lookups used to block duplicate invitations and to list pending invites for a scope.
 */
@Component
public class PendingInviteRepository extends AbstractTokenVerificationRepository<PendingInvite> {

    public PendingInviteRepository(ElasticsearchAsyncClient esAsyncClient,
                                   CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_iam_pending_invite", PendingInvite.class, esAsyncClient, crudServiceTemplate);
    }

    /**
     * Finds the pending invite for {@code email} at the given scope, or {@code null} — used to
     * block a second invitation while one is already outstanding.
     */
    public CompletableFuture<PendingInvite> findByEmailAndScope(String email, String organizationId, String applicationId) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("email", email),
                scopeFilter(organizationId, applicationId))));
    }

    /** Lists pending invites at the given scope. */
    public CompletableFuture<Page<PendingInvite>> findByScope(String organizationId, String applicationId, Pageable pageable) {
        return findAll(pageable, b -> b.query(scopeFilter(organizationId, applicationId)));
    }

    /**
     * Structural scope filter against the typed scope fields on the document:
     * <ul>
     *   <li>{@code applicationId} null → matches that org AND {@code applicationId} is missing (ORG invite)</li>
     *   <li>{@code applicationId} set → matches both fields (APP invite)</li>
     * </ul>
     */
    private Query scopeFilter(String organizationId, String applicationId) {
        if (applicationId == null) {
            return composeFilter(
                    termFilter("organizationId", organizationId),
                    missingFilter("applicationId"));
        }
        return composeFilter(
                termFilter("organizationId", organizationId),
                termFilter("applicationId", applicationId));
    }
}
