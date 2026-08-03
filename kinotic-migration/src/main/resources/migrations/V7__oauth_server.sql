-- OAuth 2.1 authorization server (PKCE authorization-code grant) that MCP hosts drive to reach
-- POST /mcp. Clients are not stored: a client_id is a Client ID Metadata Document URL the
-- authorization server fetches and validates per request
-- (draft-ietf-oauth-client-id-metadata-document).

-- Authorization-code flows in progress: created by the authorize endpoint, bound to a user when
-- the consent page approves, and deleted when the code is exchanged. codeHash is the SHA-256 of
-- the authorization code — the plaintext is never stored.
CREATE TABLE IF NOT EXISTS kinotic_oauth_authorization_grant (
    id KEYWORD,
    clientId KEYWORD,
    clientName KEYWORD NOT INDEXED,
    redirectUri KEYWORD NOT INDEXED,
    codeChallenge KEYWORD NOT INDEXED,
    scope KEYWORD NOT INDEXED,
    resource KEYWORD NOT INDEXED,
    state KEYWORD NOT INDEXED,
    identityId KEYWORD,
    codeHash KEYWORD,
    created DATE,
    expiresAt DATE
);
