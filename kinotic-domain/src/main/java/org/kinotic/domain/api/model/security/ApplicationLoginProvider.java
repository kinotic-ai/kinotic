package org.kinotic.domain.api.model.security;

/**
 * A sign-in provider an application's login offers, as its login and sign-up pages render it: what to show on its
 * button and the id that starts a sign-in through it.
 *
 * @param id       the id of the configuration, which the page posts to begin a sign-in through it
 * @param name     the name the button shows
 * @param provider the provider kind, for branding
 * @param signUp   whether a person the provider verifies who has no user yet becomes one on that first sign-in, so
 *                 the button signs people up as well as in
 */
public record ApplicationLoginProvider(String id, String name, OidcProviderType provider, boolean signUp) {
}
