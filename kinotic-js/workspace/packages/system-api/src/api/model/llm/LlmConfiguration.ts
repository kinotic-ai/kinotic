import { LlmProviderType } from '@/api/model/llm/LlmProviderType'

/**
 * A large language model the system's services call: the provider, the model, where the provider's API is
 * reached and the options each request carries. The API key the provider is called with is held in the secret
 * storage and is never a field of the configuration; apiKeyHeld says whether one is held.
 */
export class LlmConfiguration {

    public id: string | null = null

    /** The name a service selects the configuration by, unique in the system. */
    public name: string

    public provider: LlmProviderType

    /** The provider's id of the model, such as claude-opus-5-5. */
    public model: string

    /** The base URL of the provider's API, or null for the provider's public API. */
    public endpoint: string | null = null

    /** The most tokens a response may hold, or null for the calling service's default. */
    public maxTokens: number | null = null

    /** The sampling temperature, or null for the provider's default. */
    public temperature: number | null = null

    /** Whether an API key is held for the configuration. */
    public apiKeyHeld: boolean = false

    public created: string | null = null

    public updated: string | null = null

    constructor(name: string, provider: LlmProviderType, model: string) {
        this.name = name
        this.provider = provider
        this.model = model
    }
}
