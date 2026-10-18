package org.kinotic.domain.api.model.security;

/**
 * What a sign-in page or an administrator's picker needs to know of a configuration a scope offers:
 * enough to render and choose it, and nothing of how the server reaches or authenticates to the
 * provider.
 *
 * @param configurationId the id of the {@link OidcConfiguration} offered
 * @param name            the name the provider is shown by
 * @param provider        the provider kind, for branding
 * @param type            whose registration it is: one the organization made for the scope, or one Kinotic manages
 */
public record LoginProviderSummary(String configurationId, String name, OidcProviderKind provider, OidcConfigurationType type) {

    /** The summary of {@code configuration}. */
    public static LoginProviderSummary of(OidcConfiguration configuration) {
        return new LoginProviderSummary(configuration.getId(), configuration.getName(), configuration.getProvider(), configuration.getType());
    }
}
