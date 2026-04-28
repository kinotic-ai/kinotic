package org.kinotic.os.github.internal.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Verifies GitHub webhook signatures. GitHub HMAC-SHA256-signs every webhook body
 * with the App's webhook secret and presents the result in the
 * {@code X-Hub-Signature-256} header as {@code sha256=<hex>}. Verification is
 * constant-time to avoid leaking the expected digest through timing.
 */
public final class GitHubWebhookVerifier {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private GitHubWebhookVerifier() {}

    /**
     * @param rawBody         the raw request body bytes — must be the exact bytes
     *                        GitHub sent, before any JSON re-encoding
     * @param secret          the webhook secret configured for the App
     * @param headerSignature value of {@code X-Hub-Signature-256}, expected in the
     *                        form {@code sha256=<hex>}; null/blank returns false
     * @return true iff the signature matches
     */
    public static boolean verify(byte[] rawBody, String secret, String headerSignature) {
        if (rawBody == null || secret == null || secret.isEmpty() || headerSignature == null) {
            return false;
        }
        if (!headerSignature.startsWith("sha256=")) {
            return false;
        }
        String expectedHex;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            expectedHex = toHex(mac.doFinal(rawBody));
        } catch (Exception e) {
            return false;
        }
        String received = headerSignature.substring("sha256=".length());
        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.US_ASCII),
                received.getBytes(StandardCharsets.US_ASCII));
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
