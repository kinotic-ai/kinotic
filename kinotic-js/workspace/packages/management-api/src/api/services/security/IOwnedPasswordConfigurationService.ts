import { MANAGEMENT_API_ZONE } from '@/api/Zones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import { FunctionalIterablePage, type IterablePage, type Page, type Pageable } from '@kinotic-ai/core'
import type { PasswordConfiguration } from '@/api/model/security/PasswordConfiguration'

/**
 * The caller's organization's password configurations: sign-in with an email and a password at its own login, at one
 * of its applications' and at one tenant's, each managed among the organization's sign-in providers beside its OIDC
 * configurations. Every operation acts for the organization of the authenticated participant and reaches no other
 * organization's configurations.
 */
export interface IOwnedPasswordConfigurationService {

    /** The organization's configurations, of every scope and enabled or not, a page at a time. */
    findAll(pageable: Pageable): Promise<IterablePage<PasswordConfiguration>>

    /**
     * Saves the configuration of the sign-in scope it names, replacing that scope's existing one. Its `organizationId` is
     * the caller's and its id is derived from the scope, whatever either carried. `createUserOnSignIn` is taken by an
     * application's own configuration only, and only while the system manages the application's tenants. Disabling the organization's own configuration rejects, naming the members
     * it would lock out, while an enabled member holds a password and no identity of a provider the organization's login
     * offers. Returns the configuration as saved, with its id.
     */
    save(configuration: PasswordConfiguration): Promise<PasswordConfiguration>

    /**
     * Deletes the organization's configuration, so its scope's login takes no password, rejected as disabling it is by
     * `save`; nothing happens for an id the organization has no configuration by.
     */
    deleteById(id: string): Promise<void>

}

export class OwnedPasswordConfigurationService implements IOwnedPasswordConfigurationService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.security.OwnedPasswordConfigurationService`)
    }

    public async findAll(pageable: Pageable): Promise<IterablePage<PasswordConfiguration>> {
        const page: Page<PasswordConfiguration> = await this.findAllSinglePage(pageable)
        return new FunctionalIterablePage(pageable, page, (pageable: Pageable) => this.findAllSinglePage(pageable))
    }

    public save(configuration: PasswordConfiguration): Promise<PasswordConfiguration> {
        return this.serviceProxy.invoke('save', [configuration])
    }

    public deleteById(id: string): Promise<void> {
        return this.serviceProxy.invoke('deleteById', [id])
    }

    private findAllSinglePage(pageable: Pageable): Promise<Page<PasswordConfiguration>> {
        return this.serviceProxy.invoke('findAll', [pageable])
    }

}
