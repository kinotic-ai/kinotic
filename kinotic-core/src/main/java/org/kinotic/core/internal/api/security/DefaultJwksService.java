
package org.kinotic.core.internal.api.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.Jwks;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.config.OidcProvider;
import org.kinotic.core.api.config.OidcSecurityServiceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.security.Key;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "oidc-security-service", name = "enabled", havingValue = "true")
public class DefaultJwksService implements JwksService {

    private final WebClient webClient;
    private final WebClient insecureWebClient;
    private final ObjectMapper objectMapper;
    private final Cache<String, Jwk<? extends Key>> keyCache;
    private final Cache<String, JsonNode> wellKnownCache;
    private final Map<String, String> issuerToBackchannel;

    public DefaultJwksService(Vertx vertx, OidcSecurityServiceProperties oidcProperties) {
        this.webClient = WebClient.create(vertx);
        this.insecureWebClient = createInsecureWebClient(vertx);
        this.objectMapper = new ObjectMapper();

        // Build issuer -> backchannelAuthority map from configured providers
        this.issuerToBackchannel = new HashMap<>();
        if (oidcProperties.getOidcProviders() != null) {
            for (OidcProvider provider : oidcProperties.getOidcProviders()) {
                if (provider.getBackchannelAuthority() != null && provider.getAuthority() != null) {
                    issuerToBackchannel.put(provider.getAuthority(), provider.getBackchannelAuthority());
                    log.info("OIDC backchannel: {} -> {}", provider.getAuthority(), provider.getBackchannelAuthority());
                }
            }
        }

        // Cache for individual keys, with 1 hour TTL
        this.keyCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(100)
                .build();

        // Cache for well-known configuration, with 24 hour TTL
        this.wellKnownCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(24))
                .maximumSize(10)
                .build();
    }

    /**
     * Create a WebClient that trusts all SSL certificates.
     * WARNING: Only use for development with .local domains!
     */
    private WebClient createInsecureWebClient(Vertx vertx) {
        try {
            WebClientOptions options = new WebClientOptions()
                    .setTrustAll(true)
                    .setVerifyHost(false);

            return WebClient.create(vertx, options);
        } catch (Exception e) {
            log.warn("Failed to create insecure WebClient, falling back to default", e);
            return WebClient.create(vertx);
        }
    }

    /**
     * Get the appropriate WebClient based on the URL.
     * Uses an insecure client for backchannel URLs (cluster-internal, self-signed certs)
     * and .local domains (development).
     */
    private WebClient getWebClientForUrl(String url) {
        if (url != null) {
            if (url.contains(".local")) {
                log.debug("Using insecure WebClient for .local domain: {}", url);
                return insecureWebClient;
            }
            // Use insecure client for any backchannel URL (cluster-internal, self-signed certs)
            for (String backchannel : issuerToBackchannel.values()) {
                if (url.startsWith(backchannel)) {
                    log.debug("Using insecure WebClient for backchannel URL: {}", url);
                    return insecureWebClient;
                }
            }
        }
        return webClient;
    }

    /**
     * Resolves the fetch URL for an issuer. If a backchannelAuthority is configured
     * for this issuer, returns it instead — allowing the server to reach the OIDC
     * provider via a cluster-internal URL while the JWT iss claim uses the external URL.
     */
    private String resolveIssuerUrl(String issuer) {
        String backchannel = issuerToBackchannel.get(issuer);
        if (backchannel != null) {
            log.debug("Resolving issuer {} to backchannel {}", issuer, backchannel);
            return backchannel;
        }
        return issuer;
    }

    /**
     * Get the well-known configuration for an OIDC issuer
     */
    private Future<JsonNode> getWellKnownConfiguration(String issuer) {
        String cacheKey = "well-known:" + issuer;
        JsonNode cached = wellKnownCache.getIfPresent(cacheKey);
        if (cached != null) {
            return Future.succeededFuture(cached);
        }

        String fetchUrl = resolveIssuerUrl(issuer);
        String wellKnownUrl = fetchUrl + "/.well-known/openid-configuration";

        return getWebClientForUrl(wellKnownUrl)
                .getAbs(wellKnownUrl)
                .send()
                .map(response -> {
                    try {
                        JsonNode config = objectMapper.readTree(response.bodyAsString());
                        wellKnownCache.put(cacheKey, config);
                        return config;
                    } catch (Exception e) {
                        log.error("Failed to parse well-known configuration for issuer: {}", issuer, e);
                        throw new RuntimeException("Failed to parse OIDC configuration", e);
                    }
                });
    }

    /**
     * Get the JWKS URL from the well-known configuration.
     * Rewrites the URL to use the backchannel authority if configured,
     * since Keycloak's well-known response contains the external hostname.
     */
    private Future<String> getJwksUrl(String issuer) {
        return getWellKnownConfiguration(issuer)
                .map(config -> {
                    JsonNode jwksUri = config.get("jwks_uri");
                    if (jwksUri == null || jwksUri.isNull()) {
                        throw new RuntimeException("JWKS URI not found in OIDC configuration for issuer: " + issuer);
                    }
                    String url = jwksUri.asString();
                    // Rewrite the JWKS URL if we have a backchannel for this issuer
                    String backchannel = issuerToBackchannel.get(issuer);
                    if (backchannel != null) {
                        url = url.replace(issuer, backchannel);
                        log.debug("Rewrote JWKS URL to backchannel: {}", url);
                    }
                    return url;
                });
    }


    @Override
    public CompletableFuture<Jwk<? extends Key>> getKey(String issuer, String kid) {
        String cacheKey = issuer + ":" + kid;
        Jwk<? extends Key> cachedKey = keyCache.getIfPresent(cacheKey);
        if (cachedKey != null) {
            return CompletableFuture.completedFuture(cachedKey);
        }

        CompletableFuture<Jwk<? extends Key>> result = new CompletableFuture<>();

        getJwksUrl(issuer)
                .compose(jwksUrl -> getWebClientForUrl(jwksUrl)
                        .getAbs(jwksUrl)
                        .send()
                        .map(response -> {
                            try {
                                JsonNode jwks = objectMapper.readTree(response.bodyAsString());
                                JsonNode keys = jwks.get("keys");

                                if (keys == null || !keys.isArray()) {
                                    throw new RuntimeException("Invalid JWKS response: no keys array found");
                                }

                                for (JsonNode key : keys) {
                                    String keyKid = key.get("kid") != null ? key.get("kid").asString() : null;
                                    if (kid.equals(keyKid)) {
                                        // Use the correct JJWT 0.12.x API for parsing RSA keys
                                        Jwk<? extends Key> parsedKey = Jwks.parser()
                                                                           .build()
                                                                           .parse(objectMapper.writeValueAsString(key));
                                        keyCache.put(cacheKey, parsedKey);
                                        return parsedKey;
                                    }
                                }

                                throw new RuntimeException("Key with kid '" + kid + "' not found in JWKS for issuer: " + issuer);
                            } catch (Exception e) {
                                log.error("Failed to parse JWKS for issuer: {} and kid: {}", issuer, kid, e);
                                throw new RuntimeException("Failed to parse JWKS", e);
                            }
                        }))
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        result.complete(ar.result());
                    } else {
                        result.completeExceptionally(ar.cause());
                    }
                });

        return result;
    }

    @Override
    public CompletableFuture<Jwk<? extends Key>> getKeyFromToken(String token) {
        try {
            // Parse the JWT header to get the key ID
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new RuntimeException("Invalid JWT token format");
            }

            // Decode the header
            String headerJson = new String(java.util.Base64.getUrlDecoder().decode(parts[0]));
            JsonNode header = objectMapper.readTree(headerJson);
            
            String kid = header.get("kid") != null ? header.get("kid").asString() : null;
            if (kid == null) {
                throw new RuntimeException("JWT token does not contain a key ID (kid)");
            }

            // Parse the payload to get the issuer
            String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            JsonNode payload = objectMapper.readTree(payloadJson);
            String issuer = payload.get("iss") != null ? payload.get("iss").asString() : null;
            if (issuer == null) {
                throw new RuntimeException("JWT token does not contain an issuer (iss)");
            }
            return getKey(issuer, kid);
        } catch (Exception e) {
            log.error("Failed to extract key information from JWT token", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    public void clearCaches() {
        keyCache.invalidateAll();
        wellKnownCache.invalidateAll();
    }
} 