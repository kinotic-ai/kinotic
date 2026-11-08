package org.kinotic.domain.internal.config;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.ee.services.SecretStorageBackend;
import org.kinotic.domain.internal.api.ee.license.CurrentLicense;
import org.kinotic.domain.internal.api.ee.license.EnterpriseFeature;
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
    public SecretStorageBackend azureKeyVaultBackend(KinoticDomainProperties properties, Vertx vertx, CurrentLicense currentLicense) {
        // falling back to in-memory storage would lose every secret on restart, so the server refuses to start; a
        // running server keeps the backend when its license lapses, for the same reason
        if (!currentLicense.allows(EnterpriseFeature.AZURE_KEY_VAULT)) {
            throw new IllegalStateException("Azure Key Vault secret storage needs an installed Kinotic Enterprise License key, or one in "
                                                    + CurrentLicense.LICENSE_KEY_PROPERTY + ", that turns on "
                                                    + EnterpriseFeature.AZURE_KEY_VAULT);
        }
        log.info("Using Azure Key Vault secret storage");
        return new AzureKeyVaultBackend(properties.getDomain().getSecretStorage().getAzure(), vertx);
    }
}
