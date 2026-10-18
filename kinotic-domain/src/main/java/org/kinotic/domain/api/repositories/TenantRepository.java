package org.kinotic.domain.api.repositories;

import io.vertx.core.Future;
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
}
