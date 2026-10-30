package org.kinotic.domain.internal.api.services.secret;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.config.VersionedKey;
import org.kinotic.domain.api.config.VersionedKeySet;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JWT signing key set a server loads is refused unless every key holds HS256 key material, so a server never
 * starts minting tokens any shorter key could sign.
 */
class SystemSecretsServiceTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @TempDir
    Path directory;

    private SystemSecretsService service;

    @AfterEach
    void stop() {
        if (service != null) {
            service.stop();
        }
    }

    @Test
    void aKeySetOfThirtyTwoByteKeysLoads() throws Exception {
        start(keySet(new VersionedKey("v1", key(32))));
        assertEquals("v1", service.getJwtSigningKeys().getActiveKeyId());
    }

    @Test
    void aKeyShorterThanThirtyTwoBytesFailsStartupNamingTheKey() throws Exception {
        Path file = write(keySet(new VersionedKey("v1", key(32)), new VersionedKey("v0", key(16))));
        IllegalStateException refusal = assertThrows(IllegalStateException.class, () -> start(file));
        assertTrue(refusal.getMessage().contains("'v0'"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("16 bytes"), refusal.getMessage());
    }

    @Test
    void aKeyThatIsNotBase64FailsStartupNamingTheKey() throws Exception {
        Path file = write(keySet(new VersionedKey("v1", "not base64!")));
        IllegalStateException refusal = assertThrows(IllegalStateException.class, () -> start(file));
        assertTrue(refusal.getMessage().contains("'v1'"), refusal.getMessage());
    }

    private void start(VersionedKeySet set) throws Exception {
        start(write(set));
    }

    private void start(Path file) {
        KinoticDomainProperties properties = new KinoticDomainProperties();
        properties.getDomain().getSystemSecrets().setJwtSigningKeysPath(file);
        service = new SystemSecretsService(properties, jsonMapper, Optional.empty());
        service.start();
    }

    private Path write(VersionedKeySet set) throws Exception {
        Path file = directory.resolve("jwt-signing-keys");
        Files.write(file, jsonMapper.writeValueAsBytes(set));
        return file;
    }

    private static VersionedKeySet keySet(VersionedKey... keys) {
        return new VersionedKeySet().setActiveKeyId(keys[0].getId()).setKeys(List.of(keys));
    }

    private static String key(int bytes) {
        return Base64.getEncoder().encodeToString(new byte[bytes]);
    }
}
