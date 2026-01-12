
package org.mindignited.structures.auth.internal.services;

import java.net.URI;
import java.security.Key;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.mindignited.structures.auth.api.services.JwksService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;

import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.Jwks;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "oidc-security-service", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DefaultJwksService implements JwksService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Cache<String, Jwk<? extends Key>> keyCache;
    private final Cache<String, JsonNode> wellKnownCache;

    public DefaultJwksService(DefaultCaffeineCacheFactory cacheFactory) {
        this.webClient = WebClient.builder().build();
        this.objectMapper = new ObjectMapper();
        
        // Cache for individual keys, with 1 hour TTL
        this.keyCache = cacheFactory.<String, Jwk<? extends Key>>newBuilder()
                .name("jwksKeyCache")
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(100)
                .build();
                
        // Cache for well-known configuration, with 24 hour TTL
        this.wellKnownCache = cacheFactory.<String, JsonNode>newBuilder()
                .name("jwksWellKnownCache")
                .expireAfterWrite(Duration.ofHours(24))
                .maximumSize(10)
                .build();
    }

    /**
     * Get the well-known configuration for an OIDC issuer
     */
    public CompletableFuture<JsonNode> getWellKnownConfiguration(String issuer) {
        String cacheKey = "well-known:" + issuer;
        JsonNode cached = wellKnownCache.getIfPresent(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        String wellKnownUrl = issuer + "/.well-known/openid-configuration";
        
        return webClient.get()
                .uri(URI.create(wellKnownUrl))
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    try {
                        JsonNode config = objectMapper.readTree(response);
                        wellKnownCache.put(cacheKey, config);
                        return config;
                    } catch (Exception e) {
                        log.error("Failed to parse well-known configuration for issuer: {}", issuer, e);
                        throw new RuntimeException("Failed to parse OIDC configuration", e);
                    }
                })
                .toFuture();
    }

    /**
     * Get the JWKS URL from the well-known configuration
     */
    public CompletableFuture<String> getJwksUrl(String issuer) {
        return getWellKnownConfiguration(issuer)
                .thenApply(config -> {
                    JsonNode jwksUri = config.get("jwks_uri");
                    if (jwksUri == null || jwksUri.isNull()) {
                        throw new RuntimeException("JWKS URI not found in OIDC configuration for issuer: " + issuer);
                    }
                    return jwksUri.asText();
                });
    }

    /**
     * Get a key by its key ID (kid)
     */
    public CompletableFuture<Jwk<? extends Key>> getKey(String issuer, String kid) {
        String cacheKey = issuer + ":" + kid;
        Jwk<? extends Key> cachedKey = keyCache.getIfPresent(cacheKey);
        if (cachedKey != null) {
            return CompletableFuture.completedFuture(cachedKey);
        }

        return getJwksUrl(issuer)
                .thenCompose(jwksUrl -> webClient.get()
                        .uri(URI.create(jwksUrl))
                        .retrieve()
                        .bodyToMono(String.class)
                        .toFuture())
                .thenApply(jwksResponse -> {
                    try {
                        JsonNode jwks = objectMapper.readTree(jwksResponse);
                        JsonNode keys = jwks.get("keys");
                        
                        if (keys == null || !keys.isArray()) {
                            throw new RuntimeException("Invalid JWKS response: no keys array found");
                        }

                        for (JsonNode key : keys) {
                            String keyKid = key.get("kid") != null ? key.get("kid").asText() : null;
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
                });
    }

    /**
     * Extract the issuer and key ID from a JWT token header
     */
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
            
            String kid = header.get("kid") != null ? header.get("kid").asText() : null;
            if (kid == null) {
                throw new RuntimeException("JWT token does not contain a key ID (kid)");
            }

            // Parse the payload to get the issuer
            String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            JsonNode payload = objectMapper.readTree(payloadJson);
            String issuer = payload.get("iss") != null ? payload.get("iss").asText() : null;
            if (issuer == null) {
                throw new RuntimeException("JWT token does not contain an issuer (iss)");
            }
            return getKey(issuer, kid);
        } catch (Exception e) {
            log.error("Failed to extract key information from JWT token", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Clear all caches (useful for testing or manual cache invalidation)
     */
    public void clearCaches() {
        keyCache.invalidateAll();
        wellKnownCache.invalidateAll();
    }
} 