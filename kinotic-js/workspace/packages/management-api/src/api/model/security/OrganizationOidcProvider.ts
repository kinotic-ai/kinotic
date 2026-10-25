import type { OidcProviderType } from '@/api/model/security/OidcProviderType'

/** A provider kind an organization may configure for itself, and what it asks the organization for. */
export interface OrganizationOidcProvider {
    provider: OidcProviderType
    /** What the organization is asked for as its account at the provider, or null for a kind with one issuer for everyone. */
    providerAccountLabel: string | null
    /** The form the account takes, matched against the whole value, or null for a kind that takes no account. */
    providerAccountPattern: RegExp | null
    /**
     * The accounts that admit the users of every directory at the provider rather than one, which an application's or a
     * tenant's configuration may name in place of an account in the pattern's form; an organization's own login names
     * one directory of its own.
     */
    everyDirectoryAccounts: string[]
}
