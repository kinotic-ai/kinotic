package org.kinotic.domain.api.model.security;

/**
 * Whose registration with a provider an {@link ApplicationOidcConfiguration} signs in through. The application's
 * users are the same users under either, so an owner switches between the two without stranding anyone.
 */
public enum ClientRegistrationType {

    /**
     * Kinotic's registration with the provider, the {@link ApplicationSocialOidcConfiguration} of the same provider
     * kind: works with no setup, and the provider's consent screen shows Kinotic's name.
     */
    MANAGED,

    /**
     * The organization's own registration with the provider, whose client id and secret the configuration holds:
     * the provider's consent screen shows the application's own name and branding.
     */
    OWN
}
