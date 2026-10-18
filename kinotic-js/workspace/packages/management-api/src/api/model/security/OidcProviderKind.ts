/**
 * The provider kinds an OIDC configuration may name, as the platform writes them on the wire: the
 * key of each kind, such as 'azure-ad', never its Java constant name.
 */
export enum OidcProviderKind {
    GOOGLE = 'google',
    AZURE_AD = 'azure-ad',
    OKTA = 'okta',
    MICROSOFT_LIVE = 'microsoft-live',
    GITHUB = 'github',
    APPLE = 'apple',
    FACEBOOK = 'facebook',
    LINKEDIN = 'linkedin',
    SALESFORCE = 'salesforce',
    AMAZON_COGNITO = 'amazon-cognito',
    KEYCLOAK = 'keycloak',
    AUTH0 = 'auth0',
    /** Generic OIDC-compliant provider. */
    OIDC = 'oidc'
}
