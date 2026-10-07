package org.kinotic.domain.internal.config;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.config.SecretStorageProperties;
import org.kinotic.domain.internal.api.services.secret.AzureKeyVaultBackend;
import org.kinotic.domain.internal.api.services.secret.InMemoryBackend;
import org.kinotic.domain.internal.api.services.secret.SecretStorageBackend;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class SecretStorageConfiguration {

    @Bean
    public SecretStorageBackend secretStorageBackend(KinoticDomainProperties properties, Vertx vertx) {
        SecretStorageProperties settings = properties.getDomain().getSecretStorage();
        if (settings == null || settings.getBackend() == null) {
            log.info("No secret storage backend configured, using in-memory storage");
            return new InMemoryBackend();
        }
        return switch (settings.getBackend()) {
            case AZURE -> createAzureBackend(settings, vertx);
        };
    }

    private AzureKeyVaultBackend createAzureBackend(SecretStorageProperties settings, Vertx vertx) {
        log.info("Using Azure Key Vault secret storage");
        return new AzureKeyVaultBackend(settings.getAzure(), vertx);
    }
}
