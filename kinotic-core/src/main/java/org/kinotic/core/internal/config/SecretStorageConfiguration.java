package org.kinotic.core.internal.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretAsyncClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.config.KinoticProperties.SecretStorageSettings;
import org.kinotic.core.internal.secret.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Base64;

@Configuration
@Slf4j
public class SecretStorageConfiguration {

    @Bean
    public SecretNameDeriver secretNameDeriver(KinoticProperties properties) {
        SecretStorageSettings settings = properties.getSecretStorage();
        if (settings != null && settings.getMasterKey() != null) {
            byte[] masterKey = Base64.getDecoder().decode(settings.getMasterKey());
            return new SecretNameDeriver(masterKey);
        }
        // Default master key for in-memory/dev usage — not secure for production
        byte[] defaultKey = new byte[32];
        log.warn("No secret storage master key configured, using insecure default key");
        return new SecretNameDeriver(defaultKey);
    }

    @Bean
    public SecretStorageBackend secretStorageBackend(KinoticProperties properties) throws IOException {
        SecretStorageSettings settings = properties.getSecretStorage();
        if (settings == null || settings.getBackend() == null) {
            log.info("No secret storage backend configured, using in-memory storage");
            return new InMemoryBackend();
        }
        return switch (settings.getBackend()) {
            case "chronicle-map" -> createChronicleMapBackend(settings);
            case "azure" -> createAzureBackend(settings);
            default -> throw new IllegalArgumentException("Unknown secret storage backend: " + settings.getBackend());
        };
    }

    private ChronicleMapBackend createChronicleMapBackend(SecretStorageSettings settings) throws IOException {
        String filePath = System.getProperty("java.io.tmpdir") + "/kinotic-secrets.dat";
        int maxEntries = 10000;
        if (settings.getChronicleMap() != null) {
            if (settings.getChronicleMap().getFilePath() != null) {
                filePath = settings.getChronicleMap().getFilePath();
            }
            maxEntries = settings.getChronicleMap().getMaxEntries();
        }
        log.info("Using Chronicle Map secret storage at {}", filePath);
        return new ChronicleMapBackend(filePath, maxEntries);
    }

    private AzureKeyVaultBackend createAzureBackend(SecretStorageSettings settings) {
        if (settings.getAzure() == null || settings.getAzure().getVaultUrl() == null) {
            throw new IllegalArgumentException("Azure vault URL must be configured when using azure backend");
        }
        log.info("Using Azure Key Vault secret storage at {}", settings.getAzure().getVaultUrl());
        SecretAsyncClient client = new SecretClientBuilder()
                .vaultUrl(settings.getAzure().getVaultUrl())
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildAsyncClient();
        return new AzureKeyVaultBackend(client);
    }
}
