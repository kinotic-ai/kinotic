-- Create the application table if it does not exist
CREATE TABLE IF NOT EXISTS kinotic_application (
    id KEYWORD,
    organizationId KEYWORD,
    name KEYWORD,
    description TEXT,
    oidcConfigurationIds KEYWORD,
    tenantPerUser BOOLEAN,
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
-- repoFullName / repoId / repoDefaultBranch are stamped by the GitHub repo provisioner
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
    repoDefaultBranch KEYWORD,
    repoPrivate BOOLEAN,
    repoConnectionStatus KEYWORD,
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

-- IAM User: authenticated identities at each scope layer. Scope is encoded structurally by
-- which of organizationId / applicationId is set: both null = SYSTEM, organizationId only =
-- ORGANIZATION, both set = APPLICATION.
-- Uniqueness rule (enforced in service layer): one row per (email, organizationId, applicationId).
CREATE TABLE IF NOT EXISTS kinotic_iam_user (
    id KEYWORD,
    email KEYWORD,
    displayName KEYWORD,
    authType KEYWORD,
    oidcSubject KEYWORD,
    oidcConfigId KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    tenantId KEYWORD,
    enabled BOOLEAN,
    created DATE,
    updated DATE
);

-- IAM Credential: password hashes stored separately from user entities
CREATE TABLE IF NOT EXISTS kinotic_iam_credential (
    id KEYWORD,
    passwordHash KEYWORD NOT INDEXED
);

-- OIDC Configuration: per-org OIDC provider configs. Each row is owned by an organization
-- (organizationId enforced by AbstractCrudService). "Where can this config be used" is
-- expressed by inbound references — kinotic_organization.ssoConfigId for the org's SSO,
-- kinotic_application.oidcConfigurationIds for application-level logins.
CREATE TABLE IF NOT EXISTS kinotic_oidc_configuration (
    id KEYWORD,
    organizationId KEYWORD,
    name KEYWORD,
    provider KEYWORD,
    clientId KEYWORD NOT INDEXED,
    secretNameRef KEYWORD NOT INDEXED,
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

-- Kinotic-curated social IdP configs (Google, Microsoft Live, etc.) that power the
-- "Continue with X" buttons on org signup and email-first org login. Seeded via SQL
-- migration; not editable through the org admin UI.
CREATE TABLE IF NOT EXISTS kinotic_org_signup_oidc_configuration (
    id KEYWORD,
    name KEYWORD,
    provider KEYWORD,
    clientId KEYWORD NOT INDEXED,
    secretNameRef KEYWORD NOT INDEXED,
    authority KEYWORD,
    audience KEYWORD NOT INDEXED,
    enabled BOOLEAN,
    created DATE,
    updated DATE
);

-- Organization: orgs developing applications on the platform.
-- ssoConfigId points at the org's single OidcConfiguration used as its SSO provider for
-- org-level Kinotic login (null when the org has no SSO). All other OidcConfigurations
-- the org owns are referenced from the org's apps via kinotic_application.oidcConfigurationIds.
CREATE TABLE IF NOT EXISTS kinotic_organization (
    id KEYWORD,
    name KEYWORD,
    description TEXT,
    ssoConfigId KEYWORD NOT INDEXED,
    createdBy KEYWORD,
    created DATE,
    updated DATE
);

-- Pending sign-ups (email-verification or OIDC) awaiting completion (PendingSignUp): the identity
-- to create plus authType. The organization name is collected at completion, not stored here.
CREATE TABLE IF NOT EXISTS kinotic_pending_signup (
    id KEYWORD,
    verificationToken KEYWORD,
    expiresAt DATE,
    created DATE,
    email KEYWORD,
    displayName KEYWORD,
    authType KEYWORD,
    oidcSubject KEYWORD,
    oidcConfigId KEYWORD
);

-- Pending member invitations (PendingInvite) awaiting acceptance: the invitee's identity, the
-- scope they join (organizationId always set; applicationId only for app-member invites), and
-- inviter attribution for the email/accept page. No authType — the invitee chooses password or
-- OIDC at accept. Single-use: consumed and deleted when accepted, cancelled, or found expired.
CREATE TABLE IF NOT EXISTS kinotic_pending_invite (
    id KEYWORD,
    verificationToken KEYWORD,
    expiresAt DATE,
    created DATE,
    email KEYWORD,
    displayName KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    invitedById KEYWORD,
    invitedByName KEYWORD
);

-- An application's customized invitation email (InviteEmailTemplate): Handlebars sources
-- replacing the built-in invitation template, at most one row per application.
CREATE TABLE IF NOT EXISTS kinotic_invite_email_template (
    id KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    subject TEXT,
    htmlBody TEXT,
    textBody TEXT,
    created DATE,
    updated DATE
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