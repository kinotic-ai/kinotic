import type { Identifiable } from '@kinotic-ai/core'
import { OidcProviderKind } from '@/api/model/security/OidcProviderKind'

/**
 * An OIDC provider configuration owned by an organization, or by one tenant of one of its applications, which
 * signs the owner's users in. A client secret is never stored on it: secretNameRef names one an operator
 * stores, or is left out for a public client.
 */
export class OidcConfiguration implements Identifiable<string> {

    /** Assigned by the platform when the configuration is first saved. */
    public id!: string

    public organizationId!: string

    /**
     * The application and tenant that own the configuration, for a tenant's own SSO; both null for an
     * organization's configuration.
     */
    public applicationId: string | null = null

    public tenantId: string | null = null

    /** The name the provider is shown by. */
    public name: string = ''

    public provider: OidcProviderKind = OidcProviderKind.OIDC

    public clientId: string = ''

    /** The name of a client secret an operator stored, resolved at sign-in; null for a public client. */
    public secretNameRef: string | null = null

    /** The provider's issuer, whose discovery document names its endpoints. */
    public authority: string = ''

    /** Endpoint overrides for a provider whose discovery document lacks them; null to use the discovered ones. */
    public authorizationUri: string | null = null

    public tokenUri: string | null = null

    public userInfoUri: string | null = null

    public userEmailsUri: string | null = null

    /** The scopes requested, space separated; null for the provider's defaults. */
    public scopes: string | null = null

    public audience: string | null = null

    public enabled: boolean = false

    public created: string | null = null

    public updated: string | null = null

}
