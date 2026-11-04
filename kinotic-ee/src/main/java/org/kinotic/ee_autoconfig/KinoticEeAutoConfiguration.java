package org.kinotic.ee_autoconfig;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.ee.services.SecretStorageBackend;
import org.kinotic.domain.api.ee.services.OrganizationIdentityProvider;
import org.kinotic.ee.internal.api.services.AzureKeyVaultBackend;
import org.kinotic.ee.internal.api.services.security.EntraIdentityProvider;
import org.kinotic.ee.internal.api.services.security.OktaIdentityProvider;
import org.kinotic.ee.internal.license.ConditionalOnEnterpriseFeature;
import org.kinotic.ee.internal.license.EnterpriseFeature;
import org.kinotic.ee.internal.license.License;
import org.kinotic.ee.internal.license.LicenseVerifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * This is the autoconfiguration class for the Kinotic Enterprise Features.
 * It is defined in a separate package because it must not be scanned by the spring context
 */
@Slf4j
@AutoConfiguration
public class KinoticEeAutoConfiguration {

    @Bean
    @ConditionalOnProperty(value = "kinotic.domain.secretStorage.backend", havingValue = "AZURE")
    public SecretStorageBackend azureKeyVaultBackend(KinoticDomainProperties properties, Vertx vertx, Environment environment) {
        License license = LicenseVerifier.licenseOf(environment);
        // falling back to in-memory storage would lose every secret on restart, so the server refuses to start
        if (license == null || !license.allows(EnterpriseFeature.AZURE_KEY_VAULT)) {
            throw new IllegalStateException("Azure Key Vault secret storage needs a Kinotic Enterprise License key in "
                                                    + LicenseVerifier.LICENSE_KEY_PROPERTY + " that turns on "
                                                    + EnterpriseFeature.AZURE_KEY_VAULT);
        }
        log.info("Using Azure Key Vault secret storage");
        return new AzureKeyVaultBackend(properties.getDomain().getSecretStorage().getAzure(), vertx);
    }

    @Bean
    @ConditionalOnEnterpriseFeature(EnterpriseFeature.ENTERPRISE_IDENTITY_PROVIDERS)
    public OrganizationIdentityProvider entraIdentityProvider() {
        return new EntraIdentityProvider();
    }

    @Bean
    @ConditionalOnEnterpriseFeature(EnterpriseFeature.ENTERPRISE_IDENTITY_PROVIDERS)
    public OrganizationIdentityProvider oktaIdentityProvider() {
        return new OktaIdentityProvider();
    }
}
