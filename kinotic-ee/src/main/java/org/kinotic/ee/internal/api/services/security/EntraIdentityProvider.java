package org.kinotic.ee.internal.api.services.security;

import org.kinotic.domain.api.model.security.OidcProviderType;

/**
 * Microsoft Entra, where the account is the Entra directory named by its tenant id, or {@code common} for every
 * Microsoft account and {@code organizations} for every work or school account.
 */
public class EntraIdentityProvider extends AccountIdentityProvider {

    public EntraIdentityProvider() {
        super("https://login.microsoftonline.com/{providerAccount}/v2.0",
              "common|organizations|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
              "Directory (tenant) ID, or common or organizations");
    }

    @Override
    public OidcProviderType type() {
        return OidcProviderType.AZURE_AD;
    }
}
