
package org.kinotic.auth.api.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import org.kinotic.auth.api.domain.OidcProvider;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration class for OIDC authentication.
 * 
 * The OIDC verifier will:
 * 1. Extract the JWT token from the Authorization header
 * 2. Verify the token signature using JWKS from the issuer
 * 3. Validate the issuer against the list of providers
 * 4. Validate the audience against the allowed audiences list of the provider
 * 5. Check token expiration
 * 6. Create a Participant with user information from the token claims
 * 
 * Caching:
 * - JWKS keys are cached for 1 hour
 * - Well-known configurations are cached for 24 hours
 * - Cache sizes are limited to prevent memory issues
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "oidc-security-service")
public class OidcSecurityServiceProperties {

    /**
     * Master switch for enabling/disabling the OIDC security service.
     */
    private boolean enabled = false;

    /**
     * The field name for the tenant ID in the JWT token.
     */
    private String tenantIdFieldName = "tenantId";

    /**
     * List of OIDC providers to be used for authentication.
     */
    private List<OidcProvider> oidcProviders;

    /**
     * enable debugging for the OIDC security service in the UI. 
     */
    private boolean debug;

    /**
     * The path that the frontend configuration overrides will be served from.
     * Will override any default configurations in the app-config.json file.
     */
    private String frontendConfigurationPath = "/app-config.override.json";
    
}
