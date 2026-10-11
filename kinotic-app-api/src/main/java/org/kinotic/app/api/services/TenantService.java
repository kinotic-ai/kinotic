package org.kinotic.app.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzUtil;

/**
 * The tenant a user of an application belongs to, for the application's own pages: what it is called, and the
 * identity provider its administrator chose for its users. Every function acts on the caller's tenant, so only an application
 * participant that belongs to one may call, and what the caller may do is checked on that tenant in the
 * application's store.
 */
@Publish
@Version("1.0.0")
@Zone(DomainUtil.APP_API_ZONE)
@AuthzResource(value = AuthzUtil.TENANT_TYPE, parent = AuthzUtil.APPLICATION_TYPE, resourceId = "{@tenantId}")
public interface TenantService {

    /**
     * The caller's tenant.
     *
     * @return the tenant; fails when the application has no record of it
     */
    Future<Tenant> getTenant();

    /**
     * Renames the caller's tenant; its id stays what it was.
     *
     * @param name the new name, not blank
     * @return the tenant as saved
     */
    @AuthzCheck(permission = AuthzUtil.CAN_EDIT)
    Future<Tenant> rename(String name);

    /**
     * The identity provider the caller's tenant signs its users in with, or null while it has none.
     */
    Future<OidcConfiguration> getSso();

    /**
     * Makes the given identity provider the caller's tenant's own, replacing the one it had: the tenant's users
     * sign in through it from the application's login page by the tenant's id, and a user it signs in for the
     * first time is created in the tenant, granted the given role on it. The configuration names the provider
     * kind, the client id and the authority; a client secret is named by reference to one an operator stores,
     * or left out for a public client.
     *
     * @param configuration the provider, as a new configuration; its ownership is set here
     * @param ssoRoleId     the role granted to a user the provider signs in for the first time, one of those
     *                      {@code TenantMemberService.findRoles} lists, or null for membership alone
     * @return the tenant as saved, naming the configuration
     */
    @AuthzCheck(permission = AuthzUtil.CAN_EDIT)
    Future<Tenant> configureSso(OidcConfiguration configuration, String ssoRoleId);

    /**
     * Removes the caller's tenant's identity provider, so its users sign in with passwords; a user the provider
     * created keeps its account.
     *
     * @return the tenant as saved
     */
    @AuthzCheck(permission = AuthzUtil.CAN_EDIT)
    Future<Tenant> removeSso();

}
