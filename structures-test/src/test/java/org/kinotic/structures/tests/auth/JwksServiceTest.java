package org.kinotic.structures.tests.auth;

import io.jsonwebtoken.security.Jwk;
import org.junit.jupiter.api.Test;
import org.kinotic.structures.auth.api.services.JwksService;
import org.kinotic.structures.support.keycloak.KeycloakTestBase;
import org.kinotic.structures.support.keycloak.KeyloakTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for JwksService.
 * 
 * Tests include:
 * - JWKS key parsing
 * - Cache behavior
 * - Error handling
 */
@ActiveProfiles("keycloak")
public class JwksServiceTest extends KeycloakTestBase {

    @Autowired
    private JwksService jwksService;

    @Test
    public void testJwksServiceInitialization() {
        assertNotNull(jwksService);
        
        // Test that caches are initialized
        // Note: We can't directly access private fields, but we can test behavior
        assertDoesNotThrow(() -> jwksService.clearCaches());
    }

    @Test
    public void testClearCaches() {
        // Test that clearing caches doesn't throw exceptions
        assertDoesNotThrow(() -> jwksService.clearCaches());
    }

    @Test
    public void testInvalidJwtTokenFormat() {
        String invalidToken = "invalid.token.format";
        
        CompletableFuture<Jwk<?>> result = jwksService.getKeyFromToken(invalidToken);
        
        assertTrue(result.isCompletedExceptionally());
    }

    @Test
    public void testJwtTokenWithoutKid() {
        // Create a JWT token without kid in header
        String tokenWithoutKid = createJwtTokenWithoutKid();
        
        CompletableFuture<Jwk<?>> result = jwksService.getKeyFromToken(tokenWithoutKid);
        
        assertTrue(result.isCompletedExceptionally());
    }

    @Test
    public void testJwtTokenWithoutIssuer() {
        // Create a JWT token without issuer in payload
        String tokenWithoutIssuer = createJwtTokenWithoutIssuer();
        
        CompletableFuture<Jwk<?>> result = jwksService.getKeyFromToken(tokenWithoutIssuer);
        
        assertTrue(result.isCompletedExceptionally());
    }

    // It will be re-enabled once the container initialization issues are resolved
    @Test
    public void testFetchKeyFromValidToken() throws Exception {
        // Use testcontainer Keycloak started by TestConfiguration
        org.junit.jupiter.api.Assumptions.assumeTrue(KeyloakTestConfiguration.KEYCLOAK_CONTAINER != null,
                "Keycloak container not available");
        String token = fetchKeycloakAccessToken("testuser@example.com", "password123");
        assertNotNull(token);
        CompletableFuture<Jwk<?>> result = jwksService.getKeyFromToken(token);
        assertDoesNotThrow(() -> result.join());
        assertNotNull(result.join());
    }

    // Helper methods for creating test JWT tokens
    private static String createJwtTokenWithoutKid() {
        // Create a JWT token with header that doesn't contain 'kid'
        String header = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9"; // Base64 encoded {"alg":"RS256","typ":"JWT"}
        String payload = "eyJpc3MiOiJodHRwczovL3Rlc3QtZXhhbXBsZS5jb20iLCJzdWIiOiJ0ZXN0LXVzZXIifQ"; // Base64 encoded {"iss":"https://test-example.com","sub":"test-user"}
        String signature = "dummy-signature";
        
        return header + "." + payload + "." + signature;
    }

    private static String createJwtTokenWithoutIssuer() {
        // Create a JWT token with payload that doesn't contain 'iss'
        String header = "eyJraWQiOiJ0ZXN0LWtleSIsImFsZyI6IlJTMjU2IiwidHlwIjoiSldUIn0"; // Base64 encoded {"kid":"test-key","alg":"RS256","typ":"JWT"}
        String payload = "eyJzdWIiOiJ0ZXN0LXVzZXIifQ"; // Base64 encoded {"sub":"test-user"}
        String signature = "dummy-signature";
        
        return header + "." + payload + "." + signature;
    }

    private String fetchKeycloakAccessToken(String username, String password) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        String form = "username=" + java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8)
                + "&password=" + java.net.URLEncoder.encode(password, java.nio.charset.StandardCharsets.UTF_8)
                + "&grant_type=password&client_id=structures-client";
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(KeyloakTestConfiguration.getKeycloakUrl() + "/realms/test/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(form))
                .build();
        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Keycloak token endpoint returned status " + response.statusCode());
        }
        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
        return node.get("access_token").asText();
    }
    
} 