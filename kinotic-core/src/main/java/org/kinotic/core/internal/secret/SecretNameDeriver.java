package org.kinotic.core.internal.secret;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.config.SecretStorageSettings;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Derives opaque, deterministic secret names using a two-level HKDF chain (HMAC-SHA256).
 * <p>
 * Level 1: scopeKey = HMAC(masterKey, secretScope)
 * Level 2: derivedName = HMAC(scopeKey, key)  → base64url (43 chars, no padding)
 * <p>
 */
@Slf4j
@Component
public class SecretNameDeriver {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] masterKey;

    public SecretNameDeriver(KinoticProperties properties) {
        SecretStorageSettings settings = properties.getSecretStorage();
        if (settings != null && settings.getMasterKey() != null) {
            this.masterKey = Base64.getDecoder().decode(settings.getMasterKey());
        } else {
            throw new IllegalStateException("No secret storage master key configured");
        }
    }

    /**
     * Derives an opaque, deterministic 43-character base64url name for the given scope and key.
     * <p>
     * The derivation is a two-level HMAC chain:
     * <ol>
     *   <li>scopeKey = HMAC-SHA256(masterKey, secretScope)</li>
     *   <li>derivedName = base64url(HMAC-SHA256(scopeKey, key))</li>
     * </ol>
     * Scope keys are cached, so repeated calls within the same scope only compute level 2.
     *
     * @param secretScope the isolation scope (e.g. tenant ID)
     * @param key         the logical secret key within the scope
     * @return a 43-character base64url string (no padding) suitable as a storage key
     */
    public String derive(String secretScope, String key) {
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
