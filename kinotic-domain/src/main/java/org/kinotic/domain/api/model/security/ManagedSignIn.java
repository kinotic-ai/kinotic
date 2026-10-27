package org.kinotic.domain.api.model.security;

/**
 * An application's configuration that signs in through Kinotic's registration with its provider, with the
 * system's registration it signs in through.
 *
 * @param configuration the application's configuration, using {@link ClientRegistrationType#MANAGED}
 * @param registration  the system's enabled registration of the configuration's provider kind
 */
public record ManagedSignIn(ApplicationOidcConfiguration configuration, ApplicationSocialOidcConfiguration registration) {
}
