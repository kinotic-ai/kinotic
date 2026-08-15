package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.ApplicationScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

/**
 * Repository tier for entities that belong to an application within an organization.
 * Adds {@code applicationId}-scoped query helpers, each with an {@code orgId}-aware overload.
 * {@code orgId} parameters are required; callers without an organization context should use
 * the no-arg form.
 */
public abstract class AbstractApplicationScopedRepository<T extends ApplicationScoped<String>>
        extends AbstractOrganizationScopedRepository<T> {

    public AbstractApplicationScopedRepository(String indexName,
                                               Class<T> type,
                                               CrudServiceTemplate crudServiceTemplate) {
        super(indexName, type, crudServiceTemplate);
    }

    public Future<Long> countForApplication(String applicationId, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return count(b -> b.routing(orgId).query(composeOrgFilter(orgId, applicationIdFilter(applicationId))));
    }

    public Future<Page<T>> findAllForApplication(String applicationId, String orgId, Pageable pageable) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findAll(pageable, b -> b.routing(orgId).query(composeOrgFilter(orgId, applicationIdFilter(applicationId))));
    }

    protected Query applicationIdFilter(String applicationId) {
        return termFilter("applicationId", applicationId);
    }
}
