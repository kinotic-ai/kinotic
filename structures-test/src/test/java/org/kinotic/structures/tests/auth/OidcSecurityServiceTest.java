package org.kinotic.structures.tests.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.structures.auth.api.config.OidcSecurityServiceProperties;
import org.kinotic.structures.auth.api.services.JwksService;
import org.kinotic.structures.auth.internal.services.OidcSecurityService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test class for OidcSecurityService path extraction functionality.
 */
class OidcSecurityServiceTest {

    private OidcSecurityService oidcSecurityService;
    private Claims testClaims;

    @Mock
    private OidcSecurityServiceProperties properties;
    
    @Mock
    private JwksService jwksService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        oidcSecurityService = new OidcSecurityService(properties, jwksService);
        
        // Create a mock Claims object for testing
        testClaims = mock(Claims.class);
        
        // Set up the mock to return test data
        Map<String, Object> realmAccess = new HashMap<>();
        realmAccess.put("roles", List.of("admin", "user"));
        
        Map<String, Object> groups = new HashMap<>();
        groups.put("department", "engineering");
        groups.put("level", "senior");
        
        // Mock the Claims methods for step-by-step path traversal
        when(testClaims.get("email", String.class)).thenReturn("test@example.com");
        when(testClaims.get("name", String.class)).thenReturn("Test User");
        when(testClaims.get("realm_access")).thenReturn(realmAccess);
        when(testClaims.get("groups")).thenReturn(groups);
        when(testClaims.get("permissions")).thenReturn(List.of("read", "write"));
        
        // Mock the get method without type parameter for nested traversal
        when(testClaims.get("email")).thenReturn("test@example.com");
        when(testClaims.get("name")).thenReturn("Test User");
        when(testClaims.get("realm_access")).thenReturn(realmAccess);
        when(testClaims.get("groups")).thenReturn(groups);
        when(testClaims.get("permissions")).thenReturn(List.of("read", "write"));
    }

    @Test
    void testExtractValueFromPath_SimplePath() {
        // Test simple path extraction
        String email = oidcSecurityService.extractValueFromPath(testClaims, "email", String.class);
        assertEquals("test@example.com", email);
        
        String name = oidcSecurityService.extractValueFromPath(testClaims, "name", String.class);
        assertEquals("Test User", name);
    }

    @Test
    void testExtractValueFromPath_NestedPath() {
        // Test nested path extraction
        @SuppressWarnings("unchecked")
        List<String> roles = oidcSecurityService.extractValueFromPath(testClaims, "realm_access.roles", List.class);
        assertNotNull(roles);
        assertEquals(2, roles.size());
        assertTrue(roles.contains("admin"));
        assertTrue(roles.contains("user"));
    }

    @Test
    void testExtractValueFromPath_DeeperNestedPath() {
        // Test deeper nested path extraction
        String department = oidcSecurityService.extractValueFromPath(testClaims, "groups.department", String.class);
        assertEquals("engineering", department);
        
        String level = oidcSecurityService.extractValueFromPath(testClaims, "groups.level", String.class);
        assertEquals("senior", level);
    }

    @Test
    void testExtractValueFromPath_ListPath() {
        // Test direct list path extraction
        @SuppressWarnings("unchecked")
        List<String> permissions = oidcSecurityService.extractValueFromPath(testClaims, "permissions", List.class);
        assertNotNull(permissions);
        assertEquals(2, permissions.size());
        assertTrue(permissions.contains("read"));
        assertTrue(permissions.contains("write"));
    }

    @Test
    void testExtractValueFromPath_NonExistentPath() {
        // Test non-existent path
        Object result = oidcSecurityService.extractValueFromPath(testClaims, "non.existent.path", Object.class);
        assertNull(result);
    }

    @Test
    void testExtractValueFromPath_EmptyPath() {
        // Test empty path
        Object result = oidcSecurityService.extractValueFromPath(testClaims, "", Object.class);
        assertNull(result);
        
        result = oidcSecurityService.extractValueFromPath(testClaims, null, Object.class);
        assertNull(result);
    }

    @Test
    void testExtractValueFromPath_TypeMismatch() {
        // Test type mismatch
        String result = oidcSecurityService.extractValueFromPath(testClaims, "permissions", String.class);
        assertNull(result); // Should return null for type mismatch
    }

    @Test
    void testExtractValueFromPath_ComplexNestedStructure() {
        // Test with more complex nested structure
        Map<String, Object> complexNested = new HashMap<>();
        Map<String, Object> level1 = new HashMap<>();
        Map<String, Object> level2 = new HashMap<>();
        level2.put("value", "deep_value");
        level1.put("level2", level2);
        complexNested.put("level1", level1);
        
        when(testClaims.get("complex")).thenReturn(complexNested);
        
        String deepValue = oidcSecurityService.extractValueFromPath(testClaims, "complex.level1.level2.value", String.class);
        assertEquals("deep_value", deepValue);
    }
}
