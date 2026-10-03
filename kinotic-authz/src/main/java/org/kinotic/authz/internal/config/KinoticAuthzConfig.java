package org.kinotic.authz.internal.config;

import dev.openfga.sdk.api.OpenFgaApi;
import dev.openfga.sdk.api.configuration.ApiToken;
import dev.openfga.sdk.api.configuration.Credentials;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import org.kinotic.authz.api.config.AuthzProperties;
import org.kinotic.authz.api.config.KinoticAuthzProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Wires the OpenFGA client the authorization services call.
 */
@Configuration
public class KinoticAuthzConfig {

    @Bean
    public OpenFgaApi openFgaApi(KinoticAuthzProperties properties) throws FgaInvalidParameterException {
        AuthzProperties authz = properties.getAuthz();
        dev.openfga.sdk.api.configuration.Configuration configuration =
                new dev.openfga.sdk.api.configuration.Configuration().apiUrl(authz.getApiUrl());
        if (StringUtils.hasText(authz.getApiToken())) {
            configuration.credentials(new Credentials(new ApiToken(authz.getApiToken())));
        }
        return new OpenFgaApi(configuration);
    }

}
