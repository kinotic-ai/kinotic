package org.kinotic.core.internal.secret;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.config.VersionedKeySet;
import org.kinotic.core.internal.platform.PlatformSecretsService;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Derives opaque, deterministic secret names using a two-level HKDF chain (HMAC-SHA256).
 * <p>
 * Level 1: scopeKey = HMAC(masterKey, secretScope)
 * Level 2: derivedName = HMAC(scopeKey, key)  → base64url (43 chars, no padding)
 * <p>
 * Master keys are sourced from {@link PlatformSecretsService} (platform secrets mount) and
 * swap atomically on rotation. {@link #deriveActive} uses the current active key;
 * {@link #deriveAll} returns one name per key so rotation-aware reads can try each in turn.
 */
@Slf4j
@Component
public class SecretNameDeriver {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AtomicReference<VersionedKeySet> masterKeys = new AtomicReference<>();

    public SecretNameDeriver(PlatformSecretsService platformSecretsService) {
        VersionedKeySet initial = platformSecretsService.getSecretStorageMasterKeys();
        if (initial == null) {
            throw new IllegalStateException(
                    "Secret-storage master keys are not mounted. Configure " +
                    "kinotic.platformSecrets.secretStorageMasterKeysPath to point at a " +
                    "VersionedKeySet JSON file.");
        }
        masterKeys.set(initial);
        platformSecretsService.addSecretStorageMasterKeysListener(updated -> {
            log.info("Rotating secret-storage master keys; active={}", updated.getActiveKeyId());
            masterKeys.set(updated);
        });
        log.info("Secret name deriver initialized with master keys (activeKeyId={})",
                 initial.getActiveKeyId());
    }

    /**
     * Derives the opaque storage name for the given scope and key using the currently active
     * master key. Used for writes.
     */
    public String deriveActive(String secretScope, String key) {
        VersionedKeySet set = masterKeys.get();
        return deriveWith(decode(set.getActive().getKey()), secretScope, key);
    }

    /**
     * Derives all valid storage names for the given scope and key, ordered with the active
     * master key's derivation first and older keys after. Callers doing reads should try
     * each in order so secrets written under prior master keys remain reachable during a
     * rotation window.
     */
    public List<String> deriveAll(String secretScope, String key) {
        VersionedKeySet set = masterKeys.get();
        List<String> result = new ArrayList<>(set.getKeys().size());
        String activeId = set.getActiveKeyId();
        result.add(deriveWith(decode(set.findById(activeId).getKey()), secretScope, key));
        for (VersionedKeySet.KeyEntry entry : set.getKeys()) {
            if (!entry.getId().equals(activeId)) {
                result.add(deriveWith(decode(entry.getKey()), secretScope, key));
            }
        }
        return result;
    }

    private static byte[] decode(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    private static String deriveWith(byte[] masterKey, String secretScope, String key) {
        byte[] scopeKey = hmac(masterKey, secretScope.getBytes(StandardCharsets.UTF_8));
        byte[] hash = hmac(scopeKey, key.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
