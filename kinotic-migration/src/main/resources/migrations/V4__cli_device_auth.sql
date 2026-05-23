-- OAuth 2.0 Device Authorization Grant (RFC 8628): pending CLI device-code login flows.
-- Short-lived (minutes); deleted once the CLI collects its tokens. deviceCodeHash is the
-- SHA-256 of the high-entropy device_code the CLI polls with — the plaintext is never stored.
CREATE TABLE IF NOT EXISTS kinotic_device_code_grant (
    id KEYWORD,
    deviceCodeHash KEYWORD,
    userCode KEYWORD,
    userId KEYWORD,
    created DATE,
    expiresAt DATE,
    lastPolledAt DATE,
    intervalSeconds INTEGER
);

-- Rotating refresh tokens for CLI sessions. tokenHash is the SHA-256 of the refresh token —
-- the plaintext lives only on the client. familyId groups a rotation lineage so presenting
-- an already-rotated token (reuse) can revoke the whole family.
CREATE TABLE IF NOT EXISTS kinotic_refresh_token (
    id KEYWORD,
    tokenHash KEYWORD,
    userId KEYWORD,
    familyId KEYWORD,
    created DATE,
    expiresAt DATE,
    lastUsedAt DATE,
    revoked BOOLEAN,
    replacedById KEYWORD NOT INDEXED
);
