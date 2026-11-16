package org.kinotic.common.internal.api.oidc;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2AuthorizationURL;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.Oauth2Credentials;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.kinotic.common.api.oidc.InvalidIdTokenException;
import org.kinotic.common.api.oidc.OAuth2Util;
import org.kinotic.common.api.oidc.OidcAuthorization;
import org.kinotic.common.api.oidc.OidcClient;
import org.kinotic.common.api.oidc.OidcClientSettings;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class DefaultOidcClient implements OidcClient {

    private static final SecureRandom RANDOM = new SecureRandom();
    // how far this server's clock may run ahead of the provider's before an id token counts as expired
    private static final long ID_TOKEN_CLOCK_SKEW_SECONDS = 60;

    private final OAuth2Auth oauth2;
    private final WebClient webClient;
    private final OidcClientSettings settings;

    @Override
    public OidcAuthorization authorize(String callbackUrl, List<String> scopes) {
        String state = token(32);
        String nonce = token(32);
        String pkceVerifier = token(64);
        String url = oauth2.authorizeURL(new OAuth2AuthorizationURL()
                                                 .setRedirectUri(callbackUrl)
                                                 .setScopes(scopes)
                                                 .setState(state)
                                                 .setCodeChallenge(OAuth2Util.s256Challenge(pkceVerifier))
                                                 .setCodeChallengeMethod("S256")
                                                 .putAdditionalParameter("nonce", nonce));
        return new OidcAuthorization(url, state, nonce, pkceVerifier);
    }

    @Override
    public Future<Map<String, Object>> verifiedClaims(String code, String callbackUrl, String pkceVerifier, String nonce) {
        return oauth2.authenticate(new Oauth2Credentials()
                                           .setFlow(OAuth2FlowType.AUTH_CODE)
                                           .setCode(code)
                                           .setRedirectUri(callbackUrl)
                                           .setCodeVerifier(pkceVerifier))
                     .compose(user -> {
                         Future<Map<String, Object>> ret;
                         if (StringUtils.isNotBlank(settings.getUserInfoUri())) {
                             // A provider configured with an identity endpoint issues no id_token, so there is no
                             // issuer/audience/nonce to check — the caller's state match plus the direct TLS code
                             // exchange are the security boundary, and identity comes from the provider's API using
                             // the exchanged access token.
                             ret = apiClaims(user);
                         } else {
                             ret = idTokenClaims(user, nonce);
                         }
                         return ret;
                     });
    }

    @Override
    public void close() {
        // cancels the periodic JWKS refresh discovery started
        oauth2.close();
    }

    private Future<Map<String, Object>> idTokenClaims(User user, String nonce) {
        Map<String, Object> claims = flattenClaims(user);
        Future<Map<String, Object>> ret;
        if (!OAuth2Util.isIssuerValid(claims, settings.getAuthority())) {
            ret = Future.failedFuture(new InvalidIdTokenException("The id token's issuer " + claims.get("iss")
                                                                  + " is not " + settings.getAuthority()));
        } else if (!OAuth2Util.isAudienceValid(claims, settings.getAudience())) {
            ret = Future.failedFuture(new InvalidIdTokenException("The id token's audience " + claims.get("aud")
                                                                  + " does not name " + settings.getAudience()));
        } else if (!nonce.equals(OAuth2Util.stringClaim(claims, "nonce"))) {
            // authorize always sends a nonce, so OIDC Core 3.1.3.7 requires the id_token to echo it — an absent
            // claim fails the equals and is rejected
            ret = Future.failedFuture(new InvalidIdTokenException("The id token's nonce does not match the sign-in"));
        } else if (isIdTokenExpired(user)) {
            ret = Future.failedFuture(new InvalidIdTokenException("The id token has expired or states no expiry"));
        } else if (OAuth2Util.stringClaim(claims, "email") != null && !claims.containsKey("email_verified")
                && !OAuth2Util.issuesOnlyVerifiedEmails(settings.getProvider())) {
            // Okta's id_token from a code exchange leaves email_verified to the userinfo endpoint
            ret = withUserInfoEmailVerified(user, claims);
        } else {
            ret = Future.succeededFuture(claims);
        }
        return ret;
    }

    // Vert.x holds the sign-in to the token response's expires_in and the access token's exp, so the id token's own
    // exp, which OIDC Core 3.1.3.7 requires, is checked here. It is read from the id token itself: flattenClaims
    // fills a claim the id token leaves out from the token response, whose exp is derived from expires_in.
    private static boolean isIdTokenExpired(User user) {
        Object expiry = user.attributes().getJsonObject("idToken", new JsonObject()).getValue("exp");
        return !(expiry instanceof Number expiresAt)
                || Instant.now().getEpochSecond() - ID_TOKEN_CLOCK_SKEW_SECONDS >= expiresAt.longValue();
    }

    private Future<Map<String, Object>> withUserInfoEmailVerified(User user, Map<String, Object> claims) {
        return oauth2.userInfo(user)
                     .recover(err -> {
                         // the provider then states no verification, which the caller treats as unverified
                         log.warn("OIDC userinfo request to {} failed: {}", settings.getAuthority(), err.getMessage());
                         return Future.succeededFuture(null);
                     })
                     .compose(userInfo -> {
                         Future<Map<String, Object>> ret;
                         if (userInfo == null) {
                             ret = Future.succeededFuture(claims);
                         } else if (!Objects.equals(claims.get("sub"), userInfo.getString("sub"))) {
                             // OIDC Core 5.3.2: the userinfo response is the id_token's subject only when its sub matches
                             ret = Future.failedFuture(new InvalidIdTokenException("The userinfo response is for another subject"));
                         } else {
                             // email_verified speaks for the userinfo's email, so it applies only to the same address
                             if (Objects.equals(claims.get("email"), userInfo.getString("email")) && userInfo.containsKey("email_verified")) {
                                 claims.put("email_verified", userInfo.getValue("email_verified"));
                             }
                             ret = Future.succeededFuture(claims);
                         }
                         return ret;
                     });
    }

    // Vert.x puts the decoded id_token claims under the idToken attribute; the principal holds only the raw
    // token-endpoint response
    private static Map<String, Object> flattenClaims(User user) {
        Map<String, Object> map = new HashMap<>();
        JsonObject attrs = user.attributes();
        if (attrs != null) {
            JsonObject idToken = attrs.getJsonObject("idToken");
            if (idToken != null) idToken.forEach(e -> map.put(e.getKey(), e.getValue()));
            attrs.forEach(e -> {
                if (!"idToken".equals(e.getKey()) && !map.containsKey(e.getKey())) {
                    map.put(e.getKey(), e.getValue());
                }
            });
        }
        return map;
    }

    /**
     * Builds the identity claims for a provider with no id_token by querying the endpoints
     * on its settings: {@code userInfoUri} supplies {@code sub}, {@code name} and
     * {@code email}; {@code userEmailsUri}, when set, supplies the definitive {@code email}
     * and {@code email_verified} from a GitHub-style emails array.
     */
    private Future<Map<String, Object>> apiClaims(User user) {
        String accessToken = user.principal().getString("access_token");
        return apiGet(accessToken, settings.getUserInfoUri())
                .map(response -> profileClaims(response.bodyAsJsonObject()))
                .compose(claims -> {
                    Future<Map<String, Object>> ret;
                    if (StringUtils.isNotBlank(settings.getUserEmailsUri())) {
                        ret = apiGet(accessToken, settings.getUserEmailsUri())
                                .map(response -> withBestEmail(claims, response.bodyAsJsonArray()));
                    } else {
                        ret = Future.succeededFuture(claims);
                    }
                    return ret;
                });
    }

    /**
     * Maps an identity-endpoint profile to claims, reading the standard OIDC userinfo names
     * first and the GitHub names as fallbacks: {@code sub} else numeric {@code id},
     * {@code name} else {@code login}, {@code email} / {@code email_verified} as given.
     */
    private static Map<String, Object> profileClaims(JsonObject profile) {
        Map<String, Object> claims = new HashMap<>();
        Object sub = profile.getValue("sub") != null ? profile.getValue("sub") : profile.getValue("id");
        if (sub != null) {
            claims.put("sub", String.valueOf(sub));
        }
        String name = profile.getString("name");
        if (name == null || name.isBlank()) {
            name = profile.getString("login");
        }
        if (name != null) {
            claims.put("name", name);
        }
        String email = profile.getString("email");
        if (email != null) {
            claims.put("email", email);
            Object verified = profile.getValue("email_verified");
            if (verified != null) {
                claims.put("email_verified", verified);
            }
        }
        return claims;
    }

    /** Replaces the email claims with the best address from a GitHub-style emails array. */
    private static Map<String, Object> withBestEmail(Map<String, Object> claims, JsonArray emails) {
        JsonObject best = bestEmail(emails);
        if (best != null) {
            claims.put("email", best.getString("email"));
            claims.put("email_verified", Boolean.TRUE.equals(best.getBoolean("verified")));
        }
        return claims;
    }

    /**
     * The primary verified entry, else any verified entry, else the primary entry;
     * {@code null} when the account has no address matching any of these.
     */
    private static JsonObject bestEmail(JsonArray emails) {
        JsonObject verified = null;
        JsonObject primary = null;
        for (int i = 0; i < emails.size(); i++) {
            JsonObject entry = emails.getJsonObject(i);
            boolean isPrimary = Boolean.TRUE.equals(entry.getBoolean("primary"));
            if (Boolean.TRUE.equals(entry.getBoolean("verified"))) {
                if (isPrimary) {
                    // Primary + verified wins outright
                    verified = entry;
                    break;
                }
                if (verified == null) {
                    verified = entry;
                }
            } else if (isPrimary) {
                primary = entry;
            }
        }
        // An unverified primary still surfaces so the caller can refuse the email as unverified
        // rather than as missing
        return verified != null ? verified : primary;
    }

    private Future<HttpResponse<Buffer>> apiGet(String accessToken, String uri) {
        return webClient.getAbs(uri)
                        .putHeader("Authorization", "Bearer " + accessToken)
                        .putHeader("Accept", "application/json")
                        .send()
                        .compose(response -> {
                            Future<HttpResponse<Buffer>> ret;
                            if (response.statusCode() == 200) {
                                ret = Future.succeededFuture(response);
                            } else {
                                // On GitHub, a 403 from /user/emails usually means the app lacks the
                                // "Email addresses: read-only" account permission
                                ret = Future.failedFuture(new IllegalStateException(
                                        "Identity API GET " + uri + " answered " + response.statusCode()));
                            }
                            return ret;
                        });
    }

    private static String token(int bytes) {
        byte[] ret = new byte[bytes];
        RANDOM.nextBytes(ret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(ret);
    }

}
