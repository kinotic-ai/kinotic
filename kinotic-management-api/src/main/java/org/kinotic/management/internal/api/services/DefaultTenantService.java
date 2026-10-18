package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.repositories.TenantRepository;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.api.utils.HostLabelUtil;
import org.kinotic.domain.internal.api.services.AbstractApplicationScopedService;
import org.kinotic.management.api.services.TenantService;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class DefaultTenantService extends AbstractApplicationScopedService<Tenant> implements TenantService {

    // every tenant site label, <tenant>--<app>--<ui>, must leave room for at least this long a UI name
    private static final int MIN_UI_NAME_LENGTH = 1;

    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;

    public DefaultTenantService(TenantRepository repository,
                                ApplicationRepository applicationRepository,
                                SecurityContext securityContext) {
        super(repository, securityContext);
        this.tenantRepository = repository;
        this.applicationRepository = applicationRepository;
    }

    @Override
    public Future<Tenant> createTenantIfNotExist(String applicationId, String name, String description) {
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        Validate.notBlank(name, "name cannot be blank");
        String organizationId = requireOrganizationId();
        return tenantRepository.findById(DomainUtil.slugifyId(name), applicationId, organizationId)
                               .compose(tenant -> {
                                   Future<Tenant> ret;
                                   if (tenant != null) {
                                       ret = Future.succeededFuture(tenant);
                                   } else {
                                       Tenant newTenant = new Tenant().setOrganizationId(organizationId)
                                                                      .setApplicationId(applicationId)
                                                                      .setName(name)
                                                                      .setDescription(description);
                                       ret = save(newTenant);
                                   }
                                   return ret;
                               });
    }

    @Override
    protected Future<Void> beforeSave(Tenant entity) {
        Validate.notBlank(entity.getName(), "Tenant name cannot be blank");
        Validate.notBlank(entity.getApplicationId(), "Tenant applicationId cannot be blank");
        if (entity.getId() == null) {
            entity.setId(DomainUtil.slugifyId(entity.getName()));
            entity.setCreated(new Date());
        }
        // Validate only; re-minting an update's id would silently write a new document
        DomainUtil.validateTenantId(entity.getId());
        String organizationId = requireOrganizationId();
        // the id never changes after creation, so a tenant too long for a site label could never have a site
        int siteLabelLength = entity.getId().length() + HostLabelUtil.HOST_LABEL_SEPARATOR.length()
                              + entity.getApplicationId().length() + HostLabelUtil.HOST_LABEL_SEPARATOR.length()
                              + MIN_UI_NAME_LENGTH;
        Validate.isTrue(siteLabelLength <= HostLabelUtil.MAX_HOST_LABEL_LENGTH,
                        "The tenant id '%s' leaves no room for a UI name in its sites' labels, which DNS limits"
                                + " to %d characters; shorten the tenant name",
                        entity.getId(), HostLabelUtil.MAX_HOST_LABEL_LENGTH);
        entity.setUpdated(new Date());
        return applicationRepository.requireById(entity.getApplicationId(), organizationId).mapEmpty();
    }
}
