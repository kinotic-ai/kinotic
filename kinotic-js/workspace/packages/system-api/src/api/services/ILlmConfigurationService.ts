import { SYSTEM_API_ZONE } from '@kinotic-ai/management-api'
import type { IKinotic, IServiceProxy, Page, Pageable } from '@kinotic-ai/core'
import type { LlmConfiguration } from '@/api/model/llm/LlmConfiguration'

/**
 * The large language models the system's services call, each an LlmConfiguration that system users
 * manage and a service selects by name. A configuration's API key is given on save and held in the system's
 * scope of the secret storage until a save gives another or the configuration is deleted. Published in the
 * system zone, which only SYSTEM participants and the system's own cluster members may address.
 */
export interface ILlmConfigurationService {

    findAll(pageable: Pageable): Promise<Page<LlmConfiguration>>

    /** Resolves null when no configuration has the given id. */
    findById(id: string): Promise<LlmConfiguration | null>

    /** Resolves null when no configuration has the given name. */
    findByName(name: string): Promise<LlmConfiguration | null>

    /**
     * Saves a configuration, new or changed, and makes apiKey, when given, the key it calls its provider with.
     * A null or blank apiKey keeps the key held for the configuration, if any. Its name must be one no other
     * configuration has.
     */
    save(configuration: LlmConfiguration, apiKey: string | null): Promise<LlmConfiguration>

    /** Deletes the configuration and the API key held for it. */
    deleteById(id: string): Promise<void>
}

export class LlmConfigurationService implements ILlmConfigurationService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${SYSTEM_API_ZONE}~org.kinotic.system.api.services.LlmConfigurationService`)
    }

    public findAll(pageable: Pageable): Promise<Page<LlmConfiguration>> {
        return this.serviceProxy.invoke('findAll', [pageable])
    }

    public findById(id: string): Promise<LlmConfiguration | null> {
        return this.serviceProxy.invoke('findById', [id])
    }

    public findByName(name: string): Promise<LlmConfiguration | null> {
        return this.serviceProxy.invoke('findByName', [name])
    }

    public save(configuration: LlmConfiguration, apiKey: string | null): Promise<LlmConfiguration> {
        return this.serviceProxy.invoke('save', [configuration, apiKey])
    }

    public deleteById(id: string): Promise<void> {
        return this.serviceProxy.invoke('deleteById', [id])
    }
}
