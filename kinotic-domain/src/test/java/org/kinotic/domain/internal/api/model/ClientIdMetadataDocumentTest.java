package org.kinotic.domain.internal.api.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers the wire mapping of a Client ID Metadata Document: the snake_case property names a real
 * document uses, and the additional properties Section 4.1 permits it to carry.
 */
class ClientIdMetadataDocumentTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void mapsSnakeCasePropertiesAndIgnoresTheRest() {
        // the shape an MCP host publishes, including the properties this server does not read
        String json = """
                {
                  "client_id": "https://claude.ai/oauth/client-metadata.json",
                  "client_name": "Claude",
                  "client_uri": "https://claude.ai",
                  "logo_uri": "https://claude.ai/logo.png",
                  "redirect_uris": ["https://claude.ai/api/mcp/auth_callback",
                                    "http://localhost:33418/callback"],
                  "token_endpoint_auth_method": "none",
                  "grant_types": ["authorization_code", "refresh_token"],
                  "response_types": ["code"],
                  "scope": "offline_access"
                }
                """;

        ClientIdMetadataDocument document = jsonMapper.readValue(json, ClientIdMetadataDocument.class);

        assertEquals("https://claude.ai/oauth/client-metadata.json", document.getClientId());
        assertEquals("Claude", document.getClientName());
        assertEquals(List.of("https://claude.ai/api/mcp/auth_callback", "http://localhost:33418/callback"),
                     document.getRedirectUris());
        assertEquals("none", document.getTokenEndpointAuthMethod());
        assertNull(document.getClientSecret());
        assertNull(document.getClientSecretExpiresAt());
    }

    @Test
    void readsTheSecretPropertiesADocumentMustNotCarry() {
        String json = """
                {
                  "client_id": "https://evil.example/client.json",
                  "redirect_uris": ["https://evil.example/cb"],
                  "token_endpoint_auth_method": "client_secret_basic",
                  "client_secret": "s3cret",
                  "client_secret_expires_at": 0
                }
                """;

        ClientIdMetadataDocument document = jsonMapper.readValue(json, ClientIdMetadataDocument.class);

        assertEquals("client_secret_basic", document.getTokenEndpointAuthMethod());
        assertEquals("s3cret", document.getClientSecret());
        assertEquals(0L, document.getClientSecretExpiresAt());
    }
}
