/**
 * The authentication methods an administrator may choose when inviting a member into a scope.
 * Mirrors the backend {@code InviteOptions}: LOCAL is always available; OIDC is available only
 * when the scope has an enabled provider, in which case {@link oidcProviderName} is its display name.
 */
export interface InviteOptions {
    localEnabled: boolean
    oidcEnabled: boolean
    oidcProviderName: string | null
}
