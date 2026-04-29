package org.kinotic.os.github.internal.client;

import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.secret.SecretStorageService;
import org.kinotic.os.github.api.config.KinoticGithubProperties;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the GitHub App's RSA private key and produces App-level JWTs (RS256) used to
 * call install-token-mint endpoints. The signed JWT is cached so the cost of RSA
 * signing is paid once per refresh interval per node, not per request.
 * <p>
 * GitHub's hard limit is {@code exp - iat <= 10 minutes}. We use the full window:
 * {@code iat} is backdated by 60s (per GitHub's recommendation, to absorb clock drift
 * between Kinotic and GitHub) and {@code exp} is set 9 minutes after {@code now},
 * for a 10-minute span. Forward lifetime from "now" is therefore 9 minutes — that's
 * the refresh interval, not a conservatism margin.
 * <p>
 * The key is loaded from {@code SecretStorageService} on first use. Both PKCS#1
 * ({@code BEGIN RSA PRIVATE KEY}) and PKCS#8 ({@code BEGIN PRIVATE KEY}) PEM formats
 * are accepted — GitHub's download button emits PKCS#1.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubAppJwtFactory {

    private static final String SECRET_SCOPE = "kinotic-system";
    private static final String SECRET_KEY = "githubAppPrivateKey";
    private static final long REFRESH_BEFORE_EXPIRY_SECONDS = 60;
    /** {@code iat} backdating, per GitHub's clock-drift guidance. Eats into the 10-minute span. */
    private static final long IAT_BACKDATE_SECONDS = 60;
    /** Forward lifetime from "now". {@code IAT_BACKDATE + JWT_TTL} must be <= 600s (GitHub's max). */
    private static final long JWT_TTL_SECONDS = 9 * 60;

    private final KinoticGithubProperties properties;
    private final SecretStorageService secretStorageService;

    private final AtomicReference<PrivateKey> cachedKey = new AtomicReference<>();
    private final AtomicReference<CachedJwt> cachedJwt = new AtomicReference<>();

    /**
     * Returns a currently-valid App JWT, refreshing it (and re-signing) if the cached
     * value is within {@value #REFRESH_BEFORE_EXPIRY_SECONDS} seconds of expiry.
     * <p>
     * Calls {@link java.security.Signature#sign} synchronously — callers that need to
     * stay off the event loop should run this inside {@code vertx.executeBlocking}.
     */
    public String getAppJwt() {
        CachedJwt current = cachedJwt.get();
        Instant now = Instant.now();
        if (current != null && current.expiresAt.minusSeconds(REFRESH_BEFORE_EXPIRY_SECONDS).isAfter(now)) {
            return current.token;
        }
        synchronized (this) {
            current = cachedJwt.get();
            if (current != null && current.expiresAt.minusSeconds(REFRESH_BEFORE_EXPIRY_SECONDS).isAfter(now)) {
                return current.token;
            }
            CachedJwt fresh = mint(now);
            cachedJwt.set(fresh);
            return fresh.token;
        }
    }

    private CachedJwt mint(Instant now) {
        PrivateKey key = ensureKey();
        Instant expiresAt = now.plus(JWT_TTL_SECONDS, ChronoUnit.SECONDS);
        String token = Jwts.builder()
                .issuer(properties.getGithub().getAppId())
                .issuedAt(Date.from(now.minusSeconds(IAT_BACKDATE_SECONDS)))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.RS256)
                .compact();
        return new CachedJwt(token, expiresAt);
    }

    private PrivateKey ensureKey() {
        PrivateKey existing = cachedKey.get();
        if (existing != null) {
            return existing;
        }
        String pem = secretStorageService.getSecret(SECRET_SCOPE, SECRET_KEY).join();
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException(
                    "GitHub App private key not loaded — verify "
                    + properties.getGithub().getSecretsPath() + "/" + SECRET_KEY
                    + " is mounted and GitHubAppSecretsBootstrap ran");
        }
        PrivateKey parsed = parsePem(pem);
        cachedKey.set(parsed);
        return parsed;
    }

    static PrivateKey parsePem(String pem) {
        String normalised = pem.replace("\r", "").trim();
        boolean pkcs1 = normalised.contains("BEGIN RSA PRIVATE KEY");
        String body = normalised
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] raw = Base64.getDecoder().decode(body);
        byte[] pkcs8 = pkcs1 ? wrapPkcs1AsPkcs8(raw) : raw;
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse GitHub App private key", e);
        }
    }

    /**
     * Wraps a PKCS#1 RSAPrivateKey octet string in the PKCS#8 PrivateKeyInfo envelope.
     * The fixed prefix is {@code SEQUENCE { INTEGER 0, AlgorithmIdentifier rsaEncryption,
     * OCTET STRING <pkcs1> }}; we encode the SEQUENCE and OCTET STRING lengths
     * dynamically and emit the rest verbatim.
     */
    private static byte[] wrapPkcs1AsPkcs8(byte[] pkcs1) {
        // AlgorithmIdentifier: SEQUENCE { OID 1.2.840.113549.1.1.1, NULL }
        byte[] algId = new byte[] {
                0x30, 0x0D,
                0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x01,
                0x05, 0x00
        };
        byte[] octetHeader = encodeLengthPrefixed((byte) 0x04, pkcs1.length);
        byte[] version = new byte[] { 0x02, 0x01, 0x00 };
        int contentLen = version.length + algId.length + octetHeader.length + pkcs1.length;
        byte[] outerHeader = encodeLengthPrefixed((byte) 0x30, contentLen);

        byte[] out = new byte[outerHeader.length + contentLen];
        int p = 0;
        System.arraycopy(outerHeader, 0, out, p, outerHeader.length); p += outerHeader.length;
        System.arraycopy(version, 0, out, p, version.length); p += version.length;
        System.arraycopy(algId, 0, out, p, algId.length); p += algId.length;
        System.arraycopy(octetHeader, 0, out, p, octetHeader.length); p += octetHeader.length;
        System.arraycopy(pkcs1, 0, out, p, pkcs1.length);
        return out;
    }

    private static byte[] encodeLengthPrefixed(byte tag, int length) {
        if (length < 0x80) {
            return new byte[] { tag, (byte) length };
        }
        if (length <= 0xFF) {
            return new byte[] { tag, (byte) 0x81, (byte) length };
        }
        if (length <= 0xFFFF) {
            return new byte[] { tag, (byte) 0x82, (byte) (length >>> 8), (byte) length };
        }
        return new byte[] { tag, (byte) 0x83,
                (byte) (length >>> 16), (byte) (length >>> 8), (byte) length };
    }

    private record CachedJwt(String token, Instant expiresAt) {}
}
