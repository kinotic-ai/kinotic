/** The kind of an OIDC configuration, which is the scope its sign-in is offered at. */
export enum OidcConfigurationType {
    ORG_SIGNUP = 'ORG_SIGNUP',
    APP_SOCIAL = 'APP_SOCIAL',
    ORGANIZATION = 'ORGANIZATION',
    APPLICATION = 'APPLICATION',
    TENANT = 'TENANT'
}
