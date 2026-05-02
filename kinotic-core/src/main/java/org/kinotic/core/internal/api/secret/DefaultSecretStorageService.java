package org.kinotic.core.internal.api.secret;

import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.secret.SecretStorageService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Rotation-aware secret storage service.
 * <p>
 * Writes use the currently active master key via {@link SecretNameDeriver#deriveActive}.
 * Reads query all candidate derivations ({@link SecretNameDeriver#deriveAll}) so entries
 * written under a prior master key remain readable during a rotation window. Deletes remove
 * every candidate name to avoid orphans after rotation.
 */
@Component
@RequiredArgsConstructor
public class DefaultSecretStorageService implements SecretStorageService {

    private final SecretNameDeriver secretNameDeriver;
    private final SecretStorageBackend secretStorageBackend;

    @Override
    public CompletableFuture<Void> setSecret(String secretScope, String key, String value) {
        return secretStorageBackend.setSecret(secretNameDeriver.deriveActive(secretScope, key), value);
    }

    @Override
    public CompletableFuture<String> getSecret(String secretScope, String key) {
        return tryReadCandidates(secretNameDeriver.deriveAll(secretScope, key), 0);
    }

    @Override
    public CompletableFuture<Void> deleteSecret(String secretScope, String key) {
        return secretStorageBackend.deleteSecrets(secretNameDeriver.deriveAll(secretScope, key));
    }

    @Override
    public CompletableFuture<Void> setSecrets(String secretScope, Map<String, String> secrets) {
        Map<String, String> derived = new HashMap<>(secrets.size());
        for (Map.Entry<String, String> e : secrets.entrySet()) {
            derived.put(secretNameDeriver.deriveActive(secretScope, e.getKey()), e.getValue());
        }
        return secretStorageBackend.setSecrets(derived);
    }

    @Override
    public CompletableFuture<Map<String, String>> getSecrets(String secretScope, List<String> keys) {
        Map<String, List<String>> logicalToCandidates = new HashMap<>(keys.size());
        Set<String> allCandidates = new HashSet<>();
        for (String k : keys) {
            List<String> cands = secretNameDeriver.deriveAll(secretScope, k);
            logicalToCandidates.put(k, cands);
            allCandidates.addAll(cands);
        }
        return secretStorageBackend.getSecrets(List.copyOf(allCandidates))
                                   .thenApply(backendResults -> {
            Map<String, String> out = new HashMap<>(keys.size());
            for (Map.Entry<String, List<String>> e : logicalToCandidates.entrySet()) {
                for (String candidate : e.getValue()) {
                    String value = backendResults.get(candidate);
                    if (value != null) {
                        out.put(e.getKey(), value);
                        break;
                    }
                }
            }
            return out;
        });
    }

    @Override
    public CompletableFuture<Void> deleteSecrets(String secretScope, List<String> keys) {
        List<String> derivedNames = new ArrayList<>();
        for (String k : keys) {
            derivedNames.addAll(secretNameDeriver.deriveAll(secretScope, k));
        }
        return secretStorageBackend.deleteSecrets(derivedNames);
    }

    private CompletableFuture<String> tryReadCandidates(List<String> candidates, int idx) {
        if (idx >= candidates.size()) {
            return CompletableFuture.completedFuture(null);
        }
        return secretStorageBackend.getSecret(candidates.get(idx))
                                   .handle((value, ex) -> {
            if (ex == null && value != null) {
                return CompletableFuture.completedFuture(value);
            }
            return tryReadCandidates(candidates, idx + 1);
        }).thenCompose(f -> f);
    }
}
