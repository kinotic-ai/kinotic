package org.kinotic.core.internal.api.secret;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretAsyncClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.kinotic.core.api.config.AzureProperties;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Azure Key Vault backed secret storage.
 * Batch methods use Reactor to parallelize vault operations.
 */
public class AzureKeyVaultBackend implements SecretStorageBackend {

    private final SecretAsyncClient client;

    public AzureKeyVaultBackend(AzureProperties settings) {
        if (settings == null || settings.getVaultUrl() == null) {
            throw new IllegalArgumentException("Azure vault URL must be configured when using azure backend");
        }
        this.client = new SecretClientBuilder()
                .vaultUrl(settings.getVaultUrl())
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildAsyncClient();
    }

    public AzureKeyVaultBackend(SecretAsyncClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<Void> setSecret(String derivedName, String value) {
        return client.setSecret(derivedName, value)
                     .then()
                     .toFuture();
    }

    @Override
    public CompletableFuture<String> getSecret(String derivedName) {
        return client.getSecret(derivedName)
                     .map(secret -> secret.getValue())
                     .toFuture();
    }

    @Override
    public CompletableFuture<Void> deleteSecret(String derivedName) {
        return client.beginDeleteSecret(derivedName)
                     .next()
                     .then()
                     .toFuture();
    }

    @Override
    public CompletableFuture<Void> setSecrets(Map<String, String> derivedNameToValue) {
        return Flux.fromIterable(derivedNameToValue.entrySet())
                   .flatMap(e -> client.setSecret(e.getKey(), e.getValue()))
                   .then()
                   .toFuture();
    }

    @Override
    public CompletableFuture<Map<String, String>> getSecrets(List<String> derivedNames) {
        return Flux.fromIterable(derivedNames)
                   .flatMap(name -> client.getSecret(name)
                                         .map(secret -> Map.entry(name, secret.getValue())))
                   .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                   .toFuture();
    }

    @Override
    public CompletableFuture<Void> deleteSecrets(List<String> derivedNames) {
        return Flux.fromIterable(derivedNames)
                   .flatMap(name -> client.beginDeleteSecret(name).next())
                   .then()
                   .toFuture();
    }
}
