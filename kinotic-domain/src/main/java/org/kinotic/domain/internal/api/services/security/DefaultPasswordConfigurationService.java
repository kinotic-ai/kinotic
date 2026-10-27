package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.repositories.TenantRepository;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.kinotic.domain.internal.api.repositories.PasswordConfigurationRepository;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@RequiredArgsConstructor
public class DefaultPasswordConfigurationService implements PasswordConfigurationService {

    private final PasswordConfigurationRepository passwordRepository;
    private final ApplicationRepository applicationRepository;
    private final TenantRepository tenantRepository;
    private final OrganizationSignInGuard signInGuard;
    private final SecurityContext securityContext;

    @Override
    public Future<PasswordConfiguration> findOffered(String organizationId, String applicationId, String tenantId) {
        Future<PasswordConfiguration> own = findEnabled(PasswordConfiguration.idOf(organizationId, applicationId, tenantId), organizationId);
        return tenantId == null
                ? own
                : own.compose(found -> found != null
                        ? Future.succeededFuture(found)
                        : findEnabled(PasswordConfiguration.idOf(organizationId, applicationId, null), organizationId));
    }

    private Future<PasswordConfiguration> findEnabled(String id, String organizationId) {
        return passwordRepository.findById(id, organizationId).map(found -> found != null && found.isEnabled() ? found : null);
    }

    @Override
    public Future<PasswordConfiguration> findById(String id, String organizationId) {
        Validate.notBlank(id, "id cannot be blank");
        return passwordRepository.findById(id, organizationId);
    }

    @Override
    public Future<Page<PasswordConfiguration>> findByOrganization(String organizationId, Pageable pageable) {
        return passwordRepository.findAll(organizationId, pageable);
    }

    @Override
    public Future<PasswordConfiguration> save(PasswordConfiguration configuration) {
        Validate.notNull(configuration, "configuration cannot be null");
        String organizationId = configuration.getOrganizationId();
        requireOwner(organizationId);
        // sign-up gives each new user a tenant of its own, which only an application's own login can
        Validate.isTrue(!configuration.isCreateUserOnSignIn() || (configuration.getApplicationId() != null && configuration.getTenantId() == null),
                        "Sign-up with an email and a password is taken at an application's own login: an organization's"
                                + " administrators add its members, and a tenant's decide who joins it");
        configuration.setId(PasswordConfiguration.idOf(organizationId, configuration.getApplicationId(), configuration.getTenantId()));
        return requireScope(configuration)
                .compose(v -> passwordRepository.findById(configuration.getId(), organizationId))
                .compose(existing -> {
                    Date now = new Date();
                    configuration.setCreated(existing != null ? existing.getCreated() : now)
                                 .setUpdated(now);
                    return (disables(existing) && !configuration.isEnabled()
                            ? signInGuard.requireMembersKeepSignInWithoutPassword(organizationId)
                            : Future.<Void>succeededFuture())
                            .compose(v -> passwordRepository.saveSync(configuration, organizationId));
                });
    }

    @Override
    public Future<Void> deleteById(String id, String organizationId) {
        Validate.notBlank(id, "id cannot be blank");
        requireOwner(organizationId);
        return passwordRepository.findById(id, organizationId)
                                 .compose(existing -> {
                                     Future<Void> ret;
                                     if (existing == null) {
                                         ret = Future.succeededFuture();
                                     } else {
                                         ret = (disables(existing) ? signInGuard.requireMembersKeepSignInWithoutPassword(organizationId) : Future.<Void>succeededFuture())
                                                 .compose(v -> passwordRepository.deleteByIdSync(id, organizationId));
                                     }
                                     return ret;
                                 });
    }

    // whether turning existing off takes the password away from the organization's members
    private static boolean disables(PasswordConfiguration existing) {
        return existing != null && existing.isEnabled() && existing.getApplicationId() == null;
    }

    private void requireOwner(String organizationId) {
        Validate.notBlank(organizationId, "Organization id must be set on PasswordConfiguration");
        String callerOrganizationId = securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
        if (!callerOrganizationId.equals(organizationId)) {
            throw new AuthorizationException("Cannot write the PasswordConfiguration of organization '" + organizationId
                                             + "' while authenticated as organization '" + callerOrganizationId + "'");
        }
    }

    // the application, and the tenant, whose login a configuration is offered at must be the organization's
    private Future<Void> requireScope(PasswordConfiguration configuration) {
        String organizationId = configuration.getOrganizationId();
        String applicationId = configuration.getApplicationId();
        String tenantId = configuration.getTenantId();
        Future<Void> ret;
        if (applicationId == null) {
            ret = Future.succeededFuture();
        } else if (tenantId == null) {
            ret = applicationRepository.requireById(applicationId, organizationId).map(application -> {
                // a new user gets a tenant of its own only while the system manages the application's tenants
                Validate.isTrue(!configuration.isCreateUserOnSignIn() || application.getTenantManagement() == TenantManagement.SYSTEM,
                                "Sign-up with an email and a password needs the system to manage the application's tenants;"
                                        + " a user of an application whose tenants their administrators manage joins a tenant by"
                                        + " invitation or through the tenant's own sign-in providers");
                return null;
            });
        } else {
            ret = tenantRepository.findById(tenantId, applicationId, organizationId).map(tenant -> {
                Validate.isTrue(tenant != null, "Application %s of organization %s has no tenant %s", applicationId, organizationId, tenantId);
                return null;
            });
        }
        return ret;
    }
}
