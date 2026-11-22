package org.kinotic.app.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.app.api.services.TenantAdministrationService;
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectType;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.PendingInvite;
import org.kinotic.domain.api.model.security.RoleOwner;
import org.kinotic.domain.api.model.security.TenantOidcConfiguration;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.rest.support.OidcCallbacks;
import org.kinotic.domain.api.services.security.RoleService;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class DefaultTenantAdministrationService implements TenantAdministrationService {

    private final SecurityContext securityContext;
    private final ApplicationRepository applications;
    private final ParticipantIdentityService identities;
    private final InviteService invites;
    private final RoleService roleService;
    private final AuthzModelService models;
    private final RelationshipService relationships;
    private final OidcConfigurationService oidcConfigurations;
    private final KinoticDomainProperties domainProperties;

    @Override
    public Future<Page<ApplicationParticipantIdentity>> findUsers(Pageable pageable) {
        ApplicationParticipant caller = requireTenantUser();
        return identities.findTenantUsers(caller.getOrganizationId(), caller.getApplicationId(), caller.getTenantId(), pageable);
    }

    @Override
    public Future<ApplicationParticipantIdentity> inviteUser(String email, String displayName) {
        ApplicationParticipant caller = requireTenantUser();
        Validate.notBlank(email, "email is required");
        return application(caller).compose(application -> {
            Validate.isTrue(application.getTenantManagement() == TenantManagement.TENANT_ADMINISTRATORS,
                            "The system gives each user of this application a tenant of its own, so no one is invited into one.");
            return invites.inviteApplicationUser(invitation(caller, email, displayName), null);
        });
    }

    @Override
    public Future<Void> resendInvitation(String userId) {
        ApplicationParticipant caller = requireTenantUser();
        return requireUser(caller, userId).compose(user -> {
            Validate.isTrue(user.isPending(), "This user has already signed in.");
            return invites.requireAcceptable(caller.getOrganizationId(), caller.getApplicationId(), caller.getTenantId())
                          .compose(v -> invites.findPendingInvite(user.getEmail(), caller.getOrganizationId(), caller.getApplicationId()))
                          .compose(pending -> pending != null
                                  ? invites.resendInvite(pending.getId(), caller.getOrganizationId(), null)
                                  : invites.createInvite(invitation(caller, user.getEmail(), user.getDisplayName()), null).mapEmpty());
        });
    }

    // an invitation into the caller's tenant, sent in the caller's name
    private static PendingInvite invitation(ApplicationParticipant caller, String email, String displayName) {
        return new PendingInvite().setEmail(email)
                                  .setDisplayName(StringUtils.trimToNull(displayName))
                                  .setOrganizationId(caller.getOrganizationId())
                                  .setApplicationId(caller.getApplicationId())
                                  .setTenantId(caller.getTenantId())
                                  .setInvitedById(caller.getId())
                                  .setInvitedByName(DomainUtil.senderNameOf(caller));
    }

    @Override
    public Future<Void> setUserEnabled(String userId, boolean enabled) {
        return changingUser(userId, user -> identities.saveSync(user.setEnabled(enabled)).mapEmpty());
    }

    @Override
    public Future<Void> removeUser(String userId) {
        // cascades the credential, the bindings, the delegates and the user's place in the application's store
        return changingUser(userId, user -> identities.deleteByIdSync(user.getId())
                                                      // a user goes with the invitation they never accepted
                                                      .compose(v -> invites.deletePendingInvite(user.getEmail(), user.getOrganizationId(),
                                                                                                user.getApplicationId())));
    }

    // One caller at a time enables, disables or removes a user, which is read once the lock is held, so a removal
    // and an enabling of the same user do not interleave
    private Future<Void> changingUser(String userId, Function<ApplicationParticipantIdentity, Future<Void>> change) {
        ApplicationParticipant caller = requireTenantUser();
        Validate.notBlank(userId, "userId is required");
        Validate.isTrue(!userId.equals(caller.getId()), "You cannot perform this action on yourself.");
        return relationships.exclusively(AuthzGraphUtil.authzUser(userId), () -> requireUser(caller, userId).compose(change));
    }

    @Override
    public Future<List<RoleDefinition>> findRoles() {
        ApplicationParticipant caller = requireTenantUser();
        // a role a tenant's administrators grant is homed at the tenant, which the store places
        return application(caller).compose(application -> models.findApplicationStoreModel(application.getOrganizationId(), application.getId())
                                                                .compose(model -> roleService.findRoles(RoleOwner.application(application), model)))
                                  .map(roles -> roles.stream().filter(RoleDefinition::tenantAssignable).toList());
    }

    @Override
    public Future<List<Grant>> findGrants() {
        ApplicationParticipant caller = requireTenantUser();
        return relationships.findGrants(storeOf(caller), tenantObject(caller))
                            .map(grants -> grants.stream().map(AuthzGraphUtil::localGrant).toList());
    }

    @Override
    public Future<Grant> grant(Subject subject, String roleId) {
        ApplicationParticipant caller = requireTenantUser();
        Validate.notNull(subject, "subject cannot be null");
        Validate.notBlank(subject.id(), "subject id cannot be blank");
        Validate.notBlank(roleId, "roleId cannot be blank");
        Future<Void> inTenant;
        if (subject.type() == SubjectType.TENANT) {
            Validate.isTrue(caller.getTenantId().equals(subject.id()), "No tenant of yours has id %s", subject.id());
            inTenant = Future.succeededFuture();
        } else {
            Validate.isTrue(subject.type() == SubjectType.USER, "a grant in a tenant is made to one of its users or to the tenant");
            inTenant = requireUser(caller, subject.id()).mapEmpty();
        }
        return inTenant.compose(v -> application(caller))
                       .compose(application -> requireAssignable(application, roleId)
                               .compose(v -> roleService.grant(application, subject, roleId, tenantOf(caller))));
    }

    @Override
    public Future<Void> revoke(String grantId) {
        ApplicationParticipant caller = requireTenantUser();
        Validate.notBlank(grantId, "grantId cannot be blank");
        String store = storeOf(caller);
        return relationships.findGrants(store, tenantObject(caller)).compose(grants -> {
            Grant grant = grants.stream().filter(made -> grantId.equals(made.id())).findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("No grant on the tenant has id " + grantId));
            Validate.isTrue(grant.subject().type() != SubjectType.USER || !caller.getId().equals(grant.subject().id()),
                            "You cannot revoke a grant made to yourself.");
            return application(caller).compose(application -> requireAssignable(application, grant.roleId()))
                                      .compose(v -> relationships.revoke(store, grantId, tenantObject(caller)));
        });
    }

    // A tenant's administrators grant and revoke the built-in roles of the tenant, and the roles of the application
    // its developers marked for them; any other role of the application is its developers' to grant
    private Future<Void> requireAssignable(Application application, String roleId) {
        return models.findApplicationModelOfRole(application.getOrganizationId(), application.getId(), roleId)
                     .compose(model -> model.roles().containsKey(roleId)
                             ? Future.succeededFuture(AuthzConstants.TENANT_TYPE.equals(AuthzNameUtil.roleHome(roleId)))
                             : roleService.requireRole(RoleOwner.application(application), roleId).map(role -> role.isTenantAssignable()))
                     .map(assignable -> {
                         Validate.isTrue(assignable, "Role %s is not one a tenant's administrators grant", roleId);
                         return null;
                     });
    }

    @Override
    public Future<List<TenantOidcConfiguration>> findSignInProviders() {
        ApplicationParticipant caller = requireTenantUser();
        return oidcConfigurations.findOwnedByTenant(caller.getOrganizationId(), caller.getApplicationId(), caller.getTenantId());
    }

    @Override
    public Future<String> findSignInProviderCallbackUrl(String id) {
        ApplicationParticipant caller = requireTenantUser();
        return signInProvider(caller, id).map(configuration -> configuration == null
                ? null
                : OidcCallbacks.applicationCallbackUrl(configuration.applicationKey(), domainProperties.getDomain().getAppApiBaseUrl(),
                                                       configuration.getId()));
    }

    @Override
    public Future<TenantOidcConfiguration> saveSignInProvider(TenantOidcConfiguration configuration, String clientSecret) {
        ApplicationParticipant caller = requireTenantUser();
        Validate.notNull(configuration, "configuration is required");
        configuration.setApplicationId(caller.getApplicationId())
                     .setTenantId(caller.getTenantId())
                     .setOrganizationId(caller.getOrganizationId());
        return oidcConfigurations.save(configuration, clientSecret);
    }

    @Override
    public Future<Void> deleteSignInProvider(String id) {
        ApplicationParticipant caller = requireTenantUser();
        // looked up as the tenant's own first, so an id of another owner's row is a no-op rather than a refusal
        return signInProvider(caller, id).compose(found -> found == null
                ? Future.succeededFuture()
                : oidcConfigurations.deleteById(id));
    }

    // the tenant's configuration by that id, or null
    private Future<TenantOidcConfiguration> signInProvider(ApplicationParticipant caller, String id) {
        Validate.notBlank(id, "id is required");
        return oidcConfigurations.findOwnedById(id, caller.getOrganizationId())
                                 .map(owned -> owned instanceof TenantOidcConfiguration forTenant
                                         && caller.getApplicationId().equals(forTenant.getApplicationId())
                                         && caller.getTenantId().equals(forTenant.getTenantId()) ? forTenant : null);
    }

    // A tenant is administered by one of its own users; an application's machine belongs to no tenant
    private ApplicationParticipant requireTenantUser() {
        ApplicationParticipant ret = securityContext.requireParticipant(ApplicationParticipant.class);
        Validate.validState(ret.getTenantId() != null, "Only a user of a tenant administers it");
        return ret;
    }

    private Future<Application> application(ApplicationParticipant caller) {
        return applications.requireById(caller.getApplicationId(), caller.getOrganizationId());
    }

    // the user of the caller's tenant by that id; a user of another tenant gets the same answer as an unknown id
    private Future<ApplicationParticipantIdentity> requireUser(ApplicationParticipant caller, String userId) {
        return identities.findById(userId).map(identity -> {
            if (!(identity instanceof ApplicationParticipantIdentity user) || !caller.getScope().equals(user.scope())) {
                throw new IllegalArgumentException("No user of the tenant has id " + userId);
            }
            return user;
        });
    }

    private static String storeOf(ApplicationParticipant caller) {
        return AuthzGraphUtil.authzApplicationId(caller.getOrganizationId(), caller.getApplicationId());
    }

    private static Resource tenantOf(ApplicationParticipant caller) {
        return new Resource(AuthzConstants.TENANT_TYPE, caller.getTenantId());
    }

    private static String tenantObject(ApplicationParticipant caller) {
        return AuthzGraphUtil.authzObject(caller.getOrganizationId(), tenantOf(caller));
    }
}
