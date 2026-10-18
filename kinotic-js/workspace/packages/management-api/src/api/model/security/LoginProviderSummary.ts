import type { OidcConfigurationType } from '@/api/model/security/OidcConfigurationType'
import type { OidcProviderKind } from '@/api/model/security/OidcProviderKind'

/** What a login page, or the account's profile, is told of an OIDC configuration it may start a flow through. */
export interface LoginProviderSummary {
    configurationId: string
    name: string
    provider: OidcProviderKind
    type: OidcConfigurationType
}
