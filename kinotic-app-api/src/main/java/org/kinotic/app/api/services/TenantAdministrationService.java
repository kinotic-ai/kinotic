package org.kinotic.app.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.TenantOidcConfiguration;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzConstants;

import java.util.List;

/**
 * What the administrators of a tenant of an application do for their own tenant: decide who its users are, what
 * roles they hold in it, and which sign-in providers its login offers. Every function acts on the tenant of the
 * calling application user and reaches no other tenant; the built-in {@code tenant.admin} role holds every
 * permission the functions need. An application's own UI calls the service for its customers' administrators.
 */
@Publish
@Version("1.0.0")
@Zone(DomainUtil.APP_API_ZONE)
@AuthzResource(value = AuthzConstants.TENANT_TYPE, parent = AuthzConstants.APPLICATION_TYPE,
               resourceId = "{" + AuthzConstants.TENANT_ID_REFERENCE + "}")
public interface TenantAdministrationService {

    /** The users of the caller's tenant, the ones invited and not yet signed in among them, marked pending. */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_MEMBERS)
    Future<Page<ApplicationParticipantIdentity>> findUsers(Pageable pageable);

    /**
     * Adds a user to the caller's tenant at an email address and emails the address an invitation to claim it,
     * naming the caller as its sender. The user is pending until the address accepts, and can be granted roles
     * meanwhile. Fails when the application's tenants are the system's, each of which has one user, when the
     * application already has a user at the address, or when the application's login offers no way to accept.
     *
     * @param email       the address invited
     * @param displayName the name the user is shown by until they give their own, or {@code null}
     * @return the user, pending
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_MEMBERS, consistent = true)
    Future<ApplicationParticipantIdentity> inviteUser(String email, String displayName);

    /**
     * Emails the invitation of a pending user of the caller's tenant again, or a new one when it has expired.
     *
     * @param userId the pending user
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_MEMBERS, consistent = true)
    Future<Void> resendInvitation(String userId);

    /**
     * Lets a user of the caller's tenant sign in, or stops them. The caller cannot stop themselves.
     *
     * @param userId  the user
     * @param enabled whether the user may sign in
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_MEMBERS, consistent = true)
    Future<Void> setUserEnabled(String userId, boolean enabled);

    /**
     * Removes a user of the caller's tenant, with its sign-in methods, the roles it was granted and the invitation
     * it never accepted. The caller cannot remove themselves.
     *
     * @param userId the user
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_MEMBERS, consistent = true)
    Future<Void> removeUser(String userId);

    /**
     * The roles the caller may grant in its tenant: the built-in roles of the tenant, and the roles of the
     * application its developers let a tenant's administrators grant.
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_ACCESS)
    Future<List<RoleDefinition>> findRoles();

    /** The grants made on the caller's tenant, to its users and to the tenant as a whole. */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_ACCESS)
    Future<List<Grant>> findGrants();

    /**
     * Grants one of {@link #findRoles()} on the caller's tenant, to one of its users or, with a subject naming the
     * tenant, to every user it has or will have. The holder has what the role bundles within the tenant.
     *
     * @param subject a user of the caller's tenant, or the tenant itself
     * @param roleId  the role
     * @return the grant
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_ACCESS, consistent = true)
    Future<Grant> grant(Subject subject, String roleId);

    /**
     * Revokes a grant of one of {@link #findRoles()} made on the caller's tenant. The caller cannot revoke a grant
     * made to themselves.
     *
     * @param grantId the grant, as {@link #findGrants()} lists it
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_ACCESS, consistent = true)
    Future<Void> revoke(String grantId);

    /** The sign-in providers the caller's tenant configured for its own login, enabled or not, oldest first. */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_ACCESS)
    Future<List<TenantOidcConfiguration>> findSignInProviders();

    /**
     * The URL the provider of the tenant's configuration {@code id} must be given as its redirect URI before a
     * sign-in through the configuration succeeds, or {@code null} when the tenant has no configuration by that id.
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_ACCESS)
    Future<String> findSignInProviderCallbackUrl(String id);

    /**
     * Saves a sign-in provider of the caller's tenant, new or changed, offered at the tenant's login beside what
     * the application offers, and makes {@code clientSecret}, when given, the secret it sends its provider. The
     * configuration names a provider kind and the tenant's account there, from which its authority is derived on
     * every save; its organization, application and tenant are the caller's, whatever it carried. With
     * {@code createUserOnSignIn} on, an identity the provider verifies becomes a user of the tenant on its first
     * sign-in.
     *
     * @param configuration the configuration
     * @param clientSecret  the client secret's value, or {@code null} or blank to keep the secret held for the
     *                      configuration, if any; a new configuration saved without one is a public client
     * @return the configuration as saved, with its id
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_ACCESS, consistent = true)
    Future<TenantOidcConfiguration> saveSignInProvider(TenantOidcConfiguration configuration, String clientSecret);

    /**
     * Deletes the tenant's sign-in provider {@code id} and the client secret held for it; nothing happens for an
     * id the tenant has no configuration by.
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_ACCESS, consistent = true)
    Future<Void> deleteSignInProvider(String id);

}
