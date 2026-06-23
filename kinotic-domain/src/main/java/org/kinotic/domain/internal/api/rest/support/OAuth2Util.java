package org.kinotic.domain.internal.api.rest.support;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.auth.oauth2.providers.OpenIDConnectAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.iam.OidcProviderKind;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2Util {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static String firstPresent(Map<String, Object> claims, String... names) {
        for (String name : names) {
            String value = stringClaim(claims, name);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    /**
     * Verifies the JWT's {@code aud} claim against the configured expected audience.
     * Returns {@code true} when {@code expectedAudience} is null or blank — audience
     * checking is opt-in per config. When set, the claim must contain the expected
     * value; per the OIDC spec {@code aud} can be a single string or an array, so both
     * shapes are accepted.
     *
     * <p>Skipping null/blank deliberately: the config field is optional, and configs
     * that don't set it (e.g. providers where the audience is implicit in the clientId
     * already validated by Vert.x's discovery flow) shouldn't be forced to populate it.
     * SSO and other security-sensitive configs should set audience explicitly.
     *
     * @param claims           flattened JWT claims
     * @param expectedAudience the configured expected audience, or {@code null}/blank to skip
     * @return {@code true} when the check passes or is skipped, {@code false} on mismatch
     */
    public static boolean isAudienceValid(Map<String, Object> claims, String expectedAudience) {
        if (expectedAudience == null || expectedAudience.isBlank()) return true;
        if (claims == null) return false;
        Object aud = claims.get("aud");
        switch (aud) {
            case null -> {
                return false;
            }
            case String s -> {
                return expectedAudience.equals(s);
            }
            case Collection<?> col -> {
                for (Object v : col) {
                    if (v != null && expectedAudience.equals(v.toString())) return true;
                }
                return false;
            }
            case JsonArray arr -> {
                for (int i = 0; i < arr.size(); i++) {
                    Object v = arr.getValue(i);
                    if (v != null && expectedAudience.equals(v.toString())) return true;
                }
                return false;
            }
            default -> {
            }
        }
        return false;
    }

    /**
     * Provider-aware "is the email in this id_token verified?" check.
     *
     * <p>Most OIDC providers (Google, Keycloak, Auth0, Okta, generic) emit
     * {@code email_verified} as a boolean and we require it to be {@code true}.
     *
     * <p>Microsoft Entra ({@code azure-ad}) and Apple ({@code apple}) <b>do not emit</b>
     * {@code email_verified}. Their convention is "if the email claim is present at all,
     * the IdP verified it out-of-band before issuing the token" — Entra verifies via
     * tenant domain ownership / personal-account email confirmation; Apple via its
     * private-relay or signed-up email. We trust email-presence as verified for these
     * two and only these two; the JWT's signature has already been validated against
     * the IdP's JWKS, so we know the token is genuine.
     *
     * <p><b>Apple gotcha:</b> the {@code email} claim is only present on the user's
     * <i>first</i> sign-in — subsequent tokens omit it entirely. We rely on the
     * {@code (sub, configId)} key on {@link org.kinotic.domain.api.model.iam.IamUser} to
     * recognise returning users by their stable Apple {@code sub}, so the missing email
     * on later sign-ins doesn't matter for login. Signup, however, requires email and
     * will simply fail-closed here on a non-first Apple flow (which would only happen
     * if a user had previously revoked Sign in with Apple for the kinotic-platform app
     * and is signing up again). Apple emails may also be private-relay addresses
     * ({@code …@privaterelay.appleid.com}) — they are real, unique, and routable, so
     * we treat them as ordinary verified emails.
     *
     * <p>Returns {@code false} when the email is missing entirely (regardless of
     * provider) — a token without an email claim cannot drive an
     * {@link org.kinotic.domain.api.model.iam.IamUser} lookup or creation.
     */
    public static boolean isEmailVerified(Map<String, Object> claims, OidcProviderKind provider) {
        if (claims == null) return false;
        Object email = claims.get("email");
        if (!(email instanceof String emailStr) || emailStr.isBlank()) return false;

        Object explicit = claims.get("email_verified");
        if (explicit instanceof Boolean b) return b;
        if (explicit instanceof String s) return Boolean.parseBoolean(s);

        // No explicit claim — fall back to the per-provider trust convention.
        return provider == OidcProviderKind.AZURE_AD || provider == OidcProviderKind.APPLE;
    }

    /**
     * Verifies the JWT's {@code iss} claim against the configured {@code authority}.
     *
     * <p>For the common case (Google, Keycloak, single-tenant Okta, etc.) this is a
     * direct string match: the discovery doc returned the exact same issuer URL we
     * sent for discovery, and every token from that provider carries it. Tenant-specific
     * Entra configs (per-org SSO with authority
     * {@code https://login.microsoftonline.com/<tenant-id>/v2.0}) also fall through this
     * direct-match path — only the multi-tenant {@code /common} / {@code /organizations}
     * authorities need the substitution branch below.
     *
     * <p>For Microsoft multi-tenant ({@code /common/v2.0} or {@code /organizations/v2.0})
     * the discovery doc returns a literal {@code "{tenantid}"} placeholder and each
     * user's JWT has the placeholder substituted with their home tenant id. We pull that
     * id from the JWT's {@code tid} claim — safe because the signature has already been
     * verified against the JWKS that {@link OpenIDConnectAuth#discover} populated, so
     * any value in the token is signed by Microsoft's identity platform.
     *
     * @param claims    flattened JWT claims (typically from {@code user.principal()})
     * @param authority the configured OIDC authority (issuer URL we discovered against)
     * @return {@code true} if the iss claim is acceptable for this authority
     */
    public static boolean isIssuerValid(Map<String, Object> claims, String authority) {
        if (claims == null || authority == null) return false;
        Object iss = claims.get("iss");
        if (!(iss instanceof String issStr)) return false;

        // Direct match — tenant-specific Entra, Google, Keycloak, Okta, etc.
        if (issStr.equals(authority)) return true;

        // Microsoft multi-tenant: substitute the tid claim into the template form and compare.
        if (authority.startsWith("https://login.microsoftonline.com/")
                && (authority.endsWith("/common/v2.0") || authority.endsWith("/organizations/v2.0"))) {
            Object tid = claims.get("tid");
            if (!(tid instanceof String tidStr) || tidStr.isBlank()) return false;
            return issStr.equals("https://login.microsoftonline.com/" + tidStr + "/v2.0");
        }

        return false;
    }

    /** Generates a URL-safe base64 string from {@code byteLen} cryptographically random bytes. */
    public static String randomUrlSafe(int byteLen) {
        byte[] buf = new byte[byteLen];
        SECURE_RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /** Computes the PKCE S256 code-challenge for the given verifier. */
    public static String s256Challenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                                       .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : value.toString();
    }
}
