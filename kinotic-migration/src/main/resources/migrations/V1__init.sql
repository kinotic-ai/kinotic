-- Create the application table if it does not exist
CREATE TABLE IF NOT EXISTS kinotic_application (
    id KEYWORD,
    organizationId KEYWORD,
    description TEXT,
    oidcConfigurationIds KEYWORD,
    updated DATE
);

-- Create the named_query_service_definition table if it does not exist
CREATE TABLE IF NOT EXISTS kinotic_named_query_service_definition (
    id KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    projectId KEYWORD,
    entityDefinitionName KEYWORD,
    namedQueries JSON NOT INDEXED
);

-- Create the project table if it does not exist.
-- repoFullName / repoId / defaultBranch are stamped by the GitHub repo provisioner
-- when the project is created; every project is backed by a GitHub repo.
CREATE TABLE IF NOT EXISTS kinotic_project (
    id KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    name KEYWORD,
    description TEXT,
    sourceOfTruth KEYWORD,
    repoFullName KEYWORD,
    repoId LONG,
    defaultBranch KEYWORD,
    repoPrivate BOOLEAN,
    updated DATE
);

-- GitHub App installations: one row per Kinotic Org that has linked GitHub.
CREATE TABLE IF NOT EXISTS kinotic_github_app_installation (
    id KEYWORD,
    organizationId KEYWORD,
    githubInstallationId LONG,
    accountLogin KEYWORD,
    accountType KEYWORD,
    suspendedAt DATE,
    created DATE,
    updated DATE
);

-- Create the EntityDefinition table if it does not exist
CREATE TABLE IF NOT EXISTS kinotic_entity_definition (
    id KEYWORD,
    name KEYWORD,
    organizationId KEYWORD,
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
    tenantId KEYWORD,
    enabled BOOLEAN,
    primary BOOLEAN,
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
    clientId KEYWORD NOT INDEXED,
    clientSecretRef KEYWORD NOT INDEXED,
    authority KEYWORD,
    backChannelAuthority KEYWORD NOT INDEXED,
    redirectUri KEYWORD NOT INDEXED,
    postLogoutRedirectUri KEYWORD NOT INDEXED,
    silentRedirectUri KEYWORD NOT INDEXED,
    domains KEYWORD,
    audience KEYWORD NOT INDEXED,
    rolesClaimPath KEYWORD NOT INDEXED,
    additionalScopes KEYWORD NOT INDEXED,
    provisioningMode KEYWORD,
    enabled BOOLEAN,
    created DATE,
    updated DATE
);

-- System: singleton representing the Kinotic OS deployment
CREATE TABLE IF NOT EXISTS kinotic_system (
    id KEYWORD,
    oidcConfigurationIds KEYWORD,
    updated DATE
);

-- Organization: orgs developing applications on the platform
CREATE TABLE IF NOT EXISTS kinotic_organization (
    id KEYWORD,
    name KEYWORD,
    description TEXT,
    oidcConfigurationIds KEYWORD,
    createdBy KEYWORD,
    created DATE,
    updated DATE
);

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

-- Sign-up requests awaiting email verification
CREATE TABLE IF NOT EXISTS kinotic_signup_request (
    id KEYWORD,
    orgName KEYWORD,
    orgDescription TEXT,
    email KEYWORD,
    displayName KEYWORD,
    verificationToken KEYWORD,
    expiresAt DATE,
    created DATE
);

-- Create the vm_node table for tracking VmManager nodes
CREATE TABLE IF NOT EXISTS kinotic_vm_node (
    id KEYWORD,
    name KEYWORD,
    hostname KEYWORD,
    status KEYWORD,
    totalCpus INTEGER,
    totalMemoryMb INTEGER,
    totalDiskMb INTEGER,
    allocatedCpus INTEGER,
    allocatedMemoryMb INTEGER,
    allocatedDiskMb INTEGER,
    lastSeen DATE
);

-- Create the workload table for tracking deployed workloads
CREATE TABLE IF NOT EXISTS kinotic_workload (
    id KEYWORD,
    name KEYWORD,
    description TEXT,
    nodeId KEYWORD,
    providerType KEYWORD,
    image KEYWORD,
    vcpus INTEGER,
    memoryMb INTEGER,
    diskSizeMb INTEGER,
    status KEYWORD,
    environment JSON NOT INDEXED,
    portMappings JSON NOT INDEXED,
    created DATE,
    updated DATE
);