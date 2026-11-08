package org.kinotic.domain.internal.config;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.ee.services.SecretStorageBackend;
import org.kinotic.domain.internal.api.ee.license.CurrentLicenseService;
import org.kinotic.domain.internal.api.ee.license.EnterpriseFeature;
import org.kinotic.domain.internal.api.ee.license.License;
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
        License license = currentLicenseService.find();
        if (license == null || !license.features().contains(EnterpriseFeature.AZURE_KEY_VAULT)) {
            throw new IllegalStateException("Azure Key Vault secret storage needs an installed Kinotic Enterprise License key, or one in "
                                                    + CurrentLicenseService.LICENSE_KEY_PROPERTY + ", that names "
                                                    + EnterpriseFeature.AZURE_KEY_VAULT);
        }
        // a license past its grace period still starts the server, so the secrets it stored stay readable; the backend refuses new ones
        if (!currentLicenseService.allows(EnterpriseFeature.AZURE_KEY_VAULT)) {
            log.warn("The Kinotic Enterprise License for {} ended its grace period at {}, so Azure Key Vault secrets can be read and deleted but not stored",
                     license.licensee(), license.graceEndsAt());
        }
        log.info("Using Azure Key Vault secret storage");
        return new AzureKeyVaultBackend(properties.getDomain().getSecretStorage().getAzure(), vertx, currentLicenseService);
    }
}
