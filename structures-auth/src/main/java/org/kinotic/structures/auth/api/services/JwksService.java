package org.kinotic.structures.auth.api.services;

import java.util.concurrent.CompletableFuture;

import tools.jackson.databind.JsonNode;
import io.jsonwebtoken.security.Jwk;
import java.security.Key;

public interface JwksService {

    CompletableFuture<JsonNode> getWellKnownConfiguration(String issuer);

    CompletableFuture<String> getJwksUrl(String issuer);

    CompletableFuture<Jwk<? extends Key>> getKey(String issuer, String kid);

    CompletableFuture<Jwk<? extends Key>> getKeyFromToken(String token);

    void clearCaches();
}
