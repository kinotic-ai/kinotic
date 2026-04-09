-- IAM User: authenticated identities at each scope layer
CREATE TABLE IF NOT EXISTS kinotic_iam_user (
    id KEYWORD,
    email KEYWORD,
    displayName KEYWORD,
    authType KEYWORD,
    oidcSubject KEYWORD,
    oidcConfigId KEYWORD,
    authScopeType KEYWORD,
    authScopeId KEYWORD,
    enabled BOOLEAN,
    created DATE,
    updated DATE
);

-- IAM Credential: password hashes stored separately from user entities
CREATE TABLE IF NOT EXISTS kinotic_iam_credential (
    id KEYWORD,
    passwordHash KEYWORD NOT INDEXED
);

-- OIDC Configuration: standalone named OIDC provider configs (no scope/ownership)
CREATE TABLE IF NOT EXISTS kinotic_oidc_configuration (
    id KEYWORD,
    name KEYWORD,
    provider KEYWORD,
    builtIn BOOLEAN,
    clientId KEYWORD NOT INDEXED,
    authority KEYWORD,
    backchannelAuthority KEYWORD NOT INDEXED,
    redirectUri KEYWORD NOT INDEXED,
    postLogoutRedirectUri KEYWORD NOT INDEXED,
    silentRedirectUri KEYWORD NOT INDEXED,
    domains JSON,
    audience KEYWORD NOT INDEXED,
    rolesClaimPath KEYWORD NOT INDEXED,
    additionalScopes KEYWORD NOT INDEXED,
    enabled BOOLEAN,
    created DATE,
    updated DATE
);

-- System: singleton representing the Kinotic OS deployment
CREATE TABLE IF NOT EXISTS kinotic_system (
    id KEYWORD,
    oidcConfigurationIds JSON,
    updated DATE
);

-- Organization: orgs developing applications on the platform
CREATE TABLE IF NOT EXISTS kinotic_organization (
    id KEYWORD,
    name KEYWORD,
    slug KEYWORD,
    description TEXT,
    oidcConfigurationIds JSON,
    createdBy KEYWORD,
    created DATE,
    updated DATE
);

-- Add oidcConfigurationIds to existing application table
ALTER TABLE kinotic_application ADD oidcConfigurationIds JSON;
