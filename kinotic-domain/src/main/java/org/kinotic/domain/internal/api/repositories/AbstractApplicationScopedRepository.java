package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.ApplicationScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

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
        return countForApplication(applicationId, orgId, null);
    }

    /**
     * Counts the application's documents among the given ids; every document when {@code ids} is null, none
     * when it is empty.
     */
    public Future<Long> countForApplication(String applicationId, String orgId, Collection<String> ids) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return count(b -> b.routing(orgId).query(composeOrgFilter(orgId, applicationIdFilter(applicationId), idsFilter(ids))));
    }

    public Future<Page<T>> findAllForApplication(String applicationId, String orgId, Pageable pageable) {
        return findAllForApplication(applicationId, orgId, null, pageable);
    }

    /**
     * The page of the application's documents among the given ids; every document when {@code ids} is null,
     * none when it is empty.
     */
    public Future<Page<T>> findAllForApplication(String applicationId, String orgId, Collection<String> ids, Pageable pageable) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findAll(pageable, b -> b.routing(orgId).query(composeOrgFilter(orgId, applicationIdFilter(applicationId), idsFilter(ids))));
    }

    /**
     * The ids of the applications containing the documents with the given ids in {@code orgId}.
     */
    public Future<Set<String>> findApplicationIdsOf(Collection<String> ids, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        Future<Set<String>> ret;
        if (ids.isEmpty()) {
            ret = Future.succeededFuture(Set.of());
        } else {
            ret = findAll(Pageable.ofSize(ids.size()), b -> b.routing(orgId).query(composeOrgFilter(orgId, idsFilter(ids))))
                    .map(page -> page.getContent().stream().map(ApplicationScoped::getApplicationId).collect(Collectors.toSet()));
        }
        return ret;
    }

    protected Query applicationIdFilter(String applicationId) {
        return termFilter("applicationId", applicationId);
    }
}
