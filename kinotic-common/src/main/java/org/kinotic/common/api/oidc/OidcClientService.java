package org.kinotic.common.api.oidc;

import io.vertx.core.Future;

/**
 * Creates the {@link OidcClient}s that sign people in through a provider.
 */
public interface OidcClientService {

    /**
     * Creates the client of the registration {@code settings} describes, reading the provider's OpenID Connect
     * discovery document at the authority unless the settings name the authorization and token endpoints.
     * The caller owns the client and {@link OidcClient#close closes} it when done with it.
     *
     * @param settings the registration
     * @return the client, or a failure when the settings are incomplete or discovery fails
     */
    Future<OidcClient> createClient(OidcClientSettings settings);

}
