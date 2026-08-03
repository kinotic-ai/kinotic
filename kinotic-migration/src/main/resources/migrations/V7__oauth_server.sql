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

-- The surface access tokens minted from a refresh-token lineage are valid for. Rotation preserves
-- it, so an MCP host's lineage can never mint a published-services token or the reverse. Lineages
-- predating this column are all CLI device-grant tokens and are read as PUBLISHED_SERVICES.
ALTER TABLE kinotic_refresh_token ADD COLUMN audience KEYWORD;
