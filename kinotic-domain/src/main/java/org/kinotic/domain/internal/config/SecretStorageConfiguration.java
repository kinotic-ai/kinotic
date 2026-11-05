package org.kinotic.domain.internal.config;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.ee.services.SecretStorageBackend;
import org.kinotic.domain.internal.api.ee.license.EnterpriseFeature;
import org.kinotic.domain.internal.api.ee.license.License;
import org.kinotic.domain.internal.api.ee.license.LicenseVerifier;
import org.kinotic.domain.internal.api.ee.services.AzureKeyVaultBackend;
import org.kinotic.domain.internal.api.services.secret.InMemoryBackend;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@Slf4j
public class SecretStorageConfiguration {

    @Bean
    @ConditionalOnProperty(value = "kinotic.domain.secretStorage.backend", havingValue = "IN_MEMORY", matchIfMissing = true)
    public SecretStorageBackend inMemorySecretStorageBackend() {
        log.info("Using in-memory secret storage");
        return new InMemoryBackend();
    }

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
}
