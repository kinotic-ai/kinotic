package org.kinotic.domain.api.model.security;

/**
 * A sign-in provider an application's login offers, as its login and sign-up pages render it: an email and a password,
 * which the pages offer as a form, or an OIDC provider, which they offer as a button that starts a sign-in through it.
 *
 * @param configurationId the id of the {@link PasswordConfiguration} or {@link OidcConfiguration} offered; the page
 *                        posts an OIDC provider's to begin a sign-in through it
 * @param type            how the provider signs a person in
 * @param name            the name the page shows for the provider
 * @param provider        the OIDC provider kind, for branding, or {@code null} for an email and a password
 * @param signUp          whether a person who has no user yet becomes one: with an email and a password, by signing
 *                        up with them, or through an OIDC provider, on the first sign-in of a person it verifies
 */
public record ApplicationLoginProvider(String configurationId, LoginProviderType type, String name, OidcProviderType provider, boolean signUp) {
}
