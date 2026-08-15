package org.kinotic.domain.internal.api.services.secret;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.domain.api.services.SecretStorageService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DefaultSecretStorageService implements SecretStorageService {

    private final SecretNameDeriver secretNameDeriver;
    private final SecretStorageBackend secretStorageBackend;

    @Override
    public Future<Void> setSecret(String secretScope, String key, String value) {
        String derivedName = secretNameDeriver.derive(secretScope, key);
        return secretStorageBackend.setSecret(derivedName, value);
    }

    @Override
    public Future<String> getSecret(String secretScope, String key) {
        String derivedName = secretNameDeriver.derive(secretScope, key);
        return secretStorageBackend.getSecret(derivedName);
    }

    @Override
    public Future<Void> deleteSecret(String secretScope, String key) {
        String derivedName = secretNameDeriver.derive(secretScope, key);
        return secretStorageBackend.deleteSecret(derivedName);
    }

    @Override
    public Future<Void> setSecrets(String secretScope, Map<String, String> secrets) {
        Map<String, String> derived = secrets.entrySet()
                                             .stream()
                                             .collect(Collectors.toMap(
                                                     e -> secretNameDeriver.derive(secretScope, e.getKey()),
                                                     Map.Entry::getValue));
        return secretStorageBackend.setSecrets(derived);
    }

    @Override
    public Future<Map<String, String>> getSecrets(String secretScope, Set<String> keys) {
        Map<String, String> derivedToOriginal = keys.stream()
                                                    .collect(Collectors.toMap(
                                                            k -> secretNameDeriver.derive(secretScope, k),
                                                            k -> k));

        return secretStorageBackend.getSecrets(List.copyOf(derivedToOriginal.keySet()))
                                   .map(derivedResults ->
                                           derivedResults.entrySet()
                                                         .stream()
                                                         .collect(Collectors.toMap(
                                                                 e -> derivedToOriginal.get(e.getKey()),
                                                                 Map.Entry::getValue)));
    }

    @Override
    public Future<Void> deleteSecrets(String secretScope, Set<String> keys) {
        List<String> derivedNames = keys.stream()
                                        .map(k -> secretNameDeriver.derive(secretScope, k))
                                        .toList();
        return secretStorageBackend.deleteSecrets(derivedNames);
    }
}
