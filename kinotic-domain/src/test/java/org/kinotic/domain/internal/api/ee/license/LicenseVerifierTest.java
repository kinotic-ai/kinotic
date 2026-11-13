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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link LicenseVerifier#verify(String)}: a key signed with the trusted key yields its licensee, issue time,
 * expiry, grace period end and the features this server knows, a key in or past its grace period still yields its
 * license, a key bound to the verifier's cluster or to none is accepted, and a key signed with another key, before its
 * nbf, naming no licensee, or bound to another cluster is refused.
 */
class LicenseVerifierTest {

    private static final String CLUSTER_ID = "6f1c2b9e-3d4a-4c5b-8e7f-0a1b2c3d4e5f";

    private final KeyPair trusted = p256();
    private final LicenseVerifier verifier = new LicenseVerifier(trusted.getPublic(), CLUSTER_ID);

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

    @Test
    void aKeyBoundToTheClusterYieldsItsCluster() {
        assertEquals(CLUSTER_ID, verifier.verify(keyBoundTo(CLUSTER_ID)).getClusterId());
        assertNull(verifier.verify(keyExpiring(Instant.now().plus(1, ChronoUnit.DAYS))).getClusterId());
    }

    @Test
    void aKeyBoundToAnotherClusterIsRefused() {
        String key = keyBoundTo("0b9a8c7d-6e5f-4a3b-2c1d-0e9f8a7b6c5d");
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(key));
        // a cluster outside Kubernetes has no id, so it runs only on a key bound to no cluster
        assertThrows(IllegalArgumentException.class, () -> new LicenseVerifier(trusted.getPublic(), null).verify(keyBoundTo(CLUSTER_ID)));
    }

    private String keyBoundTo(String clusterId) {
        return Jwts.builder()
                   .subject("Acme")
                   .expiration(Date.from(Instant.now().plus(30, ChronoUnit.DAYS)))
                   .claim("clusterId", clusterId)
                   .claim("features", List.of(LicenseVerifier.ALL_FEATURES))
                   .signWith(trusted.getPrivate(), Jwts.SIG.ES256)
                   .compact();
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
