package org.kinotic.domain.api.repositories;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.repositories.AbstractApplicationScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

/**
 * Stores the tenants of every application, one row per tenant, keyed by {@link DomainUtil#createTenantId}.
 */
@Component
public class TenantRepository extends AbstractApplicationScopedRepository<Tenant> {

    public TenantRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_tenant", Tenant.class, crudServiceTemplate);
    }

    /**
     * The tenant of an application by the id its users carry.
     *
     * @param organizationId the application's organization
     * @param applicationId  the application
     * @param tenantId       the tenant's id within the application
     * @return the tenant, or null when the application has none of the id
     */
    public Future<Tenant> findByTenantId(String organizationId, String applicationId, String tenantId) {
        return findById(DomainUtil.createTenantId(new ApplicationKey(organizationId, applicationId), tenantId), organizationId);
    }
}
