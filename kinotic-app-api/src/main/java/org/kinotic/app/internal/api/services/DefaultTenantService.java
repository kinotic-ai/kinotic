package org.kinotic.app.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.api.security.SecurityExceptionFactory;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.repositories.TenantRepository;
import org.kinotic.app.api.services.TenantService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.app.api.services.security.TenantMemberService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@RequiredArgsConstructor
public class DefaultTenantService implements TenantService {

    private final SecurityContext securityContext;
    private final SecurityExceptionFactory securityExceptions;
    private final TenantRepository tenants;
    private final OidcConfigurationService oidcConfigurations;
    private final TenantMemberService tenantMembers;

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

    @Override
    public Future<OidcConfiguration> getSso() {
        return tenantOfCaller().compose(this::ssoOf);
    }

    @Override
    public Future<Tenant> configureSso(OidcConfiguration configuration, String ssoRoleId) {
        Validate.notNull(configuration, "configuration cannot be null");
        Validate.notBlank(configuration.getName(), "The configuration names its provider");
        Validate.notNull(configuration.getProvider(), "The configuration names its provider kind");
        Validate.notBlank(configuration.getClientId(), "The configuration names its client id");
        Validate.notBlank(configuration.getAuthority(), "The configuration names its authority");
        return tenantOfCaller()
                .compose(tenant -> requireGrantable(ssoRoleId).compose(v -> ssoOf(tenant)).map(current -> {
                    // the tenant's one configuration is replaced in place, so the users provisioned through it
                    // keep pointing at it
                    configuration.setOrganizationId(tenant.getOrganizationId())
                                 .setApplicationId(tenant.getApplicationId())
                                 .setTenantId(tenant.getTenantId())
                                 .setId(current != null ? current.getId() : null)
                                 .setCreated(current != null ? current.getCreated() : null)
                                 .setEnabled(true);
                    return tenant;
                }))
                .compose(tenant -> oidcConfigurations.saveTenantConfig(configuration)
                                                     .compose(saved -> tenants.saveSync(tenant.setSsoConfigId(saved.getId())
                                                                                              .setSsoRoleId(ssoRoleId)
                                                                                              .setUpdated(new Date()),
                                                                                        tenant.getOrganizationId())));
    }

    @Override
    public Future<Tenant> removeSso() {
        return tenantOfCaller().compose(tenant -> ssoOf(tenant)
                .compose(current -> current == null ? Future.succeededFuture() : oidcConfigurations.deleteTenantConfig(current))
                .compose(v -> tenants.saveSync(tenant.setSsoConfigId(null).setSsoRoleId(null).setUpdated(new Date()),
                                               tenant.getOrganizationId())));
    }

    // The tenant's configuration, or null while it names none
    private Future<OidcConfiguration> ssoOf(Tenant tenant) {
        return tenant.getSsoConfigId() == null
                ? Future.succeededFuture()
                : oidcConfigurations.findById(tenant.getSsoConfigId(), tenant.getOrganizationId());
    }

    // The role a provider's user is granted is one a grant on the tenant can name
    private Future<Void> requireGrantable(String roleId) {
        Future<Void> ret;
        if (roleId == null) {
            ret = Future.succeededFuture();
        } else {
            ret = tenantMembers.findRoles().map(roles -> {
                DomainUtil.requireRole(roles, roleId);
                return null;
            });
        }
        return ret;
    }

    // The caller's tenant record, which the application must have
    private Future<Tenant> tenantOfCaller() {
        ApplicationParticipant caller = securityContext.requireParticipant(ApplicationParticipant.class);
        String tenantId = DomainUtil.requireTenant(caller, securityExceptions);
        return tenants.findByTenantId(caller.getOrganizationId(), caller.getApplicationId(), tenantId).map(tenant -> {
            if (tenant == null) {
                throw new IllegalStateException("No tenant of the application has id " + tenantId);
            }
            return tenant;
        });
    }
}
