package org.mindignited.structures.tests.auth;

import org.junit.jupiter.api.Test;
import org.kinotic.continuum.api.security.Participant;
import org.mindignited.structures.auth.internal.services.OidcSecurityService;
import org.mindignited.structures.support.keycloak.KeycloakTestBase;
import org.mindignited.structures.support.keycloak.KeyloakTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for admin OIDC provider access control validation.
 * 
 * Tests the admin provider configuration with:
 * - Audiences: ["admin-client"]
 * - Required roles: ["admin", "poweruser"]
 * 
 * Test scenarios:
 * - Valid audience + valid roles = success
 * - Valid audience + invalid roles = failure
 * - Valid audience + no roles = failure
 */
@ActiveProfiles("keycloak-admin-provider")
public class AdminProviderAccessControlTest extends KeycloakTestBase {

    @Autowired
    private OidcSecurityService securityService;

    @Test
    public void testValidAudienceWithValidRoles() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(KeyloakTestConfiguration.KEYCLOAK_CONTAINER != null,
                "Keycloak container not available");
        
        // Admin user with admin role should succeed
        String token = fetchKeycloakAccessToken("adminuser@example.com", "admin123", "admin-client");
        assertNotNull(token);
        
        Map<String, String> authInfo = Map.of("authorization", "Bearer " + token);
        CompletableFuture<Participant> result = securityService.authenticate(authInfo);

        while(!result.isDone()) {
            Thread.sleep(1000);
        }

        Participant participant = result.join();

        assertNotNull(participant);
        assertEquals("mindignited", participant.getTenantId());
        assertTrue(participant.getRoles().contains("admin"));
        assertTrue(participant.getRoles().contains("user"));
    }

    @Test
    public void testValidAudienceWithPowerUserRole() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(KeyloakTestConfiguration.KEYCLOAK_CONTAINER != null,
                "Keycloak container not available");
        
        // Power user with poweruser role should succeed
        String token = fetchKeycloakAccessToken("poweruser@example.com", "power123", "admin-client");
        assertNotNull(token);
        
        Map<String, String> authInfo = Map.of("authorization", "Bearer " + token);
        Participant participant = securityService.authenticate(authInfo).join();
        
        assertNotNull(participant);
        assertEquals("mindignited", participant.getTenantId());
        assertTrue(participant.getRoles().contains("poweruser"));
    }

    @Test
    public void testValidAudienceWithInsufficientRoles() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(KeyloakTestConfiguration.KEYCLOAK_CONTAINER != null,
                "Keycloak container not available");
        
        // Regular user with only 'user' role should fail (admin-client requires admin or poweruser)
        String token = fetchKeycloakAccessToken("testuser@example.com", "password123", "admin-client");
        assertNotNull(token);
        
        Map<String, String> authInfo = Map.of("authorization", "Bearer " + token);
        CompletableFuture<Participant> result = securityService.authenticate(authInfo);

        while(!result.isDone()) {
            Thread.sleep(1000);
        }
        
        // Should fail because user doesn't have required roles
        assertTrue(result.isCompletedExceptionally());
        CompletionException exception = assertThrows(CompletionException.class, () -> result.join());
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertTrue(exception.getCause().getMessage().contains("User does not have any required roles"));
    }

    @Test
    public void testValidAudienceWithNoRoles() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(KeyloakTestConfiguration.KEYCLOAK_CONTAINER != null,
                "Keycloak container not available");
        
        // User with no roles should fail
        String token = fetchKeycloakAccessToken("noroles@example.com", "nopass123", "admin-client");
        assertNotNull(token);
        
        Map<String, String> authInfo = Map.of("authorization", "Bearer " + token);
        CompletableFuture<Participant> result = securityService.authenticate(authInfo);

        while(!result.isDone()) {
            Thread.sleep(1000);
        }
        
        // Should fail because user has no roles
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
        
        tools.jackson.databind.JsonNode node = new tools.jackson.databind.ObjectMapper().readTree(response.body());
        return node.get("access_token").asText();
    }
}
