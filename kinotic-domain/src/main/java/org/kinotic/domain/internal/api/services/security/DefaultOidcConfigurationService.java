package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.security.ApplicationOidcConfiguration;
import org.kinotic.domain.api.model.security.ApplicationSocialOidcConfiguration;
import org.kinotic.domain.api.model.security.ManagedOidcConfiguration;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OidcProviderKind;
import org.kinotic.domain.api.model.security.OrganizationOidcConfiguration;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.security.TenantOidcConfiguration;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.SystemParticipant;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.repositories.TenantRepository;
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
    private final ApplicationRepository applicationRepository;
    private final TenantRepository tenantRepository;
    private final SecretStorageService secretStorageService;
    private final SecurityContext securityContext;

    public DefaultOidcConfigurationService(OidcConfigurationRepository repository,
                                           ApplicationRepository applicationRepository,
                                           TenantRepository tenantRepository,
                                           SecretStorageService secretStorageService,
                                           SecurityContext securityContext) {
        super(repository);
        this.oidcRepository = repository;
        this.applicationRepository = applicationRepository;
        this.tenantRepository = tenantRepository;
        this.secretStorageService = secretStorageService;
        this.securityContext = securityContext;
    }

    @Override
    protected Future<Void> beforeSave(OidcConfiguration entity) {
        Validate.notNull(entity.getName(), "OidcConfiguration name cannot be null");
        Validate.notNull(entity.getProvider(), "OidcConfiguration provider cannot be null");
        requireOwner(entity);
        Future<Void> ret = Future.succeededFuture();
        if (entity instanceof OwnedOidcConfiguration owned) {
            // the organization names its account at the provider, never a URL: the kind's template
            // decides the issuer, so the server only ever connects to the provider's own hosts
            owned.setAuthority(owned.getProvider().authority(owned.getProviderAccount()));
            if (owned instanceof ApplicationOidcConfiguration forApplication) {
                ret = requireApplication(forApplication.getOrganizationId(), forApplication.getApplicationId()).mapEmpty();
            } else if (owned instanceof TenantOidcConfiguration forTenant) {
                ret = requireTenant(forTenant.getOrganizationId(), forTenant.getApplicationId(), forTenant.getTenantId());
            }
        }
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
            entity.setCreated(new Date());
        }
        entity.setUpdated(new Date());
        return ret;
    }

    @Override
    protected Future<Void> beforeDelete(String id) {
        return oidcRepository.findById(id).compose(existing -> {
            Future<Void> ret;
            if (existing == null) {
                ret = Future.succeededFuture();
            } else {
                requireOwner(existing);
                ret = existing instanceof OwnedOidcConfiguration owned
                        ? secretStorageService.deleteSecret(owned.getOrganizationId(), id)
                        : Future.succeededFuture();
            }
            return ret;
        });
    }

    /**
     * Requires the bound participant to be the owner of {@code configuration}'s kind: a system participant
     * for a managed registration, since that row names a secret of the platform's and is offered to
     * every organization; a participant of the named organization for an organization's.
     *
     * @throws IllegalStateException  if no participant is bound to the current context
     * @throws AuthorizationException if the participant is not the owner
     */
    private void requireOwner(OidcConfiguration configuration) {
        switch (configuration) {
            case ManagedOidcConfiguration _ -> securityContext.requireParticipant(SystemParticipant.class);
            case OwnedOidcConfiguration owned -> {
                Validate.notBlank(owned.getOrganizationId(), "Organization id must be set on %s", owned.getClass().getSimpleName());
                String orgId = requireOrganizationId();
                if (!orgId.equals(owned.getOrganizationId())) {
                    throw new AuthorizationException("Cannot write " + owned.getClass().getSimpleName() + " with organizationId '"
                                                     + owned.getOrganizationId() + "' while authenticated as organization '"
                                                     + orgId + "'");
                }
            }
        }
    }

    private String requireOrganizationId() {
        return securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
    }

    // the application whose login an ApplicationOidcConfiguration is offered at must be the organization's own
    private Future<Application> requireApplication(String orgId, String applicationId) {
        Validate.isTrue(StringUtils.isNotBlank(applicationId), "Application id must be set on ApplicationOidcConfiguration");
        return applicationRepository.findById(applicationId, orgId).map(application -> {
            Validate.isTrue(application != null, "Organization %s has no application %s", orgId, applicationId);
            return application;
        });
    }

    // the tenant whose login a TenantOidcConfiguration is offered at must be one of the organization's application's
    private Future<Void> requireTenant(String orgId, String applicationId, String tenantId) {
        Validate.isTrue(StringUtils.isNotBlank(applicationId), "Application id must be set on TenantOidcConfiguration");
        Validate.isTrue(StringUtils.isNotBlank(tenantId), "Tenant id must be set on TenantOidcConfiguration");
        return tenantRepository.findById(tenantId, applicationId, orgId).map(tenant -> {
            Validate.isTrue(tenant != null, "Application %s of organization %s has no tenant %s", applicationId, orgId, tenantId);
            return null;
        });
    }

    @Override
    public Future<List<OrgSignupOidcConfiguration>> findEnabledOrgSignupProviders() {
        return oidcRepository.findEnabledOrgSignup();
    }

    @Override
    public Future<OrgSignupOidcConfiguration> findEnabledOrgSignupByProvider(OidcProviderKind provider) {
        Validate.notNull(provider, "provider cannot be null");
        return oidcRepository.findEnabledOrgSignupByProvider(provider);
    }

    @Override
    public Future<OrgSignupOidcConfiguration> findOrgSignupById(String id) {
        Validate.notBlank(id, "id cannot be blank");
        return oidcRepository.findOrgSignupById(id);
    }

    @Override
    public Future<List<OidcConfiguration>> findEnabledForScope(String organizationId, String applicationId, String tenantId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        if (tenantId != null) {
            Validate.notBlank(applicationId, "applicationId is required when tenantId is supplied");
        }
        Future<List<? extends OidcConfiguration>> own = oidcRepository.findEnabledOwnedByScope(organizationId, applicationId, tenantId)
                                                                       .map(owned -> owned);
        Future<List<OidcConfiguration>> ret;
        if (applicationId == null) {
            // an organization's login offers the platform's org sign-up registrations before its own
            ret = concat(oidcRepository.findEnabledOrgSignup().map(signup -> signup), own);
        } else if (tenantId == null) {
            ret = concat(own, findEnabledSocial(organizationId, applicationId).map(social -> social));
        } else {
            // a tenant's login offers the tenant's own providers before everything the application's offers
            ret = concat(own, findEnabledForScope(organizationId, applicationId, null).map(offered -> offered));
        }
        return ret;
    }

    private static Future<List<OidcConfiguration>> concat(Future<List<? extends OidcConfiguration>> first,
                                                          Future<List<? extends OidcConfiguration>> second) {
        return Future.all(first, second).map(cf -> {
            List<OidcConfiguration> offered = new ArrayList<>(cf.<List<OidcConfiguration>>resultAt(0));
            offered.addAll(cf.<List<OidcConfiguration>>resultAt(1));
            return offered;
        });
    }

    // the enabled platform social registrations the application names, in the application's order
    private Future<List<ApplicationSocialOidcConfiguration>> findEnabledSocial(String organizationId, String applicationId) {
        return applicationRepository.findById(applicationId, organizationId)
                                    .compose(application -> application == null || application.getSocialProviders().isEmpty()
                                            ? Future.succeededFuture(List.of())
                                            : oidcRepository.findEnabledSocialByProviders(application.getSocialProviders()));
    }

    @Override
    public Future<OidcConfiguration> findOffered(String configurationId, String organizationId, String applicationId, String tenantId) {
        Validate.notBlank(configurationId, "configurationId cannot be blank");
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        if (tenantId != null) {
            Validate.notBlank(applicationId, "applicationId is required when tenantId is supplied");
        }
        return oidcRepository.findById(configurationId)
                             .compose(configuration -> {
                                 Future<Boolean> offered = switch (configuration) {
                                     case OrganizationOidcConfiguration owned ->
                                             Future.succeededFuture(applicationId == null && organizationId.equals(owned.getOrganizationId()));
                                     // offered at the application's login and at every one of its tenants'
                                     case ApplicationOidcConfiguration owned ->
                                             Future.succeededFuture(organizationId.equals(owned.getOrganizationId())
                                                                    && owned.getApplicationId().equals(applicationId));
                                     case TenantOidcConfiguration owned ->
                                             Future.succeededFuture(organizationId.equals(owned.getOrganizationId())
                                                                    && owned.getApplicationId().equals(applicationId)
                                                                    && owned.getTenantId().equals(tenantId));
                                     case ApplicationSocialOidcConfiguration social when applicationId != null ->
                                             applicationRepository.findById(applicationId, organizationId)
                                                                  .map(application -> application != null
                                                                                      && application.getSocialProviders().contains(social.getProvider()));
                                     // every organization's own login offers the platform's org sign-up registrations
                                     case OrgSignupOidcConfiguration signup -> Future.succeededFuture(applicationId == null);
                                     case null, default -> Future.succeededFuture(false);
                                 };
                                 return offered.map(yes -> yes && configuration.isEnabled() ? configuration : null);
                             });
    }

    @Override
    public Future<OwnedOidcConfiguration> findOwnedById(String id, String organizationId) {
        Validate.notBlank(id, "id cannot be blank");
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return oidcRepository.findOwnedById(id, organizationId);
    }

    @Override
    public <C extends OwnedOidcConfiguration> Future<C> save(C configuration, String clientSecret) {
        boolean confidential = StringUtils.isNotBlank(clientSecret);
        // saved first: a new configuration is given the id its secret is keyed by in beforeSave
        return save(configuration.setConfidentialClient(confidential)).compose(saved -> {
            @SuppressWarnings("unchecked")
            C owned = (C) saved;
            Future<Void> stored = confidential
                    ? secretStorageService.setSecret(owned.getOrganizationId(), owned.getId(), clientSecret)
                    : secretStorageService.deleteSecret(owned.getOrganizationId(), owned.getId());
            // a secret the vault refused is not held, so the row says so rather than claiming one at sign-in
            return stored.recover(err -> save(owned.setConfidentialClient(false)).compose(v -> Future.failedFuture(err)))
                         .map(owned);
        });
    }

    @Override
    public Future<OwnedOidcConfiguration> copy(String sourceId, String applicationId) {
        Validate.notBlank(sourceId, "sourceId cannot be blank");
        String orgId = requireOrganizationId();
        return oidcRepository.findOwnedById(sourceId, orgId)
                             .compose(source -> {
                                 Validate.isTrue(source != null, "Organization %s has no OIDC configuration %s", orgId, sourceId);
                                 OwnedOidcConfiguration target;
                                 if (applicationId == null) {
                                     target = new OrganizationOidcConfiguration();
                                 } else {
                                     target = new ApplicationOidcConfiguration().setApplicationId(applicationId);
                                 }
                                 target.setOrganizationId(orgId)
                                       .setProviderAccount(source.getProviderAccount())
                                       .setName(source.getName())
                                       .setProvider(source.getProvider())
                                       .setClientId(source.getClientId())
                                       .setAudience(source.getAudience())
                                       .setEnabled(true);
                                 return findClientSecret(source).compose(secret -> save(target, secret));
                             });
    }

    @Override
    public Future<String> findClientSecret(OwnedOidcConfiguration configuration) {
        Validate.notBlank(configuration.getId(), "The configuration has no id");
        Validate.notBlank(configuration.getOrganizationId(), "The configuration names no organization");
        return configuration.isConfidentialClient()
                ? secretStorageService.getSecret(configuration.getOrganizationId(), configuration.getId())
                : Future.succeededFuture();
    }
}
