package org.kinotic.server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.config.PlatformSecretsProperties;
import org.kinotic.core.api.config.VersionedKeySet;
import org.kinotic.core.internal.platform.PlatformSecretsBootstrap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Seeds platform secret files on first boot under the {@code development} profile, so the
 * standard file-watch code path in {@link org.kinotic.core.internal.platform.PlatformSecretsService}
 * works out of the box without requiring a separate setup step. Generated keys are fresh
 * per developer machine and never committed.
 * <p>
 * Only registered when the {@code development} profile is active; in production the
 * platform secrets must come from Azure Key Vault via the Secrets Store CSI driver.
 */
@Slf4j
@Component
@Profile("development")
@RequiredArgsConstructor
public class DevPlatformSecretsGenerator implements PlatformSecretsBootstrap {

    private static final int KEY_BYTES = 32;
    private static final String INITIAL_VERSION = "v1";

    private final KinoticProperties kinoticProperties;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    @Override
    public void ensureFilesExist() {
        PlatformSecretsProperties props = kinoticProperties.getPlatformSecrets();
        ensureFile(props.getJwtSigningKeysPath(), "JWT signing keys");
        ensureFile(props.getSecretStorageMasterKeysPath(), "secret-storage master keys");
    }

    private void ensureFile(Path path, String label) {
        if (path == null) return;
        if (Files.exists(path)) {
            log.info("Dev {} file already present at {}, leaving untouched", label, path);
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            VersionedKeySet set = newKeySet();
            objectMapper.writeValue(path.toFile(), set);
            try {
                Files.setPosixFilePermissions(path, java.util.Set.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                // non-POSIX FS (Windows); rely on directory ACLs
            }
            log.warn("Generated dev {} at {} — DO NOT USE IN PRODUCTION", label, path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to seed dev platform secret file: " + path, e);
        }
    }

    private VersionedKeySet newKeySet() {
        byte[] raw = new byte[KEY_BYTES];
        random.nextBytes(raw);
        String encoded = Base64.getEncoder().encodeToString(raw);
        return new VersionedKeySet()
                .setActiveKeyId(INITIAL_VERSION)
                .setKeys(List.of(new VersionedKeySet.KeyEntry(INITIAL_VERSION, encoded)));
    }
}
