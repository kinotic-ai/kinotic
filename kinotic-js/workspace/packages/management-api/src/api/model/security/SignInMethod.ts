import type { OidcProviderKind } from '@/api/model/security/OidcProviderKind'
import type { SignInMethodKind } from '@/api/model/security/SignInMethodKind'

/** The id of the account's password as a sign-in method. */
export const PASSWORD_SIGN_IN_METHOD_ID = 'password'

/**
 * One way the signed-in account authenticates, as the profile page lists them: the account's password, or a
 * provider identity bound to it. Nothing of the credential itself travels.
 */
export interface SignInMethod {
    /** What names the method to removeSignInMethod: the binding's id for a provider identity, 'password' for the password. */
    id: string
    kind: SignInMethodKind
    /** The name to show: the configuration the identity was asserted under, or "Password". */
    name: string
    /** The provider kind, for branding, or null for the password. */
    provider: OidcProviderKind | null
    /** When the method was added, or null for the password. */
    created: string | null
}
