package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.repositories.TenantRepository;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.services.TenantService;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@RequiredArgsConstructor
public class DefaultTenantService implements TenantService {

    private final TenantRepository tenantRepository;
    private final ApplicationRepository applications;
    private final ApplicationStoreProvisioner provisioner;
    private final PasswordConfigurationService passwordConfigurationService;
    private final SecurityContext securityContext;

    @Override
    public Future<Tenant> createTenantIfNotExist(String applicationId, String name, String description) {
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        Validate.notBlank(name, "name cannot be blank");
        String organizationId = requireOrganizationId();
        String tenantId = DomainUtil.slugifyId(name);
        DomainUtil.validateTenantId(tenantId);
        Date now = new Date();
        Tenant tenant = new Tenant().setId(tenantId)
                                    .setOrganizationId(organizationId)
                                    .setApplicationId(applicationId)
                                    .setName(name)
                                    .setDescription(description)
                                    .setCreated(now)
                                    .setUpdated(now);
        return applications.requireById(applicationId, organizationId)
                           .compose(application -> tenantRepository.create(tenant, organizationId))
                           // create refuses an id the application's tenant holds already, made by an earlier call or
                           // one made at the same time, and that tenant is the one wanted
                           .recover(err -> err instanceof AlreadyExistsException
                                   ? tenantRepository.findById(applicationId, tenantId, organizationId)
                                                     .compose(stored -> stored != null ? Future.succeededFuture(stored) : Future.failedFuture(err))
                                   : Future.failedFuture(err));
    }

    @Override
    public Future<Tenant> findById(String applicationId, String tenantId) {
        return tenantRepository.findById(applicationId, tenantId, requireOrganizationId());
    }

    @Override
    public Future<Long> countForApplication(String applicationId) {
        return tenantRepository.countForApplication(applicationId, requireOrganizationId());
    }

    @Override
    public Future<Page<Tenant>> findAllForApplication(String applicationId, Pageable pageable) {
        return tenantRepository.findAllForApplication(applicationId, requireOrganizationId(), pageable);
    }

    @Override
    public Future<Page<Tenant>> findForApplicationByPrefix(String applicationId, String prefix, Pageable pageable) {
        return tenantRepository.findForApplicationByPrefix(applicationId, prefix, requireOrganizationId(), pageable);
    }

    @Override
    public Future<Void> deleteById(String applicationId, String tenantId) {
        // both ids name the password configuration deleted with the tenant, which without either would be the
        // application's or the organization's own
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        Validate.notBlank(tenantId, "tenantId cannot be blank");
        String organizationId = requireOrganizationId();
        // the graph first, then the record: a record that outlives its graph is deleted again, while grants that
        // outlived their record would come back with a tenant created under the same id
        return tenantRepository.findById(applicationId, tenantId, organizationId)
                               .compose(tenant -> tenant == null ? Future.succeededFuture() : provisioner.removeTenant(tenant))
                               .compose(v -> passwordConfigurationService.deleteById(PasswordConfiguration.idOf(organizationId, applicationId, tenantId), organizationId))
                               .compose(v -> tenantRepository.deleteById(applicationId, tenantId, organizationId));
    }

    private String requireOrganizationId() {
        return securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
    }
}
