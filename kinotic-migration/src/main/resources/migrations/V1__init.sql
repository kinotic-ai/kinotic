-- Create the application table if it does not exist
CREATE TABLE IF NOT EXISTS kinotic_application (
    id KEYWORD,
    description TEXT,
    oidcConfigurationIds JSON,
    updated DATE
);

-- Create the named_query_service_definition table if it does not exist
CREATE TABLE IF NOT EXISTS kinotic_named_query_service_definition (
    id KEYWORD,
    applicationId KEYWORD,
    projectId KEYWORD,
    entityDefinitionName KEYWORD,
    namedQueries JSON NOT INDEXED
);

-- Create the project table if it does not exist
CREATE TABLE IF NOT EXISTS kinotic_project (
    id KEYWORD,
    applicationId KEYWORD,
    name KEYWORD,
    description TEXT,
    sourceOfTruth KEYWORD,
    updated DATE
);

-- Create the EntityDefinition table if it does not exist
CREATE TABLE IF NOT EXISTS kinotic_entity_definition (
    id KEYWORD,
    name KEYWORD,
    applicationId KEYWORD,
    projectId KEYWORD,
    description TEXT,
    multiTenancyType KEYWORD,
    entityType KEYWORD,
    schema JSON NOT INDEXED,
    created DATE,
    updated DATE,
    published BOOLEAN,
    publishedTimestamp DATE,
    itemIndex KEYWORD,
    decoratedProperties JSON NOT INDEXED,
    versionFieldName KEYWORD NOT INDEXED,
    tenantIdFieldName KEYWORD NOT INDEXED,
    timeReferenceFieldName KEYWORD NOT INDEXED
);

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
    backChannelAuthority KEYWORD NOT INDEXED,
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

-- Pending sign-ups awaiting email verification
CREATE TABLE IF NOT EXISTS kinotic_pending_signup (
    id KEYWORD,
    orgName KEYWORD,
    orgDescription TEXT,
    email KEYWORD,
    displayName KEYWORD,
    passwordHash KEYWORD NOT INDEXED,
    verificationToken KEYWORD,
    expiresAt DATE,
    created DATE
);

-- Seed the KinoticSystem singleton
INSERT INTO kinotic_system (id) VALUES ('kinotic-system') WITH REFRESH;

-- Seed the default system administrator (password: kinotic)
INSERT INTO kinotic_iam_user (id, email, displayName, authType, authScopeType, authScopeId, enabled) VALUES ('00000000-0000-0000-0000-000000000001', 'admin@kinotic.local', 'System Admin', 'LOCAL', 'SYSTEM', 'kinotic', true) WITH REFRESH;
INSERT INTO kinotic_iam_credential (id, passwordHash) VALUES ('00000000-0000-0000-0000-000000000001', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
