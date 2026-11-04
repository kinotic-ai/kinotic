package org.kinotic.ee.internal.license;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.GeneralSecurityException;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link LicenseVerifier#verify(String)}: a key signed with the trusted key yields its licensee, expiry and the
 * features this server knows, and a key signed with another key, expired, or naming no licensee is refused; and
 * {@link LicenseVerifier#licenseOf} gives a server with no valid key no license.
 */
class LicenseVerifierTest {

    private final KeyPair trusted = p256();
    private final LicenseVerifier verifier = new LicenseVerifier(trusted.getPublic());

    @Test
    void aSignedKeyYieldsItsLicense() {
        Instant expiresAt = Instant.now().plus(365, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String key = Jwts.builder()
                         .subject("Acme")
                         .expiration(Date.from(expiresAt))
                         .claim("features", List.of("ENTERPRISE_IDENTITY_PROVIDERS", "A_FEATURE_OF_A_NEWER_SERVER"))
                         .signWith(trusted.getPrivate(), Jwts.SIG.ES256)
                         .compact();
        License license = verifier.verify(key);
        assertEquals("Acme", license.licensee());
        assertEquals(expiresAt, license.expiresAt());
        assertEquals(Set.of(EnterpriseFeature.ENTERPRISE_IDENTITY_PROVIDERS), license.features());
        assertTrue(license.allows(EnterpriseFeature.ENTERPRISE_IDENTITY_PROVIDERS));
        assertFalse(license.allows(EnterpriseFeature.AZURE_KEY_VAULT));
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
    void anExpiredKeyIsRefused() {
        String key = Jwts.builder()
                         .subject("Acme")
                         .expiration(Date.from(Instant.now().minus(1, ChronoUnit.DAYS)))
                         .claim("features", List.of("AZURE_KEY_VAULT"))
                         .signWith(trusted.getPrivate(), Jwts.SIG.ES256)
                         .compact();
        assertThrows(JwtException.class, () -> verifier.verify(key));
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
    void aServerWithoutAValidKeyHasNoLicense() {
        assertNull(LicenseVerifier.licenseOf(environment("")));
        assertNull(LicenseVerifier.licenseOf(environment("not-a-license-key")));
    }

    private static StandardEnvironment environment(String licenseKey) {
        StandardEnvironment ret = new StandardEnvironment();
        ret.getPropertySources().addFirst(new MapPropertySource("test", Map.of(LicenseVerifier.LICENSE_KEY_PROPERTY, licenseKey)));
        return ret;
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
