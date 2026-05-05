package org.kinotic.gateway.internal.auth;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.providers.OpenIDConnectAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.api.model.iam.BaseOidcConfiguration;
import org.kinotic.os.api.model.iam.OidcProviderKind;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Builds a Vert.x {@link OAuth2Auth} for a given OIDC configuration by running OIDC
 * discovery against the configured {@code authority}. Accepts any {@link BaseOidcConfiguration}
 * subclass — only baseline fields ({@code authority}, {@code clientId}, {@code id}) are read.
 * Works uniformly for every standards-compliant OIDC provider (Google, Azure AD, Keycloak,
 * Salesforce, Cognito, Auth0, Okta, Ping, Duende, custom).
 * <p>
 * Non-OIDC-discovery providers (GitHub, Apple, Facebook, LinkedIn, Microsoft-Live) use
 * hardcoded endpoints and non-standard client-auth flows. They will need dedicated handling
 * when first wired in — add it here as an explicit branch keyed off
 * {@link org.kinotic.os.api.model.iam.OidcProviderKind} at that point.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthFactory {

    private final Vertx vertx;

    /**
     * @param config       the persisted OIDC configuration (must have authority set)
     * @param clientSecret resolved client secret, or null for public-client flows
     */
    public Future<OAuth2Auth> create(BaseOidcConfiguration config, String clientSecret) {
        if (config.getAuthority() == null || config.getAuthority().isBlank()) {
            return Future.failedFuture(new IllegalArgumentException(
                    "OidcConfiguration " + config.getId() + " has no authority; required for OIDC discovery"));
        }

        OAuth2Options options = new OAuth2Options()
                .setClientId(config.getClientId())
                .setSite(config.getAuthority())
                // Microsoft /common returns a discovery doc whose `issuer` is the literal template
                // "https://login.microsoftonline.com/{tenantid}/v2.0" — Vert.x's strict
                // {site == issuer} comparison fails. JWKS-backed signature verification remains
                // the real security check.
                .setValidateIssuer(false);
        if (clientSecret != null) {
            options.setClientSecret(clientSecret);
        }

        return OpenIDConnectAuth.discover(vertx, options)
                                .map(oauth -> {
                                    // Discovery still mutates JWTOptions.issuer with the same
                                    // {tenantid}-templated string — Vert.x would then fail every
                                    // per-JWT validation because the real JWT carries the
                                    // substituted tid. Clear it for the multi-tenant case; we
                                    // re-validate the issuer ourselves via {@link #isIssuerValid}
                                    // post-exchange using the JWT's tid claim (which is signed
                                    // by the JWKS Vert.x already verified against).
                                    if (options.getJWTOptions() != null) {
                                        String jwtIssuer = options.getJWTOptions().getIssuer();
                                        if (jwtIssuer != null && jwtIssuer.contains("{tenantid}")) {
                                            options.getJWTOptions().setIssuer(null);
                                        }
                                    }
                                    return oauth;
                                });
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
     * {@code (sub, configId)} key on {@link org.kinotic.os.api.model.iam.IamUser} to
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
     * {@link org.kinotic.os.api.model.iam.IamUser} lookup or creation.
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
}
