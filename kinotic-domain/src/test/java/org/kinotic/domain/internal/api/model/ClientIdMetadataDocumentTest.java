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
        // served verbatim at https://claude.ai/oauth/claude-code-client-metadata, including the
        // properties this server does not read
        String json = """
                {
                  "client_id": "https://claude.ai/oauth/claude-code-client-metadata",
                  "client_name": "Claude Code",
                  "client_uri": "https://claude.ai",
                  "redirect_uris": ["http://localhost/callback", "http://127.0.0.1/callback"],
                  "grant_types": ["authorization_code", "refresh_token"],
                  "response_types": ["code"],
                  "token_endpoint_auth_method": "none"
                }
                """;

        ClientIdMetadataDocument document = jsonMapper.readValue(json, ClientIdMetadataDocument.class);

        assertEquals("https://claude.ai/oauth/claude-code-client-metadata", document.getClientId());
        assertEquals("Claude Code", document.getClientName());
        // portless by design: the callback binds an ephemeral port, which RFC 8252 Section 7.3
        // requires the authorization server to ignore when matching
        assertEquals(List.of("http://localhost/callback", "http://127.0.0.1/callback"),
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
