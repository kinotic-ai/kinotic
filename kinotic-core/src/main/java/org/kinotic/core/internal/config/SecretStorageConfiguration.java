package org.kinotic.core.internal.config;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.config.SecretStorageProperties;
import org.kinotic.core.internal.api.secret.AzureKeyVaultBackend;
import org.kinotic.core.internal.api.secret.ChronicleMapBackend;
import org.kinotic.core.internal.api.secret.InMemoryBackend;
import org.kinotic.core.internal.api.secret.SecretStorageBackend;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@Slf4j
public class SecretStorageConfiguration {

    @Bean
    public SecretStorageBackend secretStorageBackend(KinoticProperties properties) throws IOException {
        SecretStorageProperties settings = properties.getSecretStorage();
        if (settings == null || settings.getBackend() == null) {
            log.info("No secret storage backend configured, using in-memory storage");
            return new InMemoryBackend();
        }
        return switch (settings.getBackend()) {
            case HFT -> createChronicleMapBackend(settings);
            case AZURE -> createAzureBackend(settings);
        };
    }

    private ChronicleMapBackend createChronicleMapBackend(SecretStorageProperties secretStorageProperties) throws IOException {
        log.info("Using Chronicle Map secret storage");
        return new ChronicleMapBackend(secretStorageProperties.getChronicleMap());
    }

    private AzureKeyVaultBackend createAzureBackend(SecretStorageProperties settings) {
        log.info("Using Azure Key Vault secret storage");
        return new AzureKeyVaultBackend(settings.getAzure());
    }
}
