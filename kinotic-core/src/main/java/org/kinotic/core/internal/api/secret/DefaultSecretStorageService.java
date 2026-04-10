package org.kinotic.core.internal.api.secret;

import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.secret.SecretStorageService;
import org.kinotic.core.internal.secret.SecretNameDeriver;
import org.kinotic.core.internal.secret.SecretStorageBackend;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DefaultSecretStorageService implements SecretStorageService {

    private final SecretNameDeriver secretNameDeriver;
    private final SecretStorageBackend secretStorageBackend;

    @Override
    public CompletableFuture<Void> setSecret(String secretScope, String key, String value) {
        String derivedName = secretNameDeriver.derive(secretScope, key);
        return secretStorageBackend.setSecret(derivedName, value);
    }

    @Override
    public CompletableFuture<String> getSecret(String secretScope, String key) {
        String derivedName = secretNameDeriver.derive(secretScope, key);
        return secretStorageBackend.getSecret(derivedName);
    }

    @Override
    public CompletableFuture<Void> deleteSecret(String secretScope, String key) {
        String derivedName = secretNameDeriver.derive(secretScope, key);
        return secretStorageBackend.deleteSecret(derivedName);
    }

    @Override
    public CompletableFuture<Void> setSecrets(String secretScope, Map<String, String> secrets) {
        Map<String, String> derived = secrets.entrySet()
                                             .stream()
                                             .collect(Collectors.toMap(
                                                     e -> secretNameDeriver.derive(secretScope, e.getKey()),
                                                     Map.Entry::getValue));
        return secretStorageBackend.setSecrets(derived);
    }

    @Override
    public CompletableFuture<Map<String, String>> getSecrets(String secretScope, List<String> keys) {
        // Build mapping from derived name back to original key
        Map<String, String> derivedToOriginal = keys.stream()
                                                    .collect(Collectors.toMap(
                                                            k -> secretNameDeriver.derive(secretScope, k),
                                                            k -> k));

        return secretStorageBackend.getSecrets(List.copyOf(derivedToOriginal.keySet()))
                                   .thenApply(derivedResults ->
                                           derivedResults.entrySet()
                                                         .stream()
                                                         .collect(Collectors.toMap(
                                                                 e -> derivedToOriginal.get(e.getKey()),
                                                                 Map.Entry::getValue)));
    }

    @Override
    public CompletableFuture<Void> deleteSecrets(String secretScope, List<String> keys) {
        List<String> derivedNames = keys.stream()
                                        .map(k -> secretNameDeriver.derive(secretScope, k))
                                        .toList();
        return secretStorageBackend.deleteSecrets(derivedNames);
    }
}
