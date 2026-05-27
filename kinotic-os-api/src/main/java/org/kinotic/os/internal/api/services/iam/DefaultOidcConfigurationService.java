package org.kinotic.os.internal.api.services.iam;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.iam.OidcConfiguration;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.internal.api.repositories.OidcConfigurationRepository;
import org.kinotic.domain.internal.api.services.AbstractOrganizationScopedService;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultOidcConfigurationService extends AbstractOrganizationScopedService<OidcConfiguration> implements OidcConfigurationService {

    private final OidcConfigurationRepository oidcRepository;
    private final OrganizationService organizationService;

    public DefaultOidcConfigurationService(OidcConfigurationRepository repository,
                                           OrganizationService organizationService,
                                           SecurityContext securityContext) {
        super(repository, securityContext);
        this.oidcRepository = repository;
        this.organizationService = organizationService;
    }

    @Override
    public CompletableFuture<OidcConfiguration> save(OidcConfiguration entity) {
        Validate.notNull(entity.getName(), "OidcConfiguration name cannot be null");
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
            entity.setCreated(new Date());
        }
        entity.setUpdated(new Date());
        return super.save(entity);
    }

    @Override
    public CompletableFuture<List<OidcConfiguration>> findEnabledByIds(List<String> ids) {
        Validate.notEmpty(ids, "ids cannot be null or empty");
        return oidcRepository.findEnabledByIds(ids);
    }

    @Override
    public CompletableFuture<OidcConfiguration> findOrgLoginConfig(String organizationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return organizationService.findById(organizationId).thenCompose(org -> {
            if (org == null || org.getSsoConfigId() == null) {
                return CompletableFuture.completedFuture(null);
            }
            // Direct repository lookup bypasses AbstractCrudService scope enforcement so the
            // pre-auth login-lookup path can resolve without a participant. Defense in depth:
            // also confirm the row's organizationId matches and it's enabled.
            return oidcRepository.findById(org.getSsoConfigId(), organizationId)
                                 .thenApply(c -> validForOrgLogin(c, organizationId));
        });
    }

    private static OidcConfiguration validForOrgLogin(OidcConfiguration config, String expectedOrgId) {
        if (config == null) return null;
        if (!config.isEnabled()) return null;
        if (!Objects.equals(expectedOrgId, config.getOrganizationId())) return null;
        return config;
    }
}
