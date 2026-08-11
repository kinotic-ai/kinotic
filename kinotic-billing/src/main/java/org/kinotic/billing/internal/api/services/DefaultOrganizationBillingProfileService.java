package org.kinotic.billing.internal.api.services;

import org.apache.commons.lang3.Validate;
import org.kinotic.billing.api.model.OrganizationBillingProfile;
import org.kinotic.billing.api.services.OrganizationBillingProfileService;
import org.kinotic.billing.internal.api.repositories.OrganizationBillingProfileRepository;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.internal.api.services.AbstractOrganizationScopedService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultOrganizationBillingProfileService extends AbstractOrganizationScopedService<OrganizationBillingProfile>
        implements OrganizationBillingProfileService {

    public DefaultOrganizationBillingProfileService(OrganizationBillingProfileRepository repository,
                                                    SecurityContext securityContext) {
        super(repository, securityContext);
    }

    @Override
    protected CompletableFuture<Void> beforeSave(OrganizationBillingProfile value) {
        Validate.notNull(value, "profile cannot be null");
        Validate.notBlank(value.getId(), "profile id must be set");
        Validate.isTrue(value.getId().equals(value.getOrganizationId()),
                        "OrganizationBillingProfile id '%s' must equal organizationId '%s'",
                        value.getId(), value.getOrganizationId());
        if (value.getCreated() == null) {
            value.setCreated(new Date());
        }
        value.setUpdated(new Date());
        return CompletableFuture.completedFuture(null);
    }
}
