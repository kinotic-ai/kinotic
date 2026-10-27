import type { ClientRegistrationType } from '@/api/model/security/ClientRegistrationType'
import type { OidcConfigurationType } from '@/api/model/security/OidcConfigurationType'
import type { OidcProviderType } from '@/api/model/security/OidcProviderType'

/**
 * One of an organization's own OIDC provider configurations, offered at the one sign-in scope its `type` names:
 * the organization's own login, one of its applications' or one tenant's. The organization names a provider kind
 * offered to organizations and its account there, never a URL; the authority is derived from the two on every
 * save. The client secret is given on save and held in the organization's scope of the secret storage, so it is no
 * field here; `confidentialClient` says one is held.
 */
export interface OwnedOidcConfiguration {
    /** The configuration's id, minted on the first save; the last segment of the callback URL the provider is given. */
    id: string | null
    /** The kind, which is the sign-in scope the configuration is offered at. */
    type: OidcConfigurationType.ORGANIZATION | OidcConfigurationType.APPLICATION | OidcConfigurationType.TENANT
    /** The owning organization; set to the caller's on save, whatever was sent. */
    organizationId: string | null
    /** The application whose login offers the configuration, for an `APPLICATION` or `TENANT` kind. */
    applicationId?: string | null
    /** The tenant whose login offers the configuration, for a `TENANT` kind. */
    tenantId?: string | null
    /**
     * For an `APPLICATION` kind, whose registration with the provider a sign-in uses: `MANAGED`, Kinotic's, for a kind
     * `IOwnedOidcConfigurationService.findManagedProviders` lists, which needs no account, client id or secret and
     * keeps any held for a switch back; or `OWN`, the organization's, the default.
     */
    clientRegistrationType?: ClientRegistrationType
    /** The name the login page shows on the provider's button. */
    name: string
    /** A kind offered to organizations: `azure-ad`, `okta` or `google`. */
    provider: OidcProviderType
    /** The organization's account at the provider in the form the kind asks for: the directory (tenant) id for Entra, the Okta domain for Okta, nothing for Google. */
    providerAccount: string | null
    /** The client id of the organization's own registration; null for a `MANAGED` configuration that holds none. */
    clientId: string | null
    /** The issuer URL, derived from the provider kind and the account on every save. */
    authority: string | null
    audience: string | null
    enabled: boolean
    /** Whether a client secret is held for the configuration; set by the server on save, whatever was sent. */
    confidentialClient: boolean
    /**
     * Whether the configuration signs new people up: an identity the provider verifies that no user of the scope is
     * bound to becomes a user of the scope on its first sign-in. Taken by an application's configuration, while the
     * system manages the application's tenants, and by a tenant's: an organization's members are added by its
     * administrators, whom its provider then signs in.
     */
    createUserOnSignIn: boolean
    created: string | null
    updated: string | null
}
