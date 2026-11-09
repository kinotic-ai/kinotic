package org.kinotic.domain.internal.api.ee.license;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.apache.commons.lang3.StringUtils;
import org.kinotic.domain.api.model.license.EnterpriseFeature;
import org.kinotic.domain.api.model.license.License;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Verifies Kinotic Enterprise License keys. A key is a JWT signed with ES256 by Minds Ignited's EC P-256 license
 * signing key: its {@code sub} names the licensee, its {@code iat} is when it was issued, its {@code exp} is when the
 * license expires, and its {@code features} claim lists the {@link EnterpriseFeature} names it turns on, or
 * {@value #ALL_FEATURES} for every feature the server knows.
 */
public class LicenseVerifier {

    // X.509 SubjectPublicKeyInfo of Minds Ignited's EC P-256 license signing key (the kinotic-license-signing key in
    // the platform Key Vault, deployment/terraform/azure/global/license.tf), Base64 without the PEM armor
    private static final String TRUSTED_PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEE/z6wLmdRF5jILGpPRiy97WHd5XsuLxhZHyKz0t5GTMUPtSNSYiIuPBVFrr4U8BQz86QtihaP+BU+JlPkKaX5g==";

    private static final String FEATURES_CLAIM = "features";

    /** The {@code features} entry that turns on every Enterprise Feature. */
    public static final String ALL_FEATURES = "*";

    /** How long after its expiry a license keeps its features on. */
    public static final Duration GRACE_PERIOD = Duration.ofDays(14);

    private final PublicKey publicKey;

    public LicenseVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    /** A verifier that trusts Minds Ignited's license signing key. */
    public static LicenseVerifier trusted() {
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(Base64.getDecoder().decode(TRUSTED_PUBLIC_KEY));
            return new LicenseVerifier(KeyFactory.getInstance("EC").generatePublic(spec));
        } catch (Exception e) {
            throw new IllegalStateException("The trusted license signing key cannot be read", e);
        }
    }

    /**
     * The license {@code key} holds, whether or not it has expired, with its grace period ending {@link #GRACE_PERIOD}
     * after its expiry. Its {@link License#getStatus() status} is left unset.
     *
     * @throws JwtException             when the key is not signed with the trusted key, is malformed, or is not valid
     *                                  yet by its {@code nbf}
     * @throws IllegalArgumentException when the key names no licensee or no expiry
     */
    public License verify(String key) {
        Claims claims;
        try {
            claims = Jwts.parser()
                         .verifyWith(publicKey)
                         .build()
                         .parseSignedClaims(key)
                         .getPayload();
        } catch (ExpiredJwtException e) {
            // jjwt checks exp only after it has verified the signature, so these claims are signed by the trusted key
            claims = e.getClaims();
        }
        if (StringUtils.isBlank(claims.getSubject()) || claims.getExpiration() == null) {
            throw new IllegalArgumentException("A license key names its licensee in sub and its expiry in exp");
        }
        Date expiresAt = claims.getExpiration();
        return new License().setLicensee(claims.getSubject())
                            .setIssuedAt(claims.getIssuedAt())
                            .setExpiresAt(expiresAt)
                            .setGraceEndsAt(Date.from(expiresAt.toInstant().plus(GRACE_PERIOD)))
                            .setFeatures(features(claims));
    }

    // a key may name features a newer server knows; this server turns on the ones it knows
    private static Set<EnterpriseFeature> features(Claims claims) {
        List<?> named = claims.get(FEATURES_CLAIM, List.class);
        Set<EnterpriseFeature> ret;
        if (named != null && named.contains(ALL_FEATURES)) {
            ret = EnumSet.allOf(EnterpriseFeature.class);
        } else {
            ret = EnumSet.noneOf(EnterpriseFeature.class);
            if (named != null) {
                for (Object name : named) {
                    for (EnterpriseFeature feature : EnterpriseFeature.values()) {
                        if (feature.name().equals(name)) {
                            ret.add(feature);
                        }
                    }
                }
            }
        }
        return ret;
    }
}
