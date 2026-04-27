package org.kinotic.core.internal.secret;

import net.openhft.chronicle.map.ChronicleMap;
import org.kinotic.core.api.config.ChronicleMapProperties;
import org.springframework.beans.factory.DisposableBean;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Chronicle Map backed secret storage for local development.
 * Persists secrets to a memory-mapped file.
 */
public class ChronicleMapBackend implements SecretStorageBackend, DisposableBean {

    private final ChronicleMap<String, String> map;

    public ChronicleMapBackend(ChronicleMapProperties settings) throws IOException {
        String filePath = System.getProperty("java.io.tmpdir") + "/kinotic-secrets.dat";
        int maxEntries = 10000;
        if (settings != null) {
            if (settings.getFilePath() != null) {
                filePath = settings.getFilePath();
            }
            maxEntries = settings.getMaxEntries();
        }
        this.map = buildMap(filePath, maxEntries);
    }

    private static ChronicleMap<String, String> buildMap(String filePath, int maxEntries) throws IOException {
        return ChronicleMap.of(String.class, String.class)
                           .name("kinotic-secrets")
                           .averageKeySize(43)    // base64url HMAC-SHA256 output
                           .averageValueSize(256)
                           .entries(maxEntries)
                           .createPersistedTo(new File(filePath));
    }

    @Override
    public CompletableFuture<Void> setSecret(String derivedName, String value) {
        map.put(derivedName, value);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<String> getSecret(String derivedName) {
        return CompletableFuture.completedFuture(map.get(derivedName));
    }

    @Override
    public CompletableFuture<Void> deleteSecret(String derivedName) {
        map.remove(derivedName);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> setSecrets(Map<String, String> derivedNameToValue) {
        map.putAll(derivedNameToValue);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, String>> getSecrets(List<String> derivedNames) {
        Map<String, String> results = new HashMap<>();
        for (String name : derivedNames) {
            String value = map.get(name);
            if (value != null) {
                results.put(name, value);
            }
        }
        return CompletableFuture.completedFuture(results);
    }

    @Override
    public CompletableFuture<Void> deleteSecrets(List<String> derivedNames) {
        derivedNames.forEach(map::remove);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void destroy() {
        map.close();
    }
}
