package org.kinotic.domain.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

/**
 * The tenants of the organizations' applications. A tenant is addressed by its organization, its application and its
 * id, so two applications of one organization may each have a tenant of the same id.
 */
@Component
@RequiredArgsConstructor
public class TenantRepository {

    private static final String INDEX_NAME = "kinotic_tenant";
    // organization and application ids hold no "--", so a document id names one (organization, application, tenant)
    private static final String DOCUMENT_ID_SEPARATOR = "--";

    private final CrudServiceTemplate crudServiceTemplate;

    @PostConstruct
    public void verifyIndexExists() {
        crudServiceTemplate.verifyIndexExists(INDEX_NAME);
    }

    /**
     * The tenant {@code tenantId} of application {@code applicationId} in organization {@code orgId}, or
     * {@code null} when that application has no such tenant.
     */
    public Future<Tenant> findById(String applicationId, String tenantId, String orgId) {
        return crudServiceTemplate.findById(INDEX_NAME, documentId(orgId, applicationId, tenantId), Tenant.class,
                                            b -> b.routing(orgId));
    }

    /**
     * Stores a new tenant, failing with {@link org.kinotic.core.api.exceptions.AlreadyExistsException} when its
     * application already has a tenant of its id.
     */
    public Future<Tenant> create(Tenant tenant) {
        String orgId = tenant.getOrganizationId();
        return crudServiceTemplate.create(INDEX_NAME, documentId(orgId, tenant.getApplicationId(), tenant.getId()), tenant,
                                          b -> b.routing(orgId))
                                  .map(tenant);
    }

    /** Deletes the tenant {@code tenantId} of application {@code applicationId}; no-op when there is none. */
    public Future<Void> deleteById(String applicationId, String tenantId, String orgId) {
        return crudServiceTemplate.deleteById(INDEX_NAME, documentId(orgId, applicationId, tenantId), b -> b.routing(orgId))
                                  .mapEmpty();
    }

    public Future<Long> countForApplication(String applicationId, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return crudServiceTemplate.count(INDEX_NAME, b -> b.routing(orgId).query(applicationFilter(applicationId, orgId)));
    }

    public Future<Page<Tenant>> findAllForApplication(String applicationId, String orgId, Pageable pageable) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return crudServiceTemplate.search(INDEX_NAME, pageable, Tenant.class,
                                          b -> b.routing(orgId).query(applicationFilter(applicationId, orgId)));
    }

    /**
     * The tenants of application {@code applicationId} in organization {@code orgId} whose name or id starts with
     * {@code prefix}, ignoring case; a blank prefix finds them all.
     */
    public Future<Page<Tenant>> findForApplicationByPrefix(String applicationId, String prefix, String orgId, Pageable pageable) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        Future<Page<Tenant>> ret;
        if (prefix == null || prefix.isBlank()) {
            ret = findAllForApplication(applicationId, orgId, pageable);
        } else {
            String text = prefix.trim();
            ret = crudServiceTemplate.search(INDEX_NAME, pageable, Tenant.class, b -> b.routing(orgId).query(Query.of(q -> q.bool(bq -> bq
                    .should(s -> s.prefix(p -> p.field("name").value(text).caseInsensitive(true)))
                    .should(s -> s.prefix(p -> p.field("id").value(text).caseInsensitive(true)))
                    .minimumShouldMatch("1")
                    .filter(applicationFilter(applicationId, orgId))))));
        }
        return ret;
    }

    public Future<Void> syncIndex() {
        return crudServiceTemplate.syncIndex(INDEX_NAME);
    }

    private Query applicationFilter(String applicationId, String orgId) {
        return crudServiceTemplate.composeFilter(crudServiceTemplate.termFilter("organizationId", orgId),
                                                 crudServiceTemplate.termFilter("applicationId", applicationId));
    }

    private static String documentId(String orgId, String applicationId, String tenantId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        Validate.notBlank(tenantId, "tenantId cannot be blank");
        return orgId + DOCUMENT_ID_SEPARATOR + applicationId + DOCUMENT_ID_SEPARATOR + tenantId;
    }
}
