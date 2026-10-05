package org.kinotic.domain.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.repositories.TenantRepository;
import org.kinotic.domain.api.services.TenantService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@RequiredArgsConstructor
public class DefaultTenantService implements TenantService {

    private final SecurityContext securityContext;
    private final TenantRepository tenants;

    @Override
    public Future<Tenant> getTenant() {
        return tenantOfCaller();
    }

    @Override
    public Future<Tenant> rename(String name) {
        Validate.notBlank(name, "name cannot be blank");
        return tenantOfCaller().compose(tenant -> tenants.saveSync(tenant.setName(name.trim()).setUpdated(new Date()),
                                                                   tenant.getOrganizationId()));
    }

    // The caller's tenant record, which the application must have
    private Future<Tenant> tenantOfCaller() {
        ApplicationParticipant caller = securityContext.requireParticipant(ApplicationParticipant.class);
        String tenantId = DomainUtil.requireTenant(caller);
        return tenants.findByTenantId(caller.getOrganizationId(), caller.getApplicationId(), tenantId).map(tenant -> {
            if (tenant == null) {
                throw new IllegalStateException("No tenant of the application has id " + tenantId);
            }
            return tenant;
        });
    }
}
