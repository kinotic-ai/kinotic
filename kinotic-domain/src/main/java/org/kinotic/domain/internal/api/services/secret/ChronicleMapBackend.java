package org.kinotic.domain.internal.api.services.secret;

import io.vertx.core.Future;
import net.openhft.chronicle.map.ChronicleMap;
import org.kinotic.domain.api.config.ChronicleMapProperties;
import org.springframework.beans.factory.DisposableBean;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public Future<Void> setSecret(String derivedName, String value) {
        map.put(derivedName, value);
        return Future.succeededFuture();
    }

    @Override
    public Future<String> getSecret(String derivedName) {
        return Future.succeededFuture(map.get(derivedName));
    }

    @Override
    public Future<Void> deleteSecret(String derivedName) {
        map.remove(derivedName);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> setSecrets(Map<String, String> derivedNameToValue) {
        map.putAll(derivedNameToValue);
        return Future.succeededFuture();
    }

    @Override
    public Future<Map<String, String>> getSecrets(List<String> derivedNames) {
        Map<String, String> results = new HashMap<>();
        for (String name : derivedNames) {
            String value = map.get(name);
            if (value != null) {
                results.put(name, value);
            }
        }
        return Future.succeededFuture(results);
    }

    @Override
    public Future<Void> deleteSecrets(List<String> derivedNames) {
        derivedNames.forEach(map::remove);
        return Future.succeededFuture();
    }

    @Override
    public void destroy() {
        map.close();
    }
}
