package org.kinotic.core.internal.platform;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.config.PlatformSecretsProperties;
import org.kinotic.core.api.config.VersionedKeySet;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Loads platform-level {@link VersionedKeySet} JSON files from disk and periodically polls
 * them for changes. Used by the JWT issuer (signing keys) and the secret name deriver
 * (masterKeys). File updates surface as atomic reference swaps; consumers register
 * listeners to react to rotations.
 * <p>
 * Polling (rather than {@code WatchService}) avoids the symlink/atomic-replace quirks of
 * Kubernetes Secret volumes and Azure Key Vault CSI mounts. The poll interval is well below
 * the CSI driver's rotation interval, so rotations propagate within seconds.
 */
@Slf4j
@Component
public class PlatformSecretsService {

    private static final long POLL_INTERVAL_SECONDS = 30;

    private final PlatformSecretsProperties properties;
    private final ObjectMapper objectMapper;
    private final Optional<PlatformSecretsBootstrap> bootstrap;

    private final AtomicReference<Loaded> jwtSigningKeys = new AtomicReference<>();
    private final AtomicReference<Loaded> secretStorageMasterKeys = new AtomicReference<>();

    private final List<Consumer<VersionedKeySet>> jwtListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<VersionedKeySet>> masterKeyListeners = new CopyOnWriteArrayList<>();

    private ScheduledExecutorService scheduler;

    public PlatformSecretsService(KinoticProperties kinoticProperties,
                                  ObjectMapper objectMapper,
                                  Optional<PlatformSecretsBootstrap> bootstrap) {
        this.properties = kinoticProperties.getPlatformSecrets();
        this.objectMapper = objectMapper;
        this.bootstrap = bootstrap;
    }

    @PostConstruct
    public void start() {
        bootstrap.ifPresent(PlatformSecretsBootstrap::ensureFilesExist);

        if (properties.getJwtSigningKeysPath() != null) {
            jwtSigningKeys.set(loadFile(properties.getJwtSigningKeysPath()));
        }
        if (properties.getSecretStorageMasterKeysPath() != null) {
            secretStorageMasterKeys.set(loadFile(properties.getSecretStorageMasterKeysPath()));
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "platform-secrets-watcher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::poll,
                                         POLL_INTERVAL_SECONDS,
                                         POLL_INTERVAL_SECONDS,
                                         TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public VersionedKeySet getJwtSigningKeys() {
        Loaded loaded = jwtSigningKeys.get();
        return loaded == null ? null : loaded.content();
    }

    public VersionedKeySet getSecretStorageMasterKeys() {
        Loaded loaded = secretStorageMasterKeys.get();
        return loaded == null ? null : loaded.content();
    }

    public void addJwtSigningKeysListener(Consumer<VersionedKeySet> listener) {
        jwtListeners.add(listener);
    }

    public void addSecretStorageMasterKeysListener(Consumer<VersionedKeySet> listener) {
        masterKeyListeners.add(listener);
    }

    private void poll() {
        try {
            reloadIfChanged(jwtSigningKeys, jwtListeners);
            reloadIfChanged(secretStorageMasterKeys, masterKeyListeners);
        } catch (Exception e) {
            log.error("Platform secrets poll failed", e);
        }
    }

    private void reloadIfChanged(AtomicReference<Loaded> ref, List<Consumer<VersionedKeySet>> listeners) {
        Loaded current = ref.get();
        if (current == null) return;
        try {
            long mtime = Files.getLastModifiedTime(current.path()).toMillis();
            if (mtime == current.mtime()) return;
            Loaded updated = loadFile(current.path());
            ref.set(updated);
            log.info("Reloaded platform secret file: {}", current.path());
            for (Consumer<VersionedKeySet> listener : listeners) {
                try {
                    listener.accept(updated.content());
                } catch (Exception e) {
                    log.error("Platform secret listener threw", e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to reload {}", current.path(), e);
        }
    }

    private Loaded loadFile(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            VersionedKeySet set = objectMapper.readValue(bytes, VersionedKeySet.class);
            validate(set, path);
            long mtime = Files.getLastModifiedTime(path).toMillis();
            return new Loaded(path, mtime, set);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load platform secret file: " + path, e);
        }
    }

    private void validate(VersionedKeySet set, Path path) {
        if (set.getActiveKeyId() == null || set.getActiveKeyId().isBlank()) {
            throw new IllegalStateException("Platform secret " + path + " has no activeKeyId");
        }
        if (set.getKeys() == null || set.getKeys().isEmpty()) {
            throw new IllegalStateException("Platform secret " + path + " has no keys");
        }
        boolean hasActive = false;
        for (VersionedKeySet.KeyEntry entry : set.getKeys()) {
            if (entry.getId() == null || entry.getKey() == null) {
                throw new IllegalStateException("Platform secret " + path + " has key entry missing id or key");
            }
            if (entry.getId().equals(set.getActiveKeyId())) {
                hasActive = true;
            }
        }
        if (!hasActive) {
            throw new IllegalStateException(
                    "Platform secret " + path + " activeKeyId '" + set.getActiveKeyId() + "' not in keys");
        }
    }

    private record Loaded(Path path, long mtime, VersionedKeySet content) {}
}
