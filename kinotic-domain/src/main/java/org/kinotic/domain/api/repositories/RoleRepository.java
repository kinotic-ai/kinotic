package org.kinotic.domain.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.Role;
import org.kinotic.domain.internal.api.repositories.AbstractOrganizationScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

/**
 * The custom roles of the organizations and of their applications.
 */
@Component
public class RoleRepository extends AbstractOrganizationScopedRepository<Role> {

    private static final String APPLICATION_ID_FIELD = "applicationId";

    public RoleRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_role", Role.class, crudServiceTemplate);
    }

    /**
     * The page of the organization's own roles: those of none of its applications.
     */
    public Future<Page<Role>> findAllOfOrganization(String orgId, Pageable pageable) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        Query ofNoApplication = Query.of(q -> q.bool(b -> b.mustNot(m -> m.exists(e -> e.field(APPLICATION_ID_FIELD)))));
        return findAll(pageable, b -> b.routing(orgId).query(composeOrgFilter(orgId, ofNoApplication)));
    }

    /**
     * The page of the roles of application {@code applicationId} in organization {@code orgId}.
     */
    public Future<Page<Role>> findAllForApplication(String applicationId, String orgId, Pageable pageable) {
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findAll(pageable, b -> b.routing(orgId).query(composeOrgFilter(orgId, termFilter(APPLICATION_ID_FIELD, applicationId))));
    }

    /**
     * Deletes every role of application {@code applicationId} in organization {@code orgId}, waiting for the
     * deletions to be visible in search results.
     */
    public Future<Void> deleteAllForApplication(String applicationId, String orgId) {
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        Validate.notBlank(orgId, "orgId cannot be blank");
        return crudServiceTemplate.deleteByQuery(indexName, b -> b.routing(orgId)
                                                                  .refresh(true)
                                                                  .query(composeOrgFilter(orgId, termFilter(APPLICATION_ID_FIELD, applicationId))))
                                  .mapEmpty();
    }
}
