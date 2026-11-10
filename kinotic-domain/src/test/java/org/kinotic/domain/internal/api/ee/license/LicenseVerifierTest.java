package org.kinotic.domain.internal.api.ee.license;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.license.EnterpriseFeature;
import org.kinotic.domain.api.model.license.License;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.GeneralSecurityException;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link LicenseVerifier#verify(String)}: a key signed with the trusted key yields its licensee, issue time,
 * expiry, grace period end and the features this server knows, a key in or past its grace period still yields its
 * license,
 * and a key signed with another key, before its nbf, or naming no licensee is refused.
 */
class LicenseVerifierTest {

    private final KeyPair trusted = p256();
    private final LicenseVerifier verifier = new LicenseVerifier(trusted.getPublic());

    @Test
    void aSignedKeyYieldsItsLicense() {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(365, ChronoUnit.DAYS);
        String key = Jwts.builder()
                         .subject("Acme")
                         .issuedAt(Date.from(issuedAt))
                         .expiration(Date.from(expiresAt))
                         .claim("features", List.of("ENTERPRISE_IDENTITY_PROVIDERS", "A_FEATURE_OF_A_NEWER_SERVER"))
                         .signWith(trusted.getPrivate(), Jwts.SIG.ES256)
                         .compact();
        License license = verifier.verify(key);
        assertEquals("Acme", license.getLicensee());
        assertEquals(issuedAt, license.getIssuedAt().toInstant());
        assertEquals(expiresAt, license.getExpiresAt().toInstant());
        assertEquals(expiresAt.plus(LicenseVerifier.GRACE_PERIOD), license.getGraceEndsAt().toInstant());
        assertEquals(Set.of(EnterpriseFeature.ENTERPRISE_IDENTITY_PROVIDERS), license.getFeatures());
    }

    @Test
    void theWildcardTurnsOnEveryFeature() {
        String key = Jwts.builder()
                         .subject("Kinotic Evaluation")
                         .expiration(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                         .claim("features", List.of(LicenseVerifier.ALL_FEATURES))
                         .signWith(trusted.getPrivate(), Jwts.SIG.ES256)
                         .compact();
        assertEquals(EnumSet.allOf(EnterpriseFeature.class), verifier.verify(key).getFeatures());
    }

    @Test
    void aKeySignedWithAnotherKeyIsRefused() {
        String key = Jwts.builder()
                         .subject("Acme")
                         .expiration(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                         .claim("features", List.of("AZURE_KEY_VAULT"))
                         .signWith(p256().getPrivate(), Jwts.SIG.ES256)
                         .compact();
        assertThrows(JwtException.class, () -> verifier.verify(key));
    }

    @Test
    void aKeyInItsGracePeriodIsAccepted() {
        License license = verifier.verify(keyExpiring(Instant.now().minus(13, ChronoUnit.DAYS)));
        assertTrue(license.getGraceEndsAt().toInstant().isAfter(Instant.now()));
    }

    @Test
    void aKeyPastItsGracePeriodYieldsAnExpiredLicense() {
        License license = verifier.verify(keyExpiring(Instant.now().minus(LicenseVerifier.GRACE_PERIOD).minus(1, ChronoUnit.HOURS)));
        assertTrue(license.getGraceEndsAt().toInstant().isBefore(Instant.now()));
    }

    @Test
    void aKeyNamingNoLicenseeOrExpiryIsRefused() {
        String noLicensee = Jwts.builder()
                                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                                .signWith(trusted.getPrivate(), Jwts.SIG.ES256)
                                .compact();
        String noExpiry = Jwts.builder().subject("Acme").signWith(trusted.getPrivate(), Jwts.SIG.ES256).compact();
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(noLicensee));
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(noExpiry));
    }

    @Test
    void aKeyBeforeItsNotBeforeIsRefused() {
        String key = Jwts.builder()
                         .subject("Acme")
                         .notBefore(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                         .expiration(Date.from(Instant.now().plus(30, ChronoUnit.DAYS)))
                         .signWith(trusted.getPrivate(), Jwts.SIG.ES256)
                         .compact();
        assertThrows(JwtException.class, () -> verifier.verify(key));
    }

    private String keyExpiring(Instant expiresAt) {
        return Jwts.builder()
                   .subject("Acme")
                   .expiration(Date.from(expiresAt))
                   .claim("features", List.of("AZURE_KEY_VAULT"))
                   .signWith(trusted.getPrivate(), Jwts.SIG.ES256)
                   .compact();
    }

    private static KeyPair p256() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
