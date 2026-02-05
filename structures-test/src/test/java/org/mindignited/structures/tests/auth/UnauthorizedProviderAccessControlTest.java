package org.mindignited.structures.tests.auth;

import org.junit.jupiter.api.Test;
import org.mindignited.continuum.api.security.Participant;
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
 * Test class for unauthorized OIDC provider access control validation.
 * 
 * Tests the unauthorized provider configuration with:
 * - Audiences: ["unauthorized-client"]
 * - Required roles: ["user"]
 * 
 * This provider is NOT in the main application's allowed audiences,
 * so it should always fail audience validation.
 */
@ActiveProfiles("keycloak-unauthorized-provider")
public class UnauthorizedProviderAccessControlTest extends KeycloakTestBase {

    @Autowired
    private OidcSecurityService securityService;

    @Test
    public void testUnauthorizedClientAlwaysFails() throws Exception {
        assertTrue(KeyloakTestConfiguration.areContainersHealthy(),
                "Keycloak container not healthy");
        
        // Even with valid user and roles, unauthorized-client should fail
        String token = fetchKeycloakAccessToken("testuser@example.com", "password123", "unauthorized-client");
        assertNotNull(token);
        
        Map<String, String> authInfo = Map.of("authorization", "Bearer " + token);
        CompletableFuture<Participant> result = securityService.authenticate(authInfo);

        while(!result.isDone()) {
            Thread.sleep(1000);
        }
        
        // Should fail because unauthorized-client is not in allowed audiences
        assertTrue(result.isCompletedExceptionally());
        CompletionException exception = assertThrows(CompletionException.class, () -> result.join());
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertTrue(exception.getCause().getMessage().contains("Invalid audience"));
    }

    @Test
    public void testUnauthorizedClientWithAdminUserFails() throws Exception {
        assertTrue(KeyloakTestConfiguration.areContainersHealthy(),
                "Keycloak container not healthy");
        
        // Even admin user with admin-client should fail
        String token = fetchKeycloakAccessToken("adminuser@example.com", "admin123", "unauthorized-client");
        assertNotNull(token);
        
        Map<String, String> authInfo = Map.of("authorization", "Bearer " + token);
        CompletableFuture<Participant> result = securityService.authenticate(authInfo);

        while(!result.isDone()) {
            Thread.sleep(1000);
        }
        
        // Should fail because unauthorized-client is not in allowed audiences
        assertTrue(result.isCompletedExceptionally());
        CompletionException exception = assertThrows(CompletionException.class, () -> result.join());
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertTrue(exception.getCause().getMessage().contains("Invalid audience"));
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
