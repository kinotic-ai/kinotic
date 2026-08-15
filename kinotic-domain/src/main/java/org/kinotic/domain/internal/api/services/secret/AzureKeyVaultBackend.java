package org.kinotic.domain.internal.api.services.secret;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretAsyncClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.kinotic.domain.api.config.AzureProperties;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Azure Key Vault backed secret storage.
 * Batch methods use Reactor to parallelize vault operations.
 */
public class AzureKeyVaultBackend implements SecretStorageBackend {

    private final SecretAsyncClient client;
    private final Vertx vertx;

    public AzureKeyVaultBackend(AzureProperties settings, Vertx vertx) {
        if (settings == null || settings.getVaultUrl() == null) {
            throw new IllegalArgumentException("Azure vault URL must be configured when using azure backend");
        }
        this.client = new SecretClientBuilder()
                .vaultUrl(settings.getVaultUrl())
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildAsyncClient();
        this.vertx = vertx;
    }

    @Override
    public Future<Void> setSecret(String derivedName, String value) {
        return Future.fromCompletionStage(client.setSecret(derivedName, value)
                                                .then()
                                                .toFuture(),
                                          vertx.getOrCreateContext());
    }

    @Override
    public Future<String> getSecret(String derivedName) {
        return Future.fromCompletionStage(client.getSecret(derivedName)
                                                .map(KeyVaultSecret::getValue)
                                                .toFuture(),
                                          vertx.getOrCreateContext());
    }

    @Override
    public Future<Void> deleteSecret(String derivedName) {
        return Future.fromCompletionStage(client.beginDeleteSecret(derivedName)
                                                .next()
                                                .then()
                                                .toFuture(),
                                          vertx.getOrCreateContext());
    }

    @Override
    public Future<Void> setSecrets(Map<String, String> derivedNameToValue) {
        return Future.fromCompletionStage(Flux.fromIterable(derivedNameToValue.entrySet())
                                              .flatMap(e -> client.setSecret(e.getKey(), e.getValue()))
                                              .then()
                                              .toFuture(),
                                          vertx.getOrCreateContext());
    }

    @Override
    public Future<Map<String, String>> getSecrets(List<String> derivedNames) {
        return Future.fromCompletionStage(Flux.fromIterable(derivedNames)
                                              .flatMap(name -> client.getSecret(name)
                                                                    .map(secret -> Map.entry(name, secret.getValue())))
                                              .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                                              .toFuture(),
                                          vertx.getOrCreateContext());
    }

    @Override
    public Future<Void> deleteSecrets(List<String> derivedNames) {
        return Future.fromCompletionStage(Flux.fromIterable(derivedNames)
                                              .flatMap(name -> client.beginDeleteSecret(name).next())
                                              .then()
                                              .toFuture(),
                                          vertx.getOrCreateContext());
    }
}
