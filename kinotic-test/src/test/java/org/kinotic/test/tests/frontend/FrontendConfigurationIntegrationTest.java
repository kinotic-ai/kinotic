package org.kinotic.test.tests.frontend;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.kinotic.persistence.api.config.StructuresProperties;
import org.kinotic.rpc.api.config.OidcSecurityServiceProperties;
import org.kinotic.test.support.elastic.ElasticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for frontend configuration generation.
 * This test boots up the actual application and tests the real configuration endpoint.
 */
public class FrontendConfigurationIntegrationTest extends ElasticTestBase {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private OidcSecurityServiceProperties oidcSecurityServiceProperties;
    @Autowired
    private StructuresProperties structuresProperties;

    @DynamicPropertySource
    static void registerOidcSecurityServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("oidc-security-service.enabled", () -> true);
    }

    private String getURL() {
        return String.format("http://localhost:%d/%s", structuresProperties.getWebServerPort(), oidcSecurityServiceProperties.getFrontendConfigurationPath());
    }

    @Test
    public void testFrontendConfigurationEndpoint_ReturnsValidConfiguration() throws Exception {
        // Given: Application is running with test profile

        // When: Request the frontend configuration endpoint
        ResponseEntity<String> response = restTemplate.getForEntity(getURL(), String.class);
        
        // Then: Response should be successful
        assertTrue(response.getStatusCode().is2xxSuccessful(), 
            "Expected successful response, got: " + response.getStatusCode());
        assertNotNull(response.getBody(), "Response body should not be null");
        
        // Parse the JSON response
        JsonNode config = objectMapper.readTree(response.getBody());
        
        // Validate basic structure - the config is the FrontendConfiguration object directly
        assertTrue(config.has("enabled"), "Configuration should have 'enabled' field");
        assertTrue(config.has("oidcProviders"), "Configuration should have 'oidcProviders' section");
        assertTrue(config.has("debug"), "Configuration should have 'debug' field");
        
        // Validate OIDC providers - now they're in a list
        JsonNode oidcProviders = config.get("oidcProviders");
        assertTrue(oidcProviders.isArray(), "oidcProviders should be an array");
        
        // Find specific providers in the list  
        JsonNode keycloakProvider = findProviderByName(oidcProviders, "keycloak");
        assertNotNull(keycloakProvider, "Keycloak provider should be present");
        assertTrue(keycloakProvider.get("enabled").asBoolean(), "Keycloak provider should be enabled");
        assertEquals("test-keycloak-client", keycloakProvider.get("clientId").asText(), "Keycloak client ID should match test config");
        assertEquals("http://localhost:8888/auth/realms/test", keycloakProvider.get("authority").asText(), "Keycloak authority should match test config");
        
        // Validate debug flag (should match test configuration)
        assertTrue(config.get("debug").asBoolean(), "Debug should be enabled based on test config");
        
        // Validate redirect URIs for enabled providers
        String expectedBaseUrl = "http://localhost:8989";
        
        assertEquals(expectedBaseUrl + "/login", keycloakProvider.get("redirectUri").asText(), "Keycloak redirect URI should be correct");
        assertEquals(expectedBaseUrl, keycloakProvider.get("postLogoutRedirectUri").asText(), "Keycloak post-logout redirect URI should be correct");
        assertEquals(expectedBaseUrl + "/login/silent-renew", keycloakProvider.get("silentRedirectUri").asText(), "Keycloak silent redirect URI should be correct");
    }

    @Test
    public void testFrontendConfigurationEndpoint_ResponseHeaders() {
        // Given: Application is running with test profile
        
        ResponseEntity<String> response = restTemplate.getForEntity(getURL(), String.class);
        
        // Then: Response headers should be correct
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // Check content type
        assertTrue(response.getHeaders().getContentType().toString().contains("application/json"), 
            "Content-Type should be application/json");
        
        // Check cache control headers
        String cacheControl = response.getHeaders().getFirst("Cache-Control");
        assertNotNull(cacheControl, "Cache-Control header should be present");
        assertTrue(cacheControl.contains("no-cache"), "Cache-Control should contain no-cache");
        assertTrue(cacheControl.contains("no-store"), "Cache-Control should contain no-store");
        assertTrue(cacheControl.contains("must-revalidate"), "Cache-Control should contain must-revalidate");
        
        String pragma = response.getHeaders().getFirst("Pragma");
        assertNotNull(pragma, "Pragma header should be present");
        assertEquals("no-cache", pragma, "Pragma should be no-cache");
        
        String expires = response.getHeaders().getFirst("Expires");
        assertNotNull(expires, "Expires header should be present");
        assertEquals("0", expires, "Expires should be 0");
    }

    @Test
    public void testFrontendConfigurationEndpoint_JsonStructure() throws Exception {
        // Given: Application is running with test profile
        
        // When: Request the frontend configuration endpoint
        ResponseEntity<String> response = restTemplate.getForEntity(getURL(), String.class);
        
        // Then: Response should be valid JSON
        assertTrue(response.getStatusCode().is2xxSuccessful());
        String responseBody = response.getBody();
        assertNotNull(responseBody);
        
        // Verify it's valid JSON by parsing it
        JsonNode config = objectMapper.readTree(responseBody);
        
        // Verify all required fields are present and have correct types
        assertTrue(config.isObject(), "Root should be a JSON object");
        assertTrue(config.get("enabled").isBoolean(), "enabled should be a boolean");
        assertTrue(config.get("oidcProviders").isArray(), "oidcProviders should be an array");
        assertTrue(config.get("debug").isBoolean(), "debug should be a boolean");
        
        // Verify OIDC provider structure
        JsonNode oidcProviders = config.get("oidcProviders");
        for (JsonNode provider : oidcProviders) {
            assertTrue(provider.has("enabled"), "Provider should have 'enabled' field");
            assertTrue(provider.has("provider"), "Provider should have 'provider' field");
            assertTrue(provider.has("displayName"), "Provider should have 'displayName' field");
            assertTrue(provider.has("clientId"), "Provider should have 'clientId' field");
            assertTrue(provider.has("authority"), "Provider should have 'authority' field");
            assertTrue(provider.has("redirectUri"), "Provider should have 'redirectUri' field");
            assertTrue(provider.has("postLogoutRedirectUri"), "Provider should have 'postLogoutRedirectUri' field");
            assertTrue(provider.has("silentRedirectUri"), "Provider should have 'silentRedirectUri' field");
        }
        
    }

    @Test
    public void testFrontendConfigurationEndpoint_EndpointAccessibility() {
        // Given: Application is running with test profile
        
        // When: Request the frontend configuration endpoint
        ResponseEntity<String> response = restTemplate.getForEntity(getURL(), String.class);
        
        // Then: Endpoint should be accessible and return valid JSON
        assertTrue(response.getStatusCode().is2xxSuccessful(), 
            "Endpoint should be accessible, got status: " + response.getStatusCode());
        assertNotNull(response.getBody(), "Response body should not be null");
        assertFalse(response.getBody().isEmpty(), "Response body should not be empty");
        
        // Verify it's valid JSON by checking it starts with {
        assertTrue(response.getBody().trim().startsWith("{"), 
            "Response should be valid JSON starting with {");
        assertTrue(response.getBody().trim().endsWith("}"), 
            "Response should be valid JSON ending with }");
    }
    
    /**
     * Helper method to find a provider by name in the oidcProviders array
     */
    private JsonNode findProviderByName(JsonNode oidcProviders, String providerName) {
        for (JsonNode provider : oidcProviders) {
            if (providerName.equals(provider.get("provider").asText())) {
                return provider;
            }
        }
        return null;
    }
}
