package org.kinotic.core.api.secret;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a secret value from a file mounted at a known path. Used by bootstrap classes
 * that promote operator-supplied secret material (OIDC client secrets, GitHub App
 * private key, webhook secret, etc.) into {@link SecretStorageService} on startup.
 * <p>
 * Returns {@code null} on missing file, empty contents, or read failure — the caller
 * is expected to treat this as "skip this entry, log a warning" rather than crashing
 * the boot. This matches the operator UX where a partially-configured deployment
 * should still come up with whatever providers are populated.
 */
public final class SecretFileLoader {

    private SecretFileLoader() {}

    /**
     * Reads and trims the contents of the given file.
     *
     * @param file       absolute path to the secret file
     * @param contextId  identifier used in log messages (e.g. configId, "githubAppPrivateKey")
     * @param log        caller's logger so warnings show up under the caller's category
     * @return the trimmed file contents, or {@code null} when the file is missing,
     *         empty, or unreadable
     */
    public static String read(Path file, String contextId, Logger log) {
        if (!Files.exists(file)) {
            log.warn("Secret file missing for {}: {} — skipping until populated", contextId, file);
            return null;
        }
        try {
            String value = Files.readString(file).trim();
            if (value.isEmpty()) {
                log.warn("Secret file empty for {}: {}", contextId, file);
                return null;
            }
            return value;
        } catch (IOException e) {
            log.error("Failed to read secret file for {}: {}", contextId, file, e);
            return null;
        }
    }
}
