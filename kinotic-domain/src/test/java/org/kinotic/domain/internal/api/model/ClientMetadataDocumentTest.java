package org.kinotic.domain.internal.api.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers the wire mapping of a Client ID Metadata Document: the snake_case property names a real
 * document uses, and the properties this server does not map, which are ignored.
 */
class ClientMetadataDocumentTest {

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

        ClientMetadataDocument document = jsonMapper.readValue(json, ClientMetadataDocument.class);

        assertEquals("https://claude.ai/oauth/claude-code-client-metadata", document.getClientId());
        assertEquals("Claude Code", document.getClientName());
        // portless by design: the callback binds an ephemeral port, which the authorization server
        // ignores when matching a loopback redirect URI
        assertEquals(List.of("http://localhost/callback", "http://127.0.0.1/callback"),
                     document.getRedirectUris());
        assertEquals("none", document.getTokenEndpointAuthMethod());
        assertNull(document.getClientSecret());
        assertNull(document.getClientSecretExpiresAt());
    }

    @Test
    void mapsEveryTokenEndpointAuthMethodTheClientSupports() {
        // served verbatim at https://chatgpt.com/oauth/client.json
        String json = """
                {
                  "client_id": "https://chatgpt.com/oauth/client.json",
                  "client_uri": "https://chatgpt.com/",
                  "redirect_uris": ["https://chatgpt.com/connector_platform_oauth_redirect"],
                  "token_endpoint_auth_method": "private_key_jwt",
                  "token_endpoint_auth_methods_supported": ["none", "private_key_jwt"],
                  "grant_types": ["authorization_code", "refresh_token"],
                  "response_types": ["code"],
                  "client_name": "ChatGPT",
                  "logo_uri": "https://persistent.oaistatic.com/sonic/misc/openai-logo.png",
                  "token_endpoint_auth_signing_alg": "RS256",
                  "jwks_uri": "https://chatgpt.com/oauth/jwks.json"
                }
                """;

        ClientMetadataDocument document = jsonMapper.readValue(json, ClientMetadataDocument.class);

        assertEquals("private_key_jwt", document.getTokenEndpointAuthMethod());
        assertEquals(List.of("none", "private_key_jwt"), document.getTokenEndpointAuthMethodsSupported());
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

        ClientMetadataDocument document = jsonMapper.readValue(json, ClientMetadataDocument.class);

        assertEquals("client_secret_basic", document.getTokenEndpointAuthMethod());
        assertEquals("s3cret", document.getClientSecret());
        assertEquals(0L, document.getClientSecretExpiresAt());
    }
}
