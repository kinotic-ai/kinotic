package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.security.ApplicationOidcConfiguration;
import org.kinotic.domain.api.model.security.ApplicationSocialOidcConfiguration;
import org.kinotic.domain.api.model.security.ClientRegistrationType;
import org.kinotic.domain.api.model.security.ManagedOidcConfiguration;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OidcProviderType;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
            // the organization names its account at the provider, never a URL: the kind's template decides the
            // issuer, so the server only ever connects to the provider's own hosts. A configuration signing in through
            // Kinotic's registration uses none of its own client fields, which it keeps unchecked for a switch back
            if (owned instanceof ApplicationOidcConfiguration forApplication) {
                Validate.notNull(forApplication.getClientRegistrationType(), "ApplicationOidcConfiguration clientRegistrationType cannot be null");
            }
            if (OidcConfigurationService.isManaged(owned)) {
                owned.setAuthority(null);
            } else {
                // a configuration signing in through its own client names it, or every sign-in fails at the provider
                Validate.isTrue(StringUtils.isNotBlank(owned.getClientId()),
                                "%s needs the client id of its registration with the provider", owned.getClass().getSimpleName());
                owned.setAuthority(owned.getProvider().authority(owned.getProviderAccount()));
            }
            if (owned instanceof OrganizationOidcConfiguration) {
                // an organization's members are added by its administrators, whom its provider then signs in
                Validate.isTrue(!owned.isCreateUserOnSignIn(),
                                "An organization's own login admits the members its administrators added; createUserOnSignIn"
                                        + " applies to an application's or a tenant's configuration");
                // the organization's login claims a pending member on the email the provider asserts, which only the
                // organization's own directory may vouch for
                Validate.isTrue(!owned.getProvider().admitsEveryDirectory(owned.getProviderAccount()),
                                "An organization's own login names one directory of its own, not '%s', which admits every directory",
                                owned.getProviderAccount());
            } else if (owned instanceof ApplicationOidcConfiguration forApplication) {
                ret = requireApplication(forApplication.getOrganizationId(), forApplication.getApplicationId()).mapEmpty();
                if (OidcConfigurationService.isManaged(forApplication)) {
                    ret = ret.compose(v -> requireManagedRegistration(forApplication.getProvider()));
                }
            } else if (owned instanceof TenantOidcConfiguration forTenant) {
                ret = requireTenant(forTenant.getOrganizationId(), forTenant.getApplicationId(), forTenant.getTenantId());
            }
        }
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
            entity.setCreated(new Date());
        } else {
            // the index holds every owner's rows under raw ids, so the row the id names must be the caller's too
            ret = ret.compose(v -> oidcRepository.findById(entity.getId()).map(existing -> {
                if (existing != null) {
                    requireOwner(existing);
                    // the kind is the scope the configuration is offered at and keys its bindings, so a row never changes it
                    Validate.isTrue(existing.getClass() == entity.getClass(), "Configuration %s is a %s, which a %s cannot replace",
                                    entity.getId(), existing.getClass().getSimpleName(), entity.getClass().getSimpleName());
                }
                return null;
            }));
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
     * for a managed registration, since that row names a secret of the system's and is offered to
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

    // a configuration using Kinotic's registration signs in through the system's registration of its provider kind
    private Future<Void> requireManagedRegistration(OidcProviderType provider) {
        return oidcRepository.findEnabledSocialByProvider(provider).map(registration -> {
            Validate.isTrue(registration != null, "Kinotic manages no sign-in with %s; use the application's own credentials", provider.key());
            return null;
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
    public Future<OrgSignupOidcConfiguration> findEnabledOrgSignupByProvider(OidcProviderType provider) {
        Validate.notNull(provider, "provider cannot be null");
        return oidcRepository.findEnabledOrgSignupByProvider(provider);
    }

    @Override
    public Future<OrgSignupOidcConfiguration> findOrgSignupById(String id) {
        Validate.notBlank(id, "id cannot be blank");
        return oidcRepository.findOrgSignupById(id);
    }

    @Override
    public Future<List<ApplicationSocialOidcConfiguration>> findEnabledAppSocialRegistrations() {
        return oidcRepository.findEnabledSocial();
    }

    @Override
    public Future<ApplicationSocialOidcConfiguration> findEnabledAppSocialByProvider(OidcProviderType provider) {
        Validate.notNull(provider, "provider cannot be null");
        return oidcRepository.findEnabledSocialByProvider(provider);
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
            // an organization's login offers the system's org sign-up registrations before its own
            ret = concat(oidcRepository.findEnabledOrgSignup().map(signup -> signup), own);
        } else if (tenantId == null) {
            // a configuration using Kinotic's registration is offered while the system holds an enabled one of its kind
            ret = Future.all(own, oidcRepository.findEnabledSocial()).map(cf -> {
                Set<OidcProviderType> managed = cf.<List<ApplicationSocialOidcConfiguration>>resultAt(1).stream()
                                                  .map(OidcConfiguration::getProvider)
                                                  .collect(Collectors.toSet());
                return cf.<List<OidcConfiguration>>resultAt(0).stream()
                         .filter(configuration -> !OidcConfigurationService.isManaged(configuration)
                                                  || managed.contains(configuration.getProvider()))
                         .toList();
            });
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
                                     // one using Kinotic's registration while the system holds an enabled one of its kind,
                                     // as findEnabledForScope lists it
                                     case ApplicationOidcConfiguration owned when OidcConfigurationService.isManaged(owned) ->
                                             organizationId.equals(owned.getOrganizationId()) && owned.getApplicationId().equals(applicationId)
                                                     ? oidcRepository.findEnabledSocialByProvider(owned.getProvider()).map(Objects::nonNull)
                                                     : Future.succeededFuture(false);
                                     case ApplicationOidcConfiguration owned ->
                                             Future.succeededFuture(organizationId.equals(owned.getOrganizationId())
                                                                    && owned.getApplicationId().equals(applicationId));
                                     case TenantOidcConfiguration owned ->
                                             Future.succeededFuture(organizationId.equals(owned.getOrganizationId())
                                                                    && owned.getApplicationId().equals(applicationId)
                                                                    && owned.getTenantId().equals(tenantId));
                                     // every organization's own login offers the system's org sign-up registrations
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
    public Future<Page<OwnedOidcConfiguration>> findOwnedByOrganization(String organizationId, Pageable pageable) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return oidcRepository.findOwnedByOrganization(organizationId, pageable);
    }

    @Override
    public <C extends OwnedOidcConfiguration> Future<C> save(C configuration, String clientSecret) {
        // beforeSave first, since it checks the caller owns the row and gives a new configuration the id its secret
        // is keyed by; the row is written once the vault holds the secret, so it never claims one the vault refused,
        // and its updated, which keys the OAuth client cache, changes with the secret
        return beforeSave(configuration)
                .compose(v -> StringUtils.isNotBlank(clientSecret)
                        ? secretStorageService.setSecret(configuration.getOrganizationId(), configuration.getId(), clientSecret)
                                              .map(true)
                        : isSecretHeld(configuration))
                .compose(confidential -> {
                    configuration.setConfidentialClient(confidential);
                    // refreshed before returning, since the login's provider list and the portal's table search right after a save
                    return oidcRepository.saveSync(configuration);
                })
                .map(saved -> {
                    @SuppressWarnings("unchecked")
                    C ret = (C) saved;
                    return ret;
                });
    }

    // read from the persisted row: only a save given a secret, and deleteClientSecret, change whether one is held
    private Future<Boolean> isSecretHeld(OwnedOidcConfiguration configuration) {
        return oidcRepository.findOwnedById(configuration.getId(), configuration.getOrganizationId())
                             .map(existing -> existing != null && existing.isConfidentialClient());
    }

    @Override
    public Future<OwnedOidcConfiguration> deleteClientSecret(String id) {
        Validate.notBlank(id, "id cannot be blank");
        String orgId = requireOrganizationId();
        return oidcRepository.findOwnedById(id, orgId)
                             .compose(existing -> existing == null
                                     ? Future.succeededFuture()
                                     // the row is written once the vault no longer holds the secret, as in save
                                     : beforeSave(existing).compose(v -> secretStorageService.deleteSecret(orgId, id))
                                                           .compose(v -> oidcRepository.saveSync(existing.setConfidentialClient(false)))
                                                           .map(OwnedOidcConfiguration.class::cast));
    }

    @Override
    public Future<OwnedOidcConfiguration> copy(String sourceId, String applicationId) {
        Validate.notBlank(sourceId, "sourceId cannot be blank");
        String orgId = requireOrganizationId();
        return oidcRepository.findOwnedById(sourceId, orgId)
                             .compose(source -> {
                                 Validate.isTrue(source != null, "Organization %s has no OIDC configuration %s", orgId, sourceId);
                                 Validate.isTrue(!OidcConfigurationService.isManaged(source), "Configuration %s signs in through Kinotic's registration and has no client of its own to copy", sourceId);
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
