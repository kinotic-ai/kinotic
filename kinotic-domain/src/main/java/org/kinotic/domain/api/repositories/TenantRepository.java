package org.kinotic.domain.api.repositories;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Page;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.internal.api.repositories.AbstractApplicationScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class TenantRepository extends AbstractApplicationScopedRepository<Tenant> {

    public TenantRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_tenant", Tenant.class, crudServiceTemplate);
    }

    /**
     * The tenant {@code tenantId} of application {@code applicationId} in organization {@code orgId}, or
     * {@code null} when that application has no such tenant.
     */
    public Future<Tenant> findById(String tenantId, String applicationId, String orgId) {
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        return findById(tenantId, orgId)
                .map(tenant -> tenant != null && Objects.equals(applicationId, tenant.getApplicationId()) ? tenant : null);
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
            ret = findAll(pageable, b -> b.routing(orgId).query(Query.of(q -> q.bool(bq -> bq
                    .should(s -> s.prefix(p -> p.field("name").value(text).caseInsensitive(true)))
                    .should(s -> s.prefix(p -> p.field("id").value(text).caseInsensitive(true)))
                    .minimumShouldMatch("1")
                    .filter(composeOrgFilter(orgId, applicationIdFilter(applicationId)))))));
        }
        return ret;
    }
}
