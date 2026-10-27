/**
 * Whose registration with a provider an application's OIDC configuration signs in through. The application's users
 * are the same users under either, so an owner switches between the two without stranding anyone.
 */
export enum ClientRegistrationType {
    /** Kinotic's registration with the provider: works with no setup, and the consent screen shows Kinotic's name. */
    MANAGED = 'MANAGED',
    /**
     * The organization's own registration, whose client id and secret the configuration holds: the consent screen
     * shows the application's own name and branding.
     */
    OWN = 'OWN'
}
