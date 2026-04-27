-- Pending OIDC registrations awaiting completion form submission
CREATE TABLE IF NOT EXISTS kinotic_pending_registration (
    id KEYWORD,
    verificationToken KEYWORD,
    expiresAt DATE,
    created DATE,
    oidcSubject KEYWORD,
    oidcConfigId KEYWORD,
    email KEYWORD,
    displayName KEYWORD,
    authScopeType KEYWORD,
    authScopeId KEYWORD,
    additionalClaims JSON NOT INDEXED
);
