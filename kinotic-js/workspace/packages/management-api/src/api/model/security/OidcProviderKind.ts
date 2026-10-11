/**
 * The kinds of identity provider an OIDC configuration can name; OIDC for any other provider that speaks
 * the standard protocol.
 */
export enum OidcProviderKind {
    GOOGLE = 'google',
    AZURE_AD = 'azure-ad',
    MICROSOFT_LIVE = 'microsoft-live',
    GITHUB = 'github',
    APPLE = 'apple',
    FACEBOOK = 'facebook',
    LINKEDIN = 'linkedin',
    SALESFORCE = 'salesforce',
    AMAZON_COGNITO = 'amazon-cognito',
    KEYCLOAK = 'keycloak',
    AUTH0 = 'auth0',
    OIDC = 'oidc'
}
