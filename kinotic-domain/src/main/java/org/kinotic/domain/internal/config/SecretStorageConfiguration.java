package org.kinotic.domain.internal.config;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.ee.services.SecretStorageBackend;
import org.kinotic.domain.internal.api.ee.license.CurrentLicenseService;
import org.kinotic.domain.api.model.license.EnterpriseFeature;
import org.kinotic.domain.internal.api.ee.services.AzureKeyVaultBackend;
import org.kinotic.domain.internal.api.services.secret.InMemoryBackend;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public SecretStorageBackend azureKeyVaultBackend(KinoticDomainProperties properties,
                                                     Vertx vertx,
                                                     CurrentLicenseService currentLicenseService) {
        // the server starts whatever its license, so a restart never depends on one; the backend refuses new
        // secrets until a key that turns the feature on is installed
        if (!currentLicenseService.allows(EnterpriseFeature.AZURE_KEY_VAULT)) {
            log.warn("No installed Kinotic Enterprise License key turns on {} now, so Azure Key Vault secrets can be read and deleted but not stored until one is installed",
                     EnterpriseFeature.AZURE_KEY_VAULT);
        }
        log.info("Using Azure Key Vault secret storage");
        return new AzureKeyVaultBackend(properties.getDomain().getSecretStorage().getAzure(), vertx, currentLicenseService);
    }
}
