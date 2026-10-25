package org.kinotic.domain.internal.api.services.secret;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.config.SystemSecretsProperties;
import org.kinotic.domain.api.config.VersionedKey;
import org.kinotic.domain.api.config.VersionedKeySet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Seeds the system secret files that are missing on first boot, so the standard file-watch code
 * path in {@link SystemSecretsService} works out of the box without a separate setup step.
 * Generated keys are fresh per developer machine and never committed.
 * <p>
 * Only registered when {@code kinotic.domain.systemSecrets.generateMissing} is true; in production the
 * system secrets must come from Azure Key Vault via the Secrets Store CSI driver.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "kinotic.domain.systemSecrets.generateMissing", havingValue = "true")
@RequiredArgsConstructor
public class DevSystemSecretsGenerator {

    private static final int KEY_BYTES = 32;
    private static final String INITIAL_VERSION = "v1";

    private final KinoticDomainProperties kinoticProperties;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    /**
     * Generates each configured secret file that does not exist yet, leaving existing files untouched.
     */
    public void ensureFilesExist() {
        SystemSecretsProperties props = kinoticProperties.getDomain().getSystemSecrets();
        ensureFile(props.getJwtSigningKeysPath(), "JWT signing keys");
    }

    private void ensureFile(Path path, String label) {
        if (path == null) return;
        if (Files.exists(path)) {
            log.info("Dev {} file already present at {}, leaving untouched", label, path);
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            // Written whole under a temporary name, then linked into place: a server reading the file never sees
            // it half written, and of servers starting together only the first to link publishes its keys
            Path tmp = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
            try {
                try {
                    Files.setPosixFilePermissions(tmp, java.util.Set.of(
                            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
                } catch (UnsupportedOperationException ignored) {
                    // non-POSIX FS (Windows); rely on directory ACLs
                }
                objectMapper.writeValue(tmp.toFile(), newKeySet());
                Files.createLink(path, tmp);
                log.warn("Generated dev {} at {} — DO NOT USE IN PRODUCTION", label, path);
            } catch (FileAlreadyExistsException e) {
                log.info("Dev {} file at {} was created by another server first, leaving it untouched", label, path);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to seed dev system secret file: " + path, e);
        }
    }

    private VersionedKeySet newKeySet() {
        byte[] raw = new byte[KEY_BYTES];
        random.nextBytes(raw);
        String encoded = Base64.getEncoder().encodeToString(raw);
        return new VersionedKeySet()
                .setActiveKeyId(INITIAL_VERSION)
                .setKeys(List.of(new VersionedKey(INITIAL_VERSION, encoded)));
    }
}
