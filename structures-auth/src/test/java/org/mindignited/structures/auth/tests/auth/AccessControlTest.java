package org.mindignited.structures.auth.tests.auth;

import org.junit.jupiter.api.Test;
import org.mindignited.continuum.api.security.Participant;
import org.mindignited.structures.auth.internal.services.OidcSecurityService;
import org.mindignited.structures.auth.KeycloakTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.mindignited.structures.auth.config.KeyloakTestConfiguration;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for main OIDC provider (structures-client) access control validation.
 * 
 * Tests the main provider configuration with:
 * - Audiences: ["structures-client"]
 * - Required roles: ["user", "admin", "poweruser"]
 * 
 * Test scenarios:
 * - Valid audience + valid roles = success
 * - Valid audience + invalid roles = failure
 * - Valid audience + no roles = failure
 */
public class AccessControlTest extends KeycloakTestBase {

    @Autowired
    private OidcSecurityService securityService;

    // ============================================================================
    // VALID AUDIENCE + VALID ROLES = SUCCESS
    // ============================================================================

    @Test
    public void testValidAudienceWithUserRole() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(KeyloakTestConfiguration.KEYCLOAK_CONTAINER != null,
                "Keycloak container not available");
        
        String token = fetchKeycloakAccessToken("testuser@example.com", "password123", "structures-client");
        assertNotNull(token);
        
        Map<String, String> authInfo = Map.of("authorization", "Bearer " + token);
        Participant participant = securityService.authenticate(authInfo).join();
        
        assertNotNull(participant);
        assertEquals("mindignited", participant.getTenantId());
        assertTrue(participant.getRoles().contains("user"));
    }

    @Test
    public void testValidAudienceWithAdminRole() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(KeyloakTestConfiguration.KEYCLOAK_CONTAINER != null,
                "Keycloak container not available");
        
        String token = fetchKeycloakAccessToken("adminuser@example.com", "admin123", "structures-client");
        assertNotNull(token);
        
        Map<String, String> authInfo = Map.of("authorization", "Bearer " + token);
        Participant participant = securityService.authenticate(authInfo).join();
        
        assertNotNull(participant);
        assertEquals("mindignited", participant.getTenantId());
        assertTrue(participant.getRoles().contains("admin"));
        assertTrue(participant.getRoles().contains("user"));
    }

    @Test
    public void testValidAudienceWithPowerUserRole() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(KeyloakTestConfiguration.KEYCLOAK_CONTAINER != null,
                "Keycloak container not available");
        
        String token = fetchKeycloakAccessToken("poweruser@example.com", "power123", "structures-client");
        assertNotNull(token);
        
        Map<String, String> authInfo = Map.of("authorization", "Bearer " + token);
        Participant participant = securityService.authenticate(authInfo).join();
        
        assertNotNull(participant);
        assertEquals("mindignited", participant.getTenantId());
        assertTrue(participant.getRoles().contains("poweruser"));
    }

    // ============================================================================
    // VALID AUDIENCE + INVALID/NO ROLES = FAILURE
    // ============================================================================

    @Test
    public void testValidAudienceWithNoRoles() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(KeyloakTestConfiguration.KEYCLOAK_CONTAINER != null,
                "Keycloak container not available");
        
        String token = fetchKeycloakAccessToken("noroles@example.com", "nopass123", "structures-client");
        assertNotNull(token);
        
        // Debug: Decode the JWT token to see what's actually in it
        String[] parts = token.split("\\.");
        if (parts.length == 3) {
            String payload = parts[1];
            // Add padding if needed
            while (payload.length() % 4 != 0) {
                payload += "=";
            }
            String decodedPayload = new String(java.util.Base64.getDecoder().decode(payload));
            System.out.println("JWT payload for noroles@example.com: " + decodedPayload);
        }
        
        // Debug: Check what the keycloak.test.url system property is set to
        String keycloakTestUrl = System.getProperty("keycloak.test.url");
        System.out.println("keycloak.test.url system property: " + keycloakTestUrl);
        
        Map<String, String> authInfo = Map.of("authorization", "Bearer " + token);
        CompletableFuture<Participant> result = securityService.authenticate(authInfo);

        while(!result.isDone()) {
            Thread.sleep(1000);
        }
        
        // Should fail because the provider requires roles but user has none
        assertTrue(result.isCompletedExceptionally());
        CompletionException exception = assertThrows(CompletionException.class, () -> result.join());
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertTrue(exception.getCause().getMessage().contains("No roles found in claims"));
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private String fetchKeycloakAccessToken(String username, String password, String clientId) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        String form = "username=" + java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8)
                + "&password=" + java.net.URLEncoder.encode(password, java.nio.charset.StandardCharsets.UTF_8)
                + "&grant_type=password&client_id=" + clientId;
        
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(KeyloakTestConfiguration.getKeycloakUrl() + "/realms/test/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(form))
                .build();
        
        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Keycloak token endpoint returned status " + response.statusCode() + " for client " + clientId);
        }
        
        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
        return node.get("access_token").asText();
    }
}
