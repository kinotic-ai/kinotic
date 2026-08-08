package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.PendingInvite;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class PendingInviteRepository extends AbstractTokenVerificationRepository<PendingInvite> {

    public PendingInviteRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_pending_invite", PendingInvite.class, crudServiceTemplate);
    }

    public CompletableFuture<PendingInvite> findByEmailAndScope(String email,
                                                                String organizationId,
                                                                String applicationId) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("email", DomainUtil.normalizeEmail(email)),
                scopeFilter(organizationId, applicationId))));
    }

    /**
     * Lists the live (unexpired) invitations for the given scope. Expired records are excluded
     * here rather than deleted — physical cleanup still happens on consumption via
     * {@link #findValidByToken}.
     */
    public CompletableFuture<Page<PendingInvite>> findByScope(String organizationId,
                                                              String applicationId,
                                                              Pageable pageable) {
        return findAll(pageable, b -> b.query(composeFilter(
                scopeFilter(organizationId, applicationId),
                notExpiredFilter())));
    }

    /**
     * Structural scope filter: {@code organizationId} is always set on an invite;
     * {@code applicationId} null means an org-member invite, set means an app-member invite.
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

    private Query notExpiredFilter() {
        return Query.of(q -> q.range(r -> r.date(d -> d.field("expiresAt").gt("now"))));
    }
}
