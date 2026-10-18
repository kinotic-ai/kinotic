package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.identity.Membership;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MembershipRepository extends AbstractRepository<Membership> {

    // the most organizations one account is a member of
    private static final int MAX_MEMBERSHIPS = 100;
    // the most members one organization is searched across
    private static final int MAX_MEMBERS = 10_000;

    public MembershipRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_membership", Membership.class, crudServiceTemplate);
    }

    /** The memberships of organization {@code organizationId}, or of the platform when it is {@code null}, oldest first. */
    public Future<Page<Membership>> findByOrganization(String organizationId, Pageable pageable) {
        return findAll(pageable, b -> b.query(organizationFilter(organizationId))
                                       .sort(s -> s.field(f -> f.field("created"))));
    }

    /** Every membership of organization {@code organizationId}, or of the platform when it is {@code null}, oldest first. */
    public Future<List<Membership>> findAllByOrganization(String organizationId) {
        return findByOrganization(organizationId, Pageable.ofSize(MAX_MEMBERS)).map(Page::getContent);
    }

    /** Every membership of account {@code identityId}, oldest first. */
    public Future<List<Membership>> findByIdentity(String identityId) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        return findAll(Pageable.ofSize(MAX_MEMBERSHIPS), b -> b.query(termFilter("identityId", identityId))
                                                                .sort(s -> s.field(f -> f.field("created"))))
                .map(Page::getContent);
    }

    private Query organizationFilter(String organizationId) {
        return organizationId == null ? missingFilter("organizationId") : termFilter("organizationId", organizationId);
    }
}
