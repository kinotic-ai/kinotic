package org.kinotic.rpc.internal.api.security;

import java.util.concurrent.CompletableFuture;

import tools.jackson.databind.JsonNode;
import io.jsonwebtoken.security.Jwk;
import java.security.Key;

public interface JwksService {

    /**
     * Get a key by its key ID (kid)
     */
    CompletableFuture<Jwk<? extends Key>> getKey(String issuer, String kid);

    /**
     * Extract the issuer and key ID from a JWT token header
     */
    CompletableFuture<Jwk<? extends Key>> getKeyFromToken(String token);

    /**
     * Clear all caches (useful for testing or manual cache invalidation)
     */
    void clearCaches();
}
