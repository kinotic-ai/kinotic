package org.kinotic.core.internal.secret;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;

/**
 * Derives opaque, deterministic secret names using a two-level HKDF chain (HMAC-SHA256).
 * <p>
 * Level 1: scopeKey = HMAC(masterKey, secretScope)
 * Level 2: derivedName = HMAC(scopeKey, key)  → base64url (43 chars, no padding)
 * <p>
 * Scope keys are cached with Caffeine for performance.
 */
public class SecretNameDeriver {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] masterKey;
    private final Cache<String, byte[]> scopeKeyCache;

    /**
     * Creates a new deriver with the given master key.
     *
     * @param masterKey the HKDF master key (must be at least 16 bytes)
     * @throws IllegalArgumentException if the master key is null or too short
     */
    public SecretNameDeriver(byte[] masterKey) {
        if (masterKey == null || masterKey.length < 16) {
            throw new IllegalArgumentException("Master key must be at least 16 bytes");
        }
        this.masterKey = masterKey;
        this.scopeKeyCache = Caffeine.newBuilder()
                                     .maximumSize(1000)
                                     .expireAfterAccess(Duration.ofMinutes(30))
                                     .build();
    }

    /**
     * Derives an opaque, deterministic 43-character base64url name for the given scope and key.
     * <p>
     * The derivation is a two-level HMAC chain:
     * <ol>
     *   <li>scopeKey = HMAC-SHA256(masterKey, secretScope)</li>
     *   <li>derivedName = base64url(HMAC-SHA256(scopeKey, key))</li>
     * </ol>
     * Scope keys are cached so repeated calls within the same scope only compute level 2.
     *
     * @param secretScope the isolation scope (e.g. tenant ID)
     * @param key         the logical secret key within the scope
     * @return a 43-character base64url string (no padding) suitable as a storage key
     */
    public String derive(String secretScope, String key) {
        byte[] scopeKey = scopeKeyCache.get(secretScope, this::computeScopeKey);
        byte[] hash = hmac(scopeKey, key.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private byte[] computeScopeKey(String secretScope) {
        return hmac(masterKey, secretScope.getBytes(StandardCharsets.UTF_8));
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
