package org.kinotic.domain.internal.api.ee.services;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.exception.ResourceNotFoundException;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretAsyncClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.kinotic.domain.api.config.AzureProperties;
import org.kinotic.domain.api.ee.services.SecretStorageBackend;
import org.kinotic.domain.internal.api.ee.license.CurrentLicense;
import org.kinotic.domain.internal.api.ee.license.EnterpriseFeature;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Azure Key Vault backed secret storage.
 * Batch methods use Reactor to parallelize vault operations.
 * Secrets are read and deleted whatever the license; storing a secret fails with an {@link IllegalStateException}
 * unless the current license turns {@link EnterpriseFeature#AZURE_KEY_VAULT} on.
 */
public class AzureKeyVaultBackend implements SecretStorageBackend {

    private static final int CONFLICT = 409;

    private final SecretAsyncClient client;
    private final Vertx vertx;
    private final CurrentLicense currentLicense;

    public AzureKeyVaultBackend(AzureProperties settings, Vertx vertx, CurrentLicense currentLicense) {
        if (settings == null || settings.getVaultUrl() == null) {
            throw new IllegalArgumentException("Azure vault URL must be configured when using azure backend");
        }
        this.client = new SecretClientBuilder()
                .vaultUrl(settings.getVaultUrl())
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildAsyncClient();
        this.vertx = vertx;
        this.currentLicense = currentLicense;
    }

    @Override
    public Future<Void> setSecret(String derivedName, String value) {
        if (!currentLicense.allows(EnterpriseFeature.AZURE_KEY_VAULT)) {
            return unlicensedWrite();
        }
        return Future.fromCompletionStage(set(derivedName, value)
                                                .then()
                                                .toFuture(),
                                          vertx.getOrCreateContext());
    }

    @Override
    public Future<String> getSecret(String derivedName) {
        return Future.fromCompletionStage(get(derivedName)
                                                .map(KeyVaultSecret::getValue)
                                                .toFuture(),
                                          vertx.getOrCreateContext());
    }

    @Override
    public Future<Void> deleteSecret(String derivedName) {
        return Future.fromCompletionStage(delete(derivedName)
                                                .toFuture(),
                                          vertx.getOrCreateContext());
    }

    @Override
    public Future<Void> setSecrets(Map<String, String> derivedNameToValue) {
        if (!currentLicense.allows(EnterpriseFeature.AZURE_KEY_VAULT)) {
            return unlicensedWrite();
        }
        return Future.fromCompletionStage(Flux.fromIterable(derivedNameToValue.entrySet())
                                              .flatMap(e -> set(e.getKey(), e.getValue()))
                                              .then()
                                              .toFuture(),
                                          vertx.getOrCreateContext());
    }

    @Override
    public Future<Map<String, String>> getSecrets(List<String> derivedNames) {
        return Future.fromCompletionStage(Flux.fromIterable(derivedNames)
                                              .flatMap(name -> get(name)
                                                      .map(secret -> Map.entry(name, secret.getValue())))
                                              .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                                              .toFuture(),
                                          vertx.getOrCreateContext());
    }

    @Override
    public Future<Void> deleteSecrets(List<String> derivedNames) {
        return Future.fromCompletionStage(Flux.fromIterable(derivedNames)
                                              .flatMap(this::delete)
                                              .then()
                                              .toFuture(),
                                          vertx.getOrCreateContext());
    }

    // Soft delete, which every vault has, keeps a deleted secret's name reserved for the retention
    // period and answers a set under it with 409. Recovering the deleted secret frees the name, and
    // the set that follows adds a new version, which is the one a get returns.
    private Mono<KeyVaultSecret> set(String derivedName, String value) {
        return client.setSecret(derivedName, value)
                     .onErrorResume(AzureKeyVaultBackend::isNameReserved,
                                    e -> client.beginRecoverDeletedSecret(derivedName)
                                               .last()
                                               .then(client.setSecret(derivedName, value)));
    }

    // Empty when the vault holds no secret of that name, which the contract reports as null or omits
    private Mono<KeyVaultSecret> get(String derivedName) {
        return client.getSecret(derivedName)
                     .onErrorResume(ResourceNotFoundException.class, e -> Mono.empty());
    }

    // A name the vault does not hold is already deleted
    private Mono<Void> delete(String derivedName) {
        return client.beginDeleteSecret(derivedName)
                     .next()
                     .then()
                     .onErrorResume(ResourceNotFoundException.class, e -> Mono.empty());
    }

    private static Future<Void> unlicensedWrite() {
        return Future.failedFuture(new IllegalStateException(
                "Storing a secret in Azure Key Vault needs a Kinotic Enterprise License that turns on "
                        + EnterpriseFeature.AZURE_KEY_VAULT + "; secrets already stored can still be read and deleted"));
    }

    private static boolean isNameReserved(Throwable error) {
        return error instanceof HttpResponseException http
                && http.getResponse() != null
                && http.getResponse().getStatusCode() == CONFLICT;
    }
}
