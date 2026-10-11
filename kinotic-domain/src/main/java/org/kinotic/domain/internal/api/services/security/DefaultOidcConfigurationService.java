package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OidcProviderKind;
import org.kinotic.domain.api.model.security.OrganizationOidcConfiguration;
import org.kinotic.domain.api.model.security.PlatformOidcConfiguration;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.SystemParticipant;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.SecretStorageService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.internal.api.repositories.OidcConfigurationRepository;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class DefaultOidcConfigurationService extends AbstractCrudService<OidcConfiguration> implements OidcConfigurationService {

    private final OidcConfigurationRepository oidcRepository;
    private final OrganizationService organizationService;
    private final ApplicationRepository applicationRepository;
    private final SecretStorageService secretStorageService;
    private final SecurityContext securityContext;

    public DefaultOidcConfigurationService(OidcConfigurationRepository repository,
                                           OrganizationService organizationService,
                                           ApplicationRepository applicationRepository,
                                           SecretStorageService secretStorageService,
                                           SecurityContext securityContext) {
        super(repository);
        this.oidcRepository = repository;
        this.organizationService = organizationService;
        this.applicationRepository = applicationRepository;
        this.secretStorageService = secretStorageService;
        this.securityContext = securityContext;
    }

    @Override
    protected Future<Void> beforeSave(OidcConfiguration entity) {
        Validate.notNull(entity.getName(), "OidcConfiguration name cannot be null");
        requireOwner(entity);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
            entity.setCreated(new Date());
        }
        entity.setUpdated(new Date());
        return Future.succeededFuture();
    }

    @Override
    protected Future<Void> beforeDelete(String id) {
        return oidcRepository.findById(id).compose(existing -> {
            Future<Void> ret;
            if (existing == null) {
                ret = Future.succeededFuture();
            } else {
                requireOwner(existing);
                ret = existing instanceof OrganizationOidcConfiguration owned
                        ? secretStorageService.deleteSecret(owned.getOrganizationId(), id)
                        : Future.succeededFuture();
            }
            return ret;
        });
    }

    /**
     * Requires the bound participant to be the owner of {@code configuration}'s kind: a system participant
     * for a platform configuration, since a platform row names a platform secret and is offered to every
     * sign-in; a participant of the named organization for an organization's.
     *
     * @throws IllegalStateException  if no participant is bound to the current context
     * @throws AuthorizationException if the participant is not the owner
     */
    private void requireOwner(OidcConfiguration configuration) {
        switch (configuration) {
            case PlatformOidcConfiguration _ -> securityContext.requireParticipant(SystemParticipant.class);
            case OrganizationOidcConfiguration owned -> {
                Validate.notBlank(owned.getOrganizationId(), "Organization id must be set on OrganizationOidcConfiguration");
                String orgId = securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
                if (!orgId.equals(owned.getOrganizationId())) {
                    throw new AuthorizationException("Cannot write OrganizationOidcConfiguration with organizationId '"
                                                     + owned.getOrganizationId() + "' while authenticated as organization '"
                                                     + orgId + "'");
                }
            }
        }
    }

    @Override
    public Future<List<PlatformOidcConfiguration>> findEnabledPlatformProviders() {
        return oidcRepository.findEnabledPlatform();
    }

    @Override
    public Future<PlatformOidcConfiguration> findEnabledPlatformByProvider(OidcProviderKind provider) {
        Validate.notNull(provider, "provider cannot be null");
        return oidcRepository.findEnabledPlatformByProvider(provider);
    }

    @Override
    public Future<PlatformOidcConfiguration> findPlatformById(String id) {
        Validate.notBlank(id, "id cannot be blank");
        return oidcRepository.findPlatformById(id);
    }

    @Override
    public Future<List<OrganizationOidcConfiguration>> findEnabledByIds(List<String> ids, String orgId) {
        Validate.notEmpty(ids, "ids cannot be null or empty");
        Validate.notBlank(orgId, "orgId cannot be blank");
        return oidcRepository.findEnabledByIds(ids, orgId);
    }

    @Override
    public Future<OrganizationOidcConfiguration> findById(String id, String organizationId) {
        Validate.notBlank(id, "id cannot be blank");
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return oidcRepository.findById(id, organizationId);
    }

    @Override
    public Future<OrganizationOidcConfiguration> findOrgLoginConfig(String organizationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return organizationService.findById(organizationId).compose(org -> {
            if (org == null || org.getSsoConfigId() == null) {
                return Future.succeededFuture();
            }
            return oidcRepository.findById(org.getSsoConfigId(), organizationId)
                                 .map(config -> config != null && config.isEnabled() ? config : null);
        });
    }

    @Override
    public Future<List<OidcConfiguration>> findEnabledForScope(String organizationId, String applicationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Future<List<OidcConfiguration>> ret;
        if (applicationId != null) {
            ret = applicationRepository.findById(applicationId, organizationId)
                                       .compose(app -> {
                                           if (app == null
                                                   || app.getOidcConfigurationIds() == null
                                                   || app.getOidcConfigurationIds().isEmpty()) {
                                               return Future.succeededFuture(List.<OrganizationOidcConfiguration>of());
                                           }
                                           return findEnabledByIds(app.getOidcConfigurationIds(), organizationId);
                                       })
                                       .map(ArrayList::new);
        } else {
            ret = Future.all(findEnabledPlatformProviders(), findOrgLoginConfig(organizationId))
                        .map(cf -> {
                            List<PlatformOidcConfiguration> social = cf.resultAt(0);
                            OrganizationOidcConfiguration sso = cf.resultAt(1);
                            List<OidcConfiguration> providers = new ArrayList<>(social);
                            if (sso != null) {
                                providers.add(sso);
                            }
                            return providers;
                        });
        }
        return ret;
    }

    @Override
    public Future<OrganizationOidcConfiguration> save(OrganizationOidcConfiguration configuration, String clientSecret) {
        // saved first: a new configuration is given the id its secret is keyed by in beforeSave
        return save(configuration).compose(saved -> {
            OrganizationOidcConfiguration owned = (OrganizationOidcConfiguration) saved;
            Future<Void> stored = StringUtils.isNotBlank(clientSecret)
                    ? secretStorageService.setSecret(owned.getOrganizationId(), owned.getId(), clientSecret)
                    : secretStorageService.deleteSecret(owned.getOrganizationId(), owned.getId());
            return stored.map(owned);
        });
    }

    @Override
    public Future<String> findClientSecret(OrganizationOidcConfiguration configuration) {
        Validate.notBlank(configuration.getId(), "The configuration has no id");
        Validate.notBlank(configuration.getOrganizationId(), "The configuration names no organization");
        return secretStorageService.getSecret(configuration.getOrganizationId(), configuration.getId());
    }
}
