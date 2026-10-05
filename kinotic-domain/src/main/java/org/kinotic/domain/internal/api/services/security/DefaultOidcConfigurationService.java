package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.BaseOidcConfiguration;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.repositories.TenantRepository;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.internal.api.repositories.OidcConfigurationRepository;
import org.kinotic.domain.internal.api.services.AbstractOrganizationScopedService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.OrgSignupOidcConfigurationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class DefaultOidcConfigurationService extends AbstractOrganizationScopedService<OidcConfiguration> implements OidcConfigurationService {

    private final OidcConfigurationRepository oidcRepository;
    private final OrganizationService organizationService;
    private final ApplicationRepository applicationRepository;
    private final OrgSignupOidcConfigurationService orgSignupOidcConfigurationService;
    private final TenantRepository tenants;

    public DefaultOidcConfigurationService(OidcConfigurationRepository repository,
                                           OrganizationService organizationService,
                                           ApplicationRepository applicationRepository,
                                           OrgSignupOidcConfigurationService orgSignupOidcConfigurationService,
                                           TenantRepository tenants,
                                           SecurityContext securityContext) {
        super(repository, securityContext);
        this.oidcRepository = repository;
        this.organizationService = organizationService;
        this.applicationRepository = applicationRepository;
        this.orgSignupOidcConfigurationService = orgSignupOidcConfigurationService;
        this.tenants = tenants;
    }

    @Override
    protected Future<Void> beforeSave(OidcConfiguration entity) {
        Validate.notNull(entity.getName(), "OidcConfiguration name cannot be null");
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
            entity.setCreated(new Date());
        }
        entity.setUpdated(new Date());
        return Future.succeededFuture();
    }

    @Override
    public Future<List<OidcConfiguration>> findEnabledByIds(List<String> ids, String orgId) {
        Validate.notEmpty(ids, "ids cannot be null or empty");
        Validate.notBlank(orgId, "orgId cannot be blank");
        return oidcRepository.findEnabledByIds(ids, orgId);
    }

    @Override
    public Future<OidcConfiguration> findById(String id, String organizationId) {
        Validate.notBlank(id, "id cannot be blank");
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return oidcRepository.findById(id, organizationId);
    }

    @Override
    public Future<OidcConfiguration> findOrgLoginConfig(String organizationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return organizationService.findById(organizationId).compose(org -> {
            if (org == null || org.getSsoConfigId() == null) {
                return Future.succeededFuture();
            }
            // Direct repository lookup bypasses AbstractCrudService scope enforcement so the
            // pre-auth login-lookup path can resolve without a participant. Defense in depth:
            // also confirm the row's organizationId matches and it's enabled.
            return oidcRepository.findById(org.getSsoConfigId(), organizationId)
                                 .map(c -> validForOrgLogin(c, organizationId));
        });
    }

    @Override
    public Future<List<BaseOidcConfiguration>> findEnabledForScope(String organizationId, String applicationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Future<List<BaseOidcConfiguration>> ret;
        if (applicationId != null) {
            // Direct repository lookup so the pre-auth login pages can resolve without a participant.
            ret = applicationRepository.findById(applicationId, organizationId)
                                       .compose(app -> {
                                           if (app == null
                                                   || app.getOidcConfigurationIds() == null
                                                   || app.getOidcConfigurationIds().isEmpty()) {
                                               return Future.succeededFuture(List.<OidcConfiguration>of());
                                           }
                                           return findEnabledByIds(app.getOidcConfigurationIds(), organizationId);
                                       })
                                       .map(ArrayList::new);
        } else {
            ret = Future.all(orgSignupOidcConfigurationService.findAllEnabled(),
                             findOrgLoginConfig(organizationId))
                        .map(cf -> {
                            List<OrgSignupOidcConfiguration> social = cf.resultAt(0);
                            OidcConfiguration sso = cf.resultAt(1);
                            List<BaseOidcConfiguration> providers = new ArrayList<>(social);
                            if (sso != null && sso.isEnabled()) {
                                providers.add(sso);
                            }
                            return providers;
                        });
        }
        return ret;
    }

    @Override
    public Future<OidcConfiguration> findTenantLoginConfig(String organizationId, String applicationId, String tenantId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        Validate.notBlank(tenantId, "tenantId cannot be blank");
        return tenants.findByTenantId(organizationId, applicationId, tenantId).compose(tenant -> {
            Future<OidcConfiguration> ret;
            if (tenant == null || tenant.getSsoConfigId() == null) {
                ret = Future.succeededFuture();
            } else {
                // a direct repository lookup, since the login lookup runs before a participant is bound; the row
                // must be the tenant's own and enabled
                ret = oidcRepository.findById(tenant.getSsoConfigId(), organizationId)
                                    .map(config -> config != null && config.isEnabled()
                                            && applicationId.equals(config.getApplicationId())
                                            && tenantId.equals(config.getTenantId()) ? config : null);
            }
            return ret;
        });
    }

    @Override
    public Future<OidcConfiguration> saveTenantConfig(OidcConfiguration configuration) {
        Validate.notBlank(configuration.getOrganizationId(), "A tenant's configuration names its organization");
        Validate.notBlank(configuration.getApplicationId(), "A tenant's configuration names its application");
        Validate.notBlank(configuration.getTenantId(), "A tenant's configuration names its tenant");
        return beforeSave(configuration).compose(v -> oidcRepository.saveSync(configuration, configuration.getOrganizationId()));
    }

    @Override
    public Future<Void> deleteTenantConfig(OidcConfiguration configuration) {
        Validate.notBlank(configuration.getTenantId(), "A tenant's configuration names its tenant");
        return oidcRepository.deleteByIdSync(configuration.getId(), configuration.getOrganizationId());
    }

    private static OidcConfiguration validForOrgLogin(OidcConfiguration config, String expectedOrgId) {
        if (config == null) return null;
        if (!config.isEnabled()) return null;
        if (!Objects.equals(expectedOrgId, config.getOrganizationId())) return null;
        return config;
    }
}
