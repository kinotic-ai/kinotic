package org.kinotic.ee.internal.license;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies Kinotic Enterprise License keys. A key is a JWT signed with ES256 by Minds Ignited's EC P-256 license
 * signing key: its {@code sub} names the licensee, its {@code exp} is when the license expires, and its {@code features} claim
 * lists the {@link EnterpriseFeature} names it turns on.
 */
@Slf4j
public class LicenseVerifier {

    /** The property holding the server's license key. */
    public static final String LICENSE_KEY_PROPERTY = "kinotic.ee.licenseKey";

    // X.509 SubjectPublicKeyInfo of Minds Ignited's EC P-256 license signing key, Base64 without the PEM armor.
    // TODO before merge: replace this throwaway key, whose private key was discarded, with Minds Ignited's public key
    private static final String TRUSTED_PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEkYFsxwbhq11bFWK1REL7m2yyP4K/ukypr6iwbLufkrjG4cG+Fq6PnMQcVGV/XwoIU8zS9Im1sw5Q0MfvkQIqVA==";

    private static final String FEATURES_CLAIM = "features";

    private static final Map<String, Optional<License>> VERIFIED = new ConcurrentHashMap<>();

    private final PublicKey publicKey;

    public LicenseVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    private static LicenseVerifier trusted() {
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(Base64.getDecoder().decode(TRUSTED_PUBLIC_KEY));
            return new LicenseVerifier(KeyFactory.getInstance("EC").generatePublic(spec));
        } catch (Exception e) {
            throw new IllegalStateException("The trusted license signing key cannot be read", e);
        }
    }

    /**
     * The license the {@value #LICENSE_KEY_PROPERTY} property of {@code environment} holds, verified against the
     * trusted license signing key, or {@code null} when it holds no key or one that is not valid or has expired.
     */
    public static License licenseOf(Environment environment) {
        String key = StringUtils.trimToEmpty(environment.getProperty(LICENSE_KEY_PROPERTY));
        return VERIFIED.computeIfAbsent(key, LicenseVerifier::verifyAndLog).orElse(null);
    }

    // every Spring condition on a feature asks for the license, so each key is verified and logged once
    private static Optional<License> verifyAndLog(String key) {
        License ret = null;
        if (key.isEmpty()) {
            log.info("No Kinotic Enterprise License key is set in {}, so no Enterprise Feature is on", LICENSE_KEY_PROPERTY);
        } else {
            try {
                ret = trusted().verify(key);
                log.info("Kinotic Enterprise License for {}, expiring {}, turns on {}", ret.licensee(), ret.expiresAt(), ret.features());
            } catch (JwtException | IllegalArgumentException e) {
                log.error("The Kinotic Enterprise License key in {} is not valid, so no Enterprise Feature is on: {}",
                          LICENSE_KEY_PROPERTY, e.getMessage());
            }
        }
        return Optional.ofNullable(ret);
    }

    /**
     * The license {@code key} holds.
     *
     * @throws JwtException             when the key is not signed with the trusted key, is malformed, or has expired
     * @throws IllegalArgumentException when the key names no licensee or no expiry
     */
    public License verify(String key) {
        Claims claims = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(key).getPayload();
        if (StringUtils.isBlank(claims.getSubject()) || claims.getExpiration() == null) {
            throw new IllegalArgumentException("A license key names its licensee in sub and its expiry in exp");
        }
        return new License(claims.getSubject(), claims.getExpiration().toInstant(), features(claims));
    }

    // a key may name features a newer server knows; this server turns on the ones it knows
    private static Set<EnterpriseFeature> features(Claims claims) {
        List<?> named = claims.get(FEATURES_CLAIM, List.class);
        Set<EnterpriseFeature> ret = EnumSet.noneOf(EnterpriseFeature.class);
        if (named != null) {
            for (Object name : named) {
                for (EnterpriseFeature feature : EnterpriseFeature.values()) {
                    if (feature.name().equals(name)) {
                        ret.add(feature);
                    }
                }
            }
        }
        return ret;
    }
}
