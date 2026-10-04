import { APP_API_ZONE, type OidcConfiguration } from '@kinotic-ai/management-api'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { Tenant } from '@/api/model/Tenant'

/**
 * The tenant the connected user belongs to, for an application's own pages: what it is called, and the
 * identity provider its administrator chose for its users. Every function acts on the caller's tenant, and what the caller may do is
 * checked on that tenant in the application's store.
 */
export interface ITenantService {

    /** The caller's tenant. Needs tenant_can_view. */
    getTenant(): Promise<Tenant>

    /** Renames the caller's tenant; its id stays what it was. Needs tenant_can_edit. */
    rename(name: string): Promise<Tenant>

    /** The identity provider the caller's tenant signs its users in with, or null while it has none. */
    getSso(): Promise<OidcConfiguration | null>

    /**
     * Makes the given identity provider the caller's tenant's own, replacing the one it had: the tenant's
     * users sign in through it from the application's login page by the tenant's id, and a user it signs in
     * for the first time is created in the tenant, granted the given role on it. The configuration names the
     * provider kind, the client id and the authority; a client secret is named by reference to one an
     * operator stores, or left out for a public client. Needs tenant_can_edit.
     * @param configuration the provider, as a new configuration; its ownership is set by the platform
     * @param ssoRoleId the role granted to a user the provider signs in for the first time, one of those
     *        ITenantMemberService.findRoles lists, or null for membership alone
     * @return the tenant as saved, naming the configuration
     */
    configureSso(configuration: OidcConfiguration, ssoRoleId: string | null): Promise<Tenant>

    /**
     * Removes the caller's tenant's identity provider, so its users sign in with passwords; a user the
     * provider created keeps its account. Needs tenant_can_edit.
     * @return the tenant as saved
     */
    removeSso(): Promise<Tenant>

}

export class TenantService implements ITenantService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${APP_API_ZONE}~org.kinotic.domain.api.services.TenantService`)
    }

    public getTenant(): Promise<Tenant> {
        return this.serviceProxy.invoke('getTenant', [])
    }

    public rename(name: string): Promise<Tenant> {
        return this.serviceProxy.invoke('rename', [name])
    }

    public getSso(): Promise<OidcConfiguration | null> {
        return this.serviceProxy.invoke('getSso', [])
    }

    public configureSso(configuration: OidcConfiguration, ssoRoleId: string | null): Promise<Tenant> {
        return this.serviceProxy.invoke('configureSso', [configuration, ssoRoleId])
    }

    public removeSso(): Promise<Tenant> {
        return this.serviceProxy.invoke('removeSso', [])
    }

}
